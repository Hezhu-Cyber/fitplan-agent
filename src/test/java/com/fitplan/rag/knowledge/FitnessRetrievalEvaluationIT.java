package com.fitplan.rag.knowledge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("evaluation")
@EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
class FitnessRetrievalEvaluationIT {

    private static final int RETRIEVAL_CANDIDATES = 10;
    private static final int[] TOP_K_VALUES = {1, 2, 3, 4, 5, 6};
    private static final double[] THRESHOLDS = {0.0, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7};

    private final VectorStore vectorStore;

    FitnessRetrievalEvaluationIT(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Test
    void evaluatesRetrievalConfigurations() throws IOException {
        List<EvaluationCase> cases = loadCases();
        List<QueryResult> queryResults = new ArrayList<>();

        for (EvaluationCase evaluationCase : cases) {
            List<Document> documents = vectorStore.similaritySearch(SearchRequest.builder()
                    .query(evaluationCase.query())
                    .topK(RETRIEVAL_CANDIDATES)
                    .similarityThreshold(0.0)
                    .build());
            queryResults.add(new QueryResult(evaluationCase, documents));
        }

        System.out.println("\nFitPlan retrieval evaluation");
        System.out.println("topK\tthreshold\trecall\tMRR\tavgResults");
        List<Metrics> metrics = new ArrayList<>();
        for (int topK : TOP_K_VALUES) {
            for (double threshold : THRESHOLDS) {
                Metrics result = calculateMetrics(queryResults, topK, threshold);
                metrics.add(result);
                System.out.printf(Locale.ROOT, "%d\t%.2f\t\t%.3f\t%.3f\t%.2f%n",
                        topK, threshold, result.recall(), result.mrr(), result.averageResults());
            }
        }

        Metrics baseline = metrics.stream()
                .filter(metric -> metric.topK() == 4 && metric.threshold() == 0.0)
                .findFirst()
                .orElseThrow();
        Metrics recommended = metrics.stream()
                .filter(metric -> metric.recall() >= baseline.recall())
                .max(Comparator.comparingDouble(Metrics::mrr)
                        .thenComparingDouble(Metrics::threshold)
                        .thenComparingInt(metric -> -metric.topK()))
                .orElseThrow();

        System.out.printf(Locale.ROOT,
                "BASELINE topK=%d threshold=%.2f recall=%.3f MRR=%.3f%n",
                baseline.topK(), baseline.threshold(), baseline.recall(), baseline.mrr());
        System.out.printf(Locale.ROOT,
                "RECOMMENDED topK=%d threshold=%.2f recall=%.3f MRR=%.3f%n",
                recommended.topK(), recommended.threshold(), recommended.recall(), recommended.mrr());

        assertThat(cases).hasSize(15);
        assertThat(baseline.recall()).isGreaterThanOrEqualTo(0.80);
    }

    private List<EvaluationCase> loadCases() throws IOException {
        ClassPathResource resource = new ClassPathResource("fitness-retrieval-evaluation.tsv");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                resource.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines()
                    .filter(line -> !line.isBlank() && !line.startsWith("#"))
                    .map(line -> line.split("\\|", 2))
                    .map(parts -> new EvaluationCase(parts[0], parts[1]))
                    .toList();
        }
    }

    private Metrics calculateMetrics(
            List<QueryResult> queryResults,
            int topK,
            double threshold) {
        int hits = 0;
        double reciprocalRankSum = 0.0;
        int resultCount = 0;

        for (QueryResult queryResult : queryResults) {
            List<Document> filtered = queryResult.documents().stream()
                    .filter(document -> document.getScore() != null
                            && document.getScore() >= threshold)
                    .limit(topK)
                    .toList();
            resultCount += filtered.size();
            for (int index = 0; index < filtered.size(); index++) {
                Object filename = filtered.get(index).getMetadata().get("filename");
                if (queryResult.evaluationCase().expectedFilename().equals(filename)) {
                    hits++;
                    reciprocalRankSum += 1.0 / (index + 1);
                    break;
                }
            }
        }

        int total = queryResults.size();
        return new Metrics(
                topK,
                threshold,
                hits / (double) total,
                reciprocalRankSum / total,
                resultCount / (double) total);
    }

    private record EvaluationCase(String query, String expectedFilename) {
    }

    private record QueryResult(EvaluationCase evaluationCase, List<Document> documents) {
    }

    private record Metrics(
            int topK,
            double threshold,
            double recall,
            double mrr,
            double averageResults) {
    }
}
