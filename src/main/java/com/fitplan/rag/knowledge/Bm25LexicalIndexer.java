package com.fitplan.rag.knowledge;

import io.micrometer.core.instrument.MeterRegistry;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.cn.smart.SmartChineseAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * In-process BM25 lexical index over the vector-store chunks.
 *
 * <p>PostgreSQL {@code pg_trgm} word similarity is ineffective for Chinese, so the
 * lexical channel is backed by Apache Lucene with a Chinese-aware analyzer
 * (SmartChineseAnalyzer) and BM25 ranking. The index is rebuilt from
 * {@code vector_store} when the application becomes ready and again after every
 * successful dense RAG re-index ({@link RagIndexCompletedEvent}).
 */
@Component
public class Bm25LexicalIndexer {

    private static final Logger log = LoggerFactory.getLogger(Bm25LexicalIndexer.class);

    private final JdbcTemplate jdbcTemplate;
    private final MeterRegistry meterRegistry;

    private volatile IndexSearcher searcher;
    private volatile Analyzer analyzer;

    public Bm25LexicalIndexer(JdbcTemplate jdbcTemplate, MeterRegistry meterRegistry) {
        this.jdbcTemplate = jdbcTemplate;
        this.meterRegistry = meterRegistry;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void rebuildOnStartup() {
        try {
            rebuild();
        } catch (RuntimeException exception) {
            log.warn("BM25 lexical index unavailable at startup; lexical retrieval disabled: {}",
                    exception.getMessage());
        }
    }

    @EventListener
    public void rebuildAfterRagIndex(RagIndexCompletedEvent event) {
        try {
            rebuild();
        } catch (RuntimeException exception) {
            log.warn("BM25 lexical index refresh failed after RAG re-index: {}", exception.getMessage());
        }
    }

    /**
     * Rebuilds the in-memory Lucene index from the current vector-store chunks.
     * Safe to call concurrently; the previous index stays searchable until the
     * new one is fully built and swapped in.
     */
    public synchronized void rebuild() {
        rebuildFrom(loadChunks());
    }

    /** Testable seam: builds the index from a supplied chunk list. */
    synchronized void rebuildFrom(List<IndexedChunk> chunks) {
        try {
            Analyzer newAnalyzer = new SmartChineseAnalyzer();
            Directory directory = new ByteBuffersDirectory();
            IndexWriterConfig config = new IndexWriterConfig(newAnalyzer);
            config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
            config.setSimilarity(new BM25Similarity());
            try (IndexWriter writer = new IndexWriter(directory, config)) {
                for (IndexedChunk chunk : chunks) {
                    Document document = new Document();
                    document.add(new StringField("id", chunk.id(), Field.Store.YES));
                    document.add(new TextField("content", chunk.content(), Field.Store.YES));
                    document.add(new StringField("filename", chunk.filename(), Field.Store.YES));
                    document.add(new StoredField("chunkIndex", chunk.chunkIndex()));
                    writer.addDocument(document);
                }
            }
            DirectoryReader reader = DirectoryReader.open(directory);
            IndexSearcher newSearcher = new IndexSearcher(reader);
            newSearcher.setSimilarity(new BM25Similarity());

            IndexSearcher oldSearcher = this.searcher;
            Analyzer oldAnalyzer = this.analyzer;
            this.searcher = newSearcher;
            this.analyzer = newAnalyzer;
            closeQuietly(oldSearcher);
            if (oldAnalyzer != null) {
                oldAnalyzer.close();
            }
            meterRegistry.counter("fitplan.rag.lexical.index", "status", "success").increment();
            log.info("BM25 lexical index rebuilt with {} chunks", chunks.size());
        } catch (IOException exception) {
            meterRegistry.counter("fitplan.rag.lexical.index", "status", "failed").increment();
            throw new IllegalStateException("Failed to build BM25 lexical index", exception);
        }
    }

    /** BM25 top-{@code limit} results for a free-text Chinese query. */
    public List<RagCandidate> search(String question, int limit) {
        IndexSearcher current = this.searcher;
        Analyzer currentAnalyzer = this.analyzer;
        if (current == null || currentAnalyzer == null || question == null || question.isBlank()) {
            return List.of();
        }
        try {
            QueryParser parser = new QueryParser("content", currentAnalyzer);
            parser.setDefaultOperator(QueryParser.Operator.OR);
            Query query = parser.parse(question);
            TopDocs topDocs = current.search(query, limit);
            List<RagCandidate> candidates = new ArrayList<>(topDocs.scoreDocs.length);
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                Document document = current.storedFields().document(scoreDoc.doc);
                candidates.add(new RagCandidate(
                        document.get("id"),
                        document.get("content"),
                        document.get("filename"),
                        document.getField("chunkIndex").numericValue().intValue(),
                        scoreDoc.score,
                        "lexical"));
            }
            meterRegistry.counter("fitplan.rag.retrieval.lexical.bm25").increment();
            return candidates;
        } catch (ParseException exception) {
            log.warn("BM25 query parse failed: {}", exception.getMessage());
            return List.of();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to search BM25 lexical index", exception);
        }
    }

    public boolean isReady() {
        return searcher != null;
    }

    private List<IndexedChunk> loadChunks() {
        return jdbcTemplate.query(
                """
                SELECT id::text, content,
                       COALESCE(metadata->>'filename', 'unknown') AS filename,
                       COALESCE((metadata->>'chunkIndex')::integer, 0) AS chunk_index
                FROM vector_store
                WHERE metadata->>'domain' = 'fitness'
                """,
                (rs, rowNum) -> new IndexedChunk(
                        rs.getString("id"),
                        rs.getString("content"),
                        rs.getString("filename"),
                        rs.getInt("chunk_index")));
    }

    private static void closeQuietly(IndexSearcher searcher) {
        if (searcher == null) {
            return;
        }
        try {
            searcher.getIndexReader().close();
        } catch (IOException ignored) {
            // best effort on rotation
        }
    }

    record IndexedChunk(String id, String content, String filename, int chunkIndex) {
    }
}
