package com.fitplan.rag.knowledge;

import com.fitplan.rag.config.FitnessRagProperties;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Optional cross-encoder reranking via the Qwen rerank API. When reranking is
 * not configured or the request fails, the original RRF order is returned.
 */
@Component
class DashScopeReranker {

    private static final Logger log = LoggerFactory.getLogger(DashScopeReranker.class);
    private static final String INSTRUCTION =
            "Given a fitness question, retrieve evidence passages that directly and safely answer the question.";

    private final FitnessRagProperties properties;
    private final MeterRegistry meterRegistry;
    private final RestClient restClient;

    DashScopeReranker(FitnessRagProperties properties, MeterRegistry meterRegistry) {
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        this.restClient = RestClient.builder().build();
    }

    List<RagCandidate> rerank(String query, List<RagCandidate> candidates, int topK) {
        if (!isConfigured() || candidates.isEmpty()) {
            return candidates;
        }
        try {
            QwenRerankResponse response = restClient.post()
                    .uri(properties.getRerankBaseUrl())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getRerankApiKey())
                    .body(new QwenRerankRequest(
                            properties.getRerankModel(),
                            candidates.stream().map(RagCandidate::text).toList(),
                            query,
                            Math.min(topK, candidates.size()),
                            INSTRUCTION))
                    .retrieve()
                    .body(QwenRerankResponse.class);
            if (response == null || response.results() == null) {
                throw new IllegalStateException("Rerank API returned no results");
            }

            List<RagCandidate> ranked = new ArrayList<>();
            for (RerankResult result : response.results()) {
                if (result.index() >= 0 && result.index() < candidates.size()) {
                    ranked.add(candidates.get(result.index())
                            .withScore(result.relevanceScore(), "hybrid+qwen3-rerank"));
                }
            }
            meterRegistry.counter("fitplan.rag.rerank.requests", "status", "success").increment();
            return ranked.isEmpty() ? candidates : List.copyOf(ranked);
        } catch (RuntimeException exception) {
            meterRegistry.counter("fitplan.rag.rerank.requests", "status", "fallback").increment();
            log.warn("Rerank request failed; using RRF order: {}", exception.getMessage());
            return candidates;
        }
    }

    private boolean isConfigured() {
        return properties.isRerankEnabled()
                && !properties.getRerankBaseUrl().isBlank()
                && !properties.getRerankApiKey().isBlank();
    }

    private record QwenRerankRequest(
            String model,
            List<String> documents,
            String query,
            int top_n,
            String instruct) {
    }

    private record QwenRerankResponse(List<RerankResult> results) {
    }

    private record RerankResult(int index, double relevance_score) {

        double relevanceScore() {
            return relevance_score;
        }
    }
}