package com.fitplan.rag.knowledge;

import com.fitplan.rag.knowledge.IncrementalRagIndexer;
import com.fitplan.rag.knowledge.HybridFitnessKnowledgeRetriever;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("evaluation")
@EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
class HybridRagEvaluationIT {

    private final IncrementalRagIndexer indexer;
    private final HybridFitnessKnowledgeRetriever retriever;

    HybridRagEvaluationIT(
            IncrementalRagIndexer indexer,
            HybridFitnessKnowledgeRetriever retriever) {
        this.indexer = indexer;
        this.retriever = retriever;
    }

    @Test
    void evaluatesHybridRetrievalPipeline() throws IOException {
        indexer.indexNow();
        List<EvaluationCase> cases = loadCases();
        int hits = 0;
        double reciprocalRank = 0.0;
        double ndcg = 0.0;
        int contextChars = 0;
        Set<String> retrievalMethods = new HashSet<>();

        for (EvaluationCase evaluationCase : cases) {
            List<HybridFitnessKnowledgeRetriever.RetrievedKnowledge> results =
                    retriever.retrieve(evaluationCase.query());
            contextChars += results.stream().mapToInt(result -> result.text().length()).sum();
            results.forEach(result -> retrievalMethods.add(result.retrievalMethod()));
            for (int rank = 0; rank < results.size(); rank++) {
                if (evaluationCase.expectedFilename().equals(results.get(rank).filename())) {
                    hits++;
                    reciprocalRank += 1.0 / (rank + 1);
                    ndcg += 1.0 / (Math.log(rank + 2) / Math.log(2));
                    break;
                }
            }
        }

        double recall = hits / (double) cases.size();
        double mrr = reciprocalRank / cases.size();
        double averageNdcg = ndcg / cases.size();
        double averageContextChars = contextChars / (double) cases.size();
        System.out.printf(Locale.ROOT,
                "HYBRID recall@%d=%.3f MRR=%.3f nDCG=%.3f avgContextChars=%.1f methods=%s%n",
                2, recall, mrr, averageNdcg, averageContextChars, retrievalMethods);

        assertThat(cases).hasSize(15);
        assertThat(recall).isGreaterThanOrEqualTo(0.80);
    }

    private static List<EvaluationCase> loadCases() throws IOException {
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

    private record EvaluationCase(String query, String expectedFilename) {
    }
}
