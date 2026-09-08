package com.fitplan.rag.knowledge;

import com.fitplan.rag.config.FitnessRagProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hybrid retrieval pipeline:
 * <ol>
 *   <li>dense semantic search over the pgvector index,</li>
 *   <li>lexical search with an in-process Lucene BM25 index (Chinese-aware),</li>
 *   <li>reciprocal rank fusion of both channels,</li>
 *   <li>optional Qwen reranking,</li>
 *   <li>context selection under per-source and total token budgets.</li>
 * </ol>
 * Dense retrieval failures degrade gracefully to lexical-only results.
 */
@Service
public class HybridFitnessKnowledgeRetriever {

    private static final int RRF_K = 60;
    private static final Logger log = LoggerFactory.getLogger(HybridFitnessKnowledgeRetriever.class);

    private final VectorStore vectorStore;
    private final Bm25LexicalIndexer bm25LexicalIndexer;
    private final FitnessRagProperties properties;
    private final DashScopeReranker reranker;
    private final MeterRegistry meterRegistry;

    public HybridFitnessKnowledgeRetriever(
            VectorStore vectorStore,
            Bm25LexicalIndexer bm25LexicalIndexer,
            FitnessRagProperties properties,
            DashScopeReranker reranker,
            MeterRegistry meterRegistry) {
        this.vectorStore = vectorStore;
        this.bm25LexicalIndexer = bm25LexicalIndexer;
        this.properties = properties;
        this.reranker = reranker;
        this.meterRegistry = meterRegistry;
    }

    public List<RetrievedKnowledge> retrieve(String question) {
        return retrieve(question, question, RetrievalMode.HYBRID);
    }

    /**
     * Hybrid retrieval with a rewritten semantic query. The original question is
     * retained for exact lexical matching and final reranking.
     */
    public List<RetrievedKnowledge> retrieve(String originalQuestion, String semanticQuery) {
        return retrieve(originalQuestion, semanticQuery, RetrievalMode.HYBRID);
    }

    /**
     * Retrieval with an explicit channel configuration for ablation baselines:
     * {@link RetrievalMode#DENSE} uses vector similarity only, {@link RetrievalMode#LEXICAL}
     * uses BM25 only, and {@link RetrievalMode#HYBRID} runs the full fusion pipeline.
     */
    public List<RetrievedKnowledge> retrieve(String question, RetrievalMode mode) {
        return retrieve(question, question, mode);
    }

    private List<RetrievedKnowledge> retrieve(
            String originalQuestion,
            String semanticQuery,
            RetrievalMode mode) {
        Timer.Sample timer = Timer.start(meterRegistry);
        try {
            List<RagCandidate> dense = mode == RetrievalMode.LEXICAL ? List.of() : denseCandidates(semanticQuery);
            List<RagCandidate> lexical = mode == RetrievalMode.DENSE ? List.of() : lexicalCandidates(originalQuestion);
            List<RagCandidate> fused = switch (mode) {
                case DENSE -> dense;
                case LEXICAL -> lexical;
                case HYBRID -> reciprocalRankFusion(dense, lexical);
            };
            List<RagCandidate> ranked = mode == RetrievalMode.HYBRID
                    ? reranker.rerank(originalQuestion, fused, properties.getCandidateK())
                    : fused;
            List<RetrievedKnowledge> selected = selectContext(ranked);

            meterRegistry.summary("fitplan.rag.retrieval.candidates", "stage", "dense")
                    .record(dense.size());
            meterRegistry.summary("fitplan.rag.retrieval.candidates", "stage", "lexical")
                    .record(lexical.size());
            meterRegistry.summary("fitplan.rag.retrieval.results").record(selected.size());
            return selected;
        } finally {
            timer.stop(meterRegistry.timer("fitplan.rag.retrieval.duration"));
        }
    }

    /** Retrieval channel configuration used for evaluation baselines. */
    public enum RetrievalMode {
        DENSE,
        LEXICAL,
        HYBRID
    }

    private List<RagCandidate> denseCandidates(String question) {
        List<Document> documents;
        try {
            documents = vectorStore.similaritySearch(SearchRequest.builder()
                    .query(question)
                    .topK(properties.getCandidateK())
                    .similarityThreshold(properties.getSimilarityThreshold())
                    .build());
        } catch (RuntimeException exception) {
            meterRegistry.counter("fitplan.rag.retrieval.dense", "status", "fallback").increment();
            log.warn("Dense retrieval unavailable; continuing with PostgreSQL lexical retrieval: {}",
                    exception.getMessage());
            return List.of();
        }
        return documents.stream()
                .map(document -> new RagCandidate(
                        document.getId(),
                        document.getText(),
                        metadata(document, "filename", "unknown"),
                        integerMetadata(document, "chunkIndex"),
                        document.getScore() == null ? 0.0 : document.getScore(),
                        "dense"))
                .toList();
    }

    private List<RagCandidate> lexicalCandidates(String question) {
        return bm25LexicalIndexer.search(question, properties.getLexicalCandidateK());
    }

    List<RagCandidate> reciprocalRankFusion(
            List<RagCandidate> dense,
            List<RagCandidate> lexical) {
        Map<String, MutableFusionCandidate> combined = new LinkedHashMap<>();
        addRanked(combined, dense, "dense");
        addRanked(combined, lexical, "lexical");
        return combined.values().stream()
                .map(MutableFusionCandidate::toCandidate)
                .sorted(Comparator.comparingDouble(RagCandidate::relevanceScore).reversed())
                .limit(properties.getCandidateK())
                .toList();
    }

    private static void addRanked(
            Map<String, MutableFusionCandidate> combined,
            List<RagCandidate> candidates,
            String channel) {
        for (int index = 0; index < candidates.size(); index++) {
            RagCandidate candidate = candidates.get(index);
            MutableFusionCandidate fusion = combined.computeIfAbsent(
                    candidate.chunkId(),
                    ignored -> new MutableFusionCandidate(candidate));
            fusion.score += 1.0 / (RRF_K + index + 1);
            fusion.channels.put(channel, true);
        }
    }

    private List<RetrievedKnowledge> selectContext(List<RagCandidate> ranked) {
        List<RetrievedKnowledge> selected = new ArrayList<>();
        Map<String, Integer> perSource = new HashMap<>();
        int usedChars = 0;
        for (RagCandidate candidate : ranked) {
            if (selected.size() >= properties.getTopK()) {
                break;
            }
            int sourceCount = perSource.getOrDefault(candidate.filename(), 0);
            if (sourceCount >= properties.getMaxChunksPerSource()) {
                continue;
            }
            int candidateLength = candidate.text().length();
            if (!selected.isEmpty() && usedChars + candidateLength > properties.getMaxContextChars()) {
                continue;
            }
            selected.add(new RetrievedKnowledge(
                    candidate.text(), candidate.filename(), candidate.chunkId(), candidate.chunkIndex(),
                    candidate.relevanceScore(), candidate.retrievalMethod()));
            perSource.put(candidate.filename(), sourceCount + 1);
            usedChars += candidateLength;
        }
        return List.copyOf(selected);
    }

    private static String metadata(Document document, String key, String fallback) {
        Object value = document.getMetadata().get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private static int integerMetadata(Document document, String key) {
        Object value = document.getMetadata().get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? 0 : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    public record RetrievedKnowledge(
            String text,
            String filename,
            String chunkId,
            int chunkIndex,
            double relevanceScore,
            String retrievalMethod) {
    }

    private static final class MutableFusionCandidate {

        private final RagCandidate candidate;
        private final Map<String, Boolean> channels = new LinkedHashMap<>();
        private double score;

        private MutableFusionCandidate(RagCandidate candidate) {
            this.candidate = candidate;
        }

        private RagCandidate toCandidate() {
            String method = channels.size() == 2 ? "dense+lexical+rrf" : channels.keySet().iterator().next() + "+rrf";
            return candidate.withScore(score, method);
        }
    }
}
