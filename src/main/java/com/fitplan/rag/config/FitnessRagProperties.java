package com.fitplan.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "fitplan.rag")
public class FitnessRagProperties {

    private int topK = 2;
    private double similarityThreshold = 0.4;
    private int candidateK = 12;
    private int lexicalCandidateK = 12;
    private int maxContextChars = 6000;
    private int maxChunksPerSource = 2;
    private boolean indexOnStartup = false;
    private boolean queryRewriteEnabled = true;
    private boolean rerankEnabled;
    private String rerankModel = "qwen3-rerank";
    private String rerankBaseUrl = "";
    private String rerankApiKey = "";

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        if (topK < 1) {
            throw new IllegalArgumentException("fitplan.rag.top-k must be at least 1");
        }
        this.topK = topK;
    }

    public double getSimilarityThreshold() {
        return similarityThreshold;
    }

    public void setSimilarityThreshold(double similarityThreshold) {
        if (similarityThreshold < 0.0 || similarityThreshold > 1.0) {
            throw new IllegalArgumentException(
                    "fitplan.rag.similarity-threshold must be between 0.0 and 1.0");
        }
        this.similarityThreshold = similarityThreshold;
    }

    public int getCandidateK() {
        return candidateK;
    }

    public void setCandidateK(int candidateK) {
        this.candidateK = requirePositive(candidateK, "candidate-k");
    }

    public int getLexicalCandidateK() {
        return lexicalCandidateK;
    }

    public void setLexicalCandidateK(int lexicalCandidateK) {
        this.lexicalCandidateK = requirePositive(lexicalCandidateK, "lexical-candidate-k");
    }

    public int getMaxContextChars() {
        return maxContextChars;
    }

    public void setMaxContextChars(int maxContextChars) {
        this.maxContextChars = requirePositive(maxContextChars, "max-context-chars");
    }

    public int getMaxChunksPerSource() {
        return maxChunksPerSource;
    }

    public void setMaxChunksPerSource(int maxChunksPerSource) {
        this.maxChunksPerSource = requirePositive(maxChunksPerSource, "max-chunks-per-source");
    }

    public boolean isIndexOnStartup() {
        return indexOnStartup;
    }

    public void setIndexOnStartup(boolean indexOnStartup) {
        this.indexOnStartup = indexOnStartup;
    }

    public boolean isQueryRewriteEnabled() {
        return queryRewriteEnabled;
    }

    public void setQueryRewriteEnabled(boolean queryRewriteEnabled) {
        this.queryRewriteEnabled = queryRewriteEnabled;
    }

    public boolean isRerankEnabled() {
        return rerankEnabled;
    }

    public void setRerankEnabled(boolean rerankEnabled) {
        this.rerankEnabled = rerankEnabled;
    }

    public String getRerankModel() {
        return rerankModel;
    }

    public void setRerankModel(String rerankModel) {
        this.rerankModel = normalize(rerankModel);
    }

    public String getRerankBaseUrl() {
        return rerankBaseUrl;
    }

    public void setRerankBaseUrl(String rerankBaseUrl) {
        this.rerankBaseUrl = normalize(rerankBaseUrl);
    }

    public String getRerankApiKey() {
        return rerankApiKey;
    }

    public void setRerankApiKey(String rerankApiKey) {
        this.rerankApiKey = normalize(rerankApiKey);
    }

    private static int requirePositive(int value, String property) {
        if (value < 1) {
            throw new IllegalArgumentException("fitplan.rag." + property + " must be at least 1");
        }
        return value;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
