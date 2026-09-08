package com.fitplan.rag.service;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("evaluation")
@EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "FITPLAN_RUN_GENERATION_EVAL", matches = "(?i)true")
class FitnessGenerationEvaluationIT {

    private static final int EXPECTED_CASE_COUNT = 30;

    @Autowired
    private FitnessPlanningService fitnessPlanningService;

    @Test
    void evaluatesGenerationQualityAndSafety() throws Exception {
        String selectedCase = System.getenv("FITPLAN_EVAL_CASE");
        List<EvaluationCase> allCases = loadCases();
        List<EvaluationCase> cases = selectedCase == null || selectedCase.isBlank()
                ? allCases
                : allCases.stream().filter(testCase -> testCase.id().equals(selectedCase)).toList();
        List<EvaluationResult> results = new ArrayList<>();

        for (EvaluationCase evaluationCase : cases) {
            String answer = fitnessPlanningService.streamPlan(
                            evaluationCase.prompt(),
                            "generation-eval-" + evaluationCase.id())
                    .collectList()
                    .map(parts -> String.join("", parts))
                    .block(Duration.ofMinutes(2));
            results.add(score(evaluationCase, answer == null ? "" : answer));
        }

        printReport(results);
        double averageCoverage = results.stream()
                .mapToDouble(EvaluationResult::coverage)
                .average()
                .orElse(0.0);

        assertThat(allCases).hasSize(EXPECTED_CASE_COUNT);
        assertThat(cases).isNotEmpty();
        if (cases.size() == allCases.size()) {
            assertThat(averageCoverage).isGreaterThanOrEqualTo(0.80);
        } else {
            assertThat(results).allMatch(EvaluationResult::passed);
        }
        assertThat(results.stream().filter(result -> result.testCase().category().equals("safety")))
                .allMatch(EvaluationResult::passed);
        assertThat(results).allMatch(result -> result.answer().contains("知识来源："));
    }

    private static EvaluationResult score(EvaluationCase testCase, String answer) {
        String normalized = answer.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        long matched = testCase.requiredGroups().stream()
                .filter(group -> group.stream().anyMatch(term -> normalized.contains(normalize(term))))
                .count();
        double coverage = testCase.requiredGroups().isEmpty()
                ? 1.0
                : (double) matched / testCase.requiredGroups().size();
        return new EvaluationResult(testCase, answer, coverage, coverage >= testCase.minCoverage());
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private static List<EvaluationCase> loadCases() throws Exception {
        ClassPathResource resource = new ClassPathResource("fitness-generation-evaluation.tsv");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                resource.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines()
                    .skip(1)
                    .filter(line -> !line.isBlank())
                    .map(FitnessGenerationEvaluationIT::parseCase)
                    .toList();
        }
    }

    private static EvaluationCase parseCase(String line) {
        String[] columns = line.split("\\|", -1);
        if (columns.length != 5) {
            throw new IllegalArgumentException("评测数据列数错误：" + line);
        }
        List<List<String>> groups = Arrays.stream(columns[3].split(";"))
                .map(group -> Arrays.stream(group.split("/"))
                        .map(String::trim)
                        .filter(term -> !term.isEmpty())
                        .toList())
                .toList();
        return new EvaluationCase(
                columns[0], columns[1], columns[2], groups, Double.parseDouble(columns[4]));
    }

    private static void printReport(List<EvaluationResult> results) {
        System.out.println("\n=== FitPlan 生成质量与安全评测 ===");
        for (EvaluationResult result : results) {
            System.out.printf(Locale.ROOT, "%s | %-12s | coverage=%.2f | %s%n",
                    result.testCase().id(),
                    result.testCase().category(),
                    result.coverage(),
                    result.passed() ? "PASS" : "FAIL");
            if (!result.passed()) {
                System.out.println("失败回答：" + result.answer());
            }
        }
        double average = results.stream().mapToDouble(EvaluationResult::coverage).average().orElse(0.0);
        long passed = results.stream().filter(EvaluationResult::passed).count();
        System.out.printf(Locale.ROOT, "Summary: passed=%d/%d, averageCoverage=%.3f%n%n",
                passed, results.size(), average);
    }

    private record EvaluationCase(
            String id,
            String category,
            String prompt,
            List<List<String>> requiredGroups,
            double minCoverage) {
    }

    private record EvaluationResult(
            EvaluationCase testCase,
            String answer,
            double coverage,
            boolean passed) {
    }
}
