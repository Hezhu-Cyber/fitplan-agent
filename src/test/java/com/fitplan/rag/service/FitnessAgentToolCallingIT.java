package com.fitplan.rag.service;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("evaluation")
@EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "FITPLAN_RUN_AGENT_EVAL", matches = "(?i)true")
class FitnessAgentToolCallingIT {

    @Autowired
    private FitnessPlanningService fitnessPlanningService;

    @Test
    void modelCallsKnowledgeToolAndReturnsActualSource() {
        String answer = fitnessPlanningService.streamPlan(
                        "力量训练中的 RPE 8 表示什么？请依据知识库回答。",
                        "agent-tool-smoke-test")
                .collectList()
                .map(parts -> String.join("", parts))
                .block(Duration.ofMinutes(2));

        assertThat(answer)
                .contains("Agent 工具：", "searchFitnessKnowledge", "知识来源：")
                .contains("02-strength-training.md")
                .doesNotContain("未调用知识检索工具");
    }
}
