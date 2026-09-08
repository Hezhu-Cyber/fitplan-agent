package com.fitplan.rag.knowledge;

record RagCandidate(
        String chunkId,
        String text,
        String filename,
        int chunkIndex,
        double relevanceScore,
        String retrievalMethod) {

    RagCandidate withScore(double score, String method) {
        return new RagCandidate(chunkId, text, filename, chunkIndex, score, method);
    }
}
