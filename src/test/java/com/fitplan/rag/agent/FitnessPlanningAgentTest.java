package com.fitplan.rag.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FitnessPlanningAgentTest {

    @Test
    void exposesSevenToolCallingCapabilities() {
        assertThat(FitnessPlanningAgent.TOOL_NAMES)
                .containsExactly(
                        "searchFitnessKnowledge",
                        "getUserProfile",
                        "updateUserProfile",
                        "saveTrainingLog",
                        "getRecentTrainingLogs",
                        "savePlanSummary",
                        "getCurrentPlan");
    }

    @Test
    void exposesAgentMetadataAndSystemPrompt() {
        assertThat(FitnessPlanningAgent.ID).isEqualTo("fitness-planning");
        assertThat(FitnessPlanningAgent.NAME).isEqualTo("健身规划 Agent");
        assertThat(FitnessPlanningAgent.DESCRIPTION).contains("7 个工具");
        assertThat(FitnessPlanningAgent.SYSTEM_PROMPT)
                .contains("FitPlan Agent")
                .contains("searchFitnessKnowledge")
                .contains("不得编造");
    }
}
