package com.fitplan.rag.knowledge;

/**
 * Published after the dense RAG index finishes a rebuild so dependent caches
 * (currently the BM25 lexical index) can refresh from the new chunk set.
 */
public record RagIndexCompletedEvent(IncrementalRagIndexer.IndexOutcome outcome) {
}
