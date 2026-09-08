package com.fitplan.rag.knowledge;

import com.fitplan.rag.config.FitnessRagProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Incremental RAG indexer.
 *
 * <p>On startup (or on demand) it scans the Markdown corpus and re-embeds only
 * the sources that changed: content hash, embedding model, vector dimensions or
 * chunk-schema version. Removed sources are dropped from the vector store. A
 * database-backed lease lock prevents concurrent instances from indexing at the
 * same time, and every run is recorded as an index job for observability.
 */
@Service
public class IncrementalRagIndexer {

    private static final Logger log = LoggerFactory.getLogger(IncrementalRagIndexer.class);
    private static final String LOCK_NAME = "fitplan-rag-index";
    private static final int LOCK_LEASE_SECONDS = 900;
    private static final int EMBEDDING_BATCH_SIZE = 10;
    static final String CHUNK_SCHEMA_VERSION = "zh-markdown-token-v3";
    private static final TokenTextSplitter TOKEN_SPLITTER = TokenTextSplitter.builder()
            .withChunkSize(800)
            .withMinChunkSizeChars(350)
            .withMinChunkLengthToEmbed(20)
            .withMaxNumChunks(5000)
            .withKeepSeparator(true)
            .withPunctuationMarks(List.of(
                    '。', '！', '？', '；', '，', '、',
                    '.', '!', '?', ';', ',', '\n'))
            .build();

    private final VectorStore vectorStore;
    private final FitnessDocumentLoader documentLoader;
    private final RagIndexRepository repository;
    private final FitnessRagProperties properties;
    private final MeterRegistry meterRegistry;
    private final String embeddingModel;
    private final int embeddingDimensions;
    private final ApplicationEventPublisher eventPublisher;
    private final String ownerId = UUID.randomUUID().toString();

    public IncrementalRagIndexer(
            VectorStore vectorStore,
            FitnessDocumentLoader documentLoader,
            RagIndexRepository repository,
            FitnessRagProperties properties,
            MeterRegistry meterRegistry,
            ApplicationEventPublisher eventPublisher,
            @Value("${spring.ai.openai.embedding.model}") String embeddingModel,
            @Value("${spring.ai.openai.embedding.dimensions}") int embeddingDimensions) {
        this.vectorStore = vectorStore;
        this.documentLoader = documentLoader;
        this.repository = repository;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        this.embeddingModel = embeddingModel;
        this.embeddingDimensions = embeddingDimensions;
        this.eventPublisher = eventPublisher;
    }

    @Async("ragIndexExecutor")
    @EventListener(ApplicationReadyEvent.class)
    public void indexOnStartup() {
        if (properties.isIndexOnStartup()) {
            indexNow();
        }
    }

    public IndexOutcome indexNow() {
        if (!repository.tryAcquireLock(LOCK_NAME, ownerId, LOCK_LEASE_SECONDS)) {
            meterRegistry.counter("fitplan.rag.index.lock_skipped").increment();
            log.info("Skipping RAG index because another instance owns the index lock");
            return IndexOutcome.locked();
        }
        UUID jobId = repository.createJob(ownerId);
        Timer.Sample timer = Timer.start(meterRegistry);
        try {
            IndexOutcome outcome = doIndex();
            repository.completeJob(jobId, outcome.stats());
            publishIndexCompletedIfChanged(outcome);
            meterRegistry.counter("fitplan.rag.index.jobs", "status", "success").increment();
            meterRegistry.counter("fitplan.rag.index.chunks").increment(outcome.stats().indexedChunks());
            log.info("RAG incremental index completed: jobId={}, stats={}", jobId, outcome.stats());
            return outcome;
        } catch (RuntimeException exception) {
            repository.failJob(jobId, exception.getMessage());
            meterRegistry.counter("fitplan.rag.index.jobs", "status", "failed").increment();
            log.error("RAG incremental index failed: jobId={}", jobId, exception);
            throw exception;
        } finally {
            timer.stop(meterRegistry.timer("fitplan.rag.index.duration"));
            repository.releaseLock(LOCK_NAME, ownerId);
        }
    }

    private void publishIndexCompletedIfChanged(IndexOutcome outcome) {
        RagIndexRepository.IndexStats stats = outcome.stats();
        if (stats.indexedChunks() > 0 || stats.deletedSources() > 0) {
            eventPublisher.publishEvent(new RagIndexCompletedEvent(outcome));
        }
    }

    private IndexOutcome doIndex() {
        List<FitnessDocumentLoader.KnowledgeSource> sources = documentLoader.loadSources();
        Map<String, RagIndexRepository.SourceManifest> manifests = repository.findManifests();
        Set<String> currentSourceIds = new HashSet<>();
        int changed = 0;
        int indexedChunks = 0;
        int skipped = 0;

        for (FitnessDocumentLoader.KnowledgeSource source : sources) {
            currentSourceIds.add(source.sourceId());
            RagIndexRepository.SourceManifest previous = manifests.get(source.sourceId());
            if (isUnchanged(source, previous)) {
                skipped++;
                continue;
            }
            List<Document> chunks = createChunks(source);
            List<String> oldIds = repository.findVectorIds(source.sourceId());
            if (!oldIds.isEmpty()) {
                vectorStore.delete(oldIds);
            }
            for (int start = 0; start < chunks.size(); start += EMBEDDING_BATCH_SIZE) {
                int end = Math.min(start + EMBEDDING_BATCH_SIZE, chunks.size());
                vectorStore.add(chunks.subList(start, end));
            }
            log.info("Indexed RAG source: sourceId={}, filename={}, oldChunks={}, newChunks={}, schema={}",
                    source.sourceId(), source.filename(), oldIds.size(), chunks.size(), CHUNK_SCHEMA_VERSION);
            repository.saveManifest(new RagIndexRepository.SourceManifest(
                    source.sourceId(), source.filename(), source.contentHash(), chunks.size(),
                    embeddingModel, embeddingDimensions, CHUNK_SCHEMA_VERSION));
            changed++;
            indexedChunks += chunks.size();
        }

        int deleted = 0;
        for (String existingSourceId : manifests.keySet()) {
            if (!currentSourceIds.contains(existingSourceId)) {
                List<String> vectorIds = repository.findVectorIds(existingSourceId);
                if (!vectorIds.isEmpty()) {
                    vectorStore.delete(vectorIds);
                }
                repository.deleteManifest(existingSourceId);
                deleted++;
            }
        }
        return new IndexOutcome(false, new RagIndexRepository.IndexStats(
                sources.size(), changed, indexedChunks, skipped, deleted));
    }

    private boolean isUnchanged(
            FitnessDocumentLoader.KnowledgeSource source,
            RagIndexRepository.SourceManifest manifest) {
        return manifest != null
                && source.contentHash().equals(manifest.contentHash())
                && embeddingModel.equals(manifest.embeddingModel())
                && embeddingDimensions == manifest.embeddingDimensions()
                && CHUNK_SCHEMA_VERSION.equals(manifest.chunkSchemaVersion());
    }

    List<Document> createChunks(FitnessDocumentLoader.KnowledgeSource source) {
        return createKnowledgeChunks(source);
    }

    /**
     * Each input document represents one Markdown section. Long sections are
     * split again by token count without ever crossing a section boundary.
     */
    private static List<Document> createKnowledgeChunks(FitnessDocumentLoader.KnowledgeSource source) {
        List<Document> chunks = new ArrayList<>();
        int chunkIndex = 0;
        for (int sectionIndex = 0; sectionIndex < source.documents().size(); sectionIndex++) {
            Document section = source.documents().get(sectionIndex);
            List<Document> sectionChunks = TOKEN_SPLITTER.split(section);
            for (int sectionChunkIndex = 0; sectionChunkIndex < sectionChunks.size(); sectionChunkIndex++) {
                Document split = sectionChunks.get(sectionChunkIndex);
                Map<String, Object> metadata = new HashMap<>(section.getMetadata());
                metadata.putAll(split.getMetadata());
                String documentTitle = metadataValue(metadata, "sourceTitle", source.filename());
                String sectionTitle = metadataValue(metadata, "sectionTitle", documentTitle);

                metadata.put("source_id", source.sourceId());
                metadata.put("filename", source.filename());
                metadata.put("document_title", documentTitle);
                metadata.put("section_title", sectionTitle);
                metadata.put("section_index", sectionIndex);
                metadata.put("chunk_index", chunkIndex);
                metadata.put("language", "zh-CN");
                metadata.put("chunk_schema_version", CHUNK_SCHEMA_VERSION);

                metadata.put("domain", "fitness");
                metadata.put("sourceId", source.sourceId());
                metadata.put("contentHash", source.contentHash());
                metadata.put("documentTitle", documentTitle);
                metadata.put("sectionTitle", sectionTitle);
                metadata.put("sectionIndex", sectionIndex);
                metadata.put("chunkIndex", chunkIndex);
                metadata.put("sectionChunkIndex", sectionChunkIndex);
                metadata.put("chunkSchemaVersion", CHUNK_SCHEMA_VERSION);
                metadata.put("sectionPath", documentTitle + " > " + sectionTitle);

                String idSeed = source.sourceId() + ':' + source.contentHash() + ':'
                        + CHUNK_SCHEMA_VERSION + ':' + sectionIndex + ':' + sectionChunkIndex;
                String chunkId = UUID.nameUUIDFromBytes(idSeed.getBytes(StandardCharsets.UTF_8)).toString();
                chunks.add(Document.builder()
                        .id(chunkId)
                        .text(split.getText())
                        .metadata(metadata)
                        .build());
                chunkIndex++;
            }
        }
        return List.copyOf(chunks);
    }

    private static String metadataValue(Map<String, Object> metadata, String key, String fallback) {
        Object value = metadata.get(key);
        return value == null || value.toString().isBlank() ? fallback : value.toString();
    }

    public record IndexOutcome(boolean lockSkipped, RagIndexRepository.IndexStats stats) {

        static IndexOutcome locked() {
            return new IndexOutcome(true, new RagIndexRepository.IndexStats(0, 0, 0, 0, 0));
        }
    }
}