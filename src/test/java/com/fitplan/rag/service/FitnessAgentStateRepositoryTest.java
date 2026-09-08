package com.fitplan.rag.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FitnessAgentStateRepositoryTest {

    private final FitnessAgentStateRepository repository = new FitnessAgentStateRepository();

    @Test
    void mergesOnlyNewProfileFields() {
        repository.updateProfile("chat-1", new FitnessAgentStateRepository.ProfileUpdate(
                "30", "增肌", "新手", "3", "60", "健身房", "无伤病"));

        FitnessAgentStateRepository.UserProfile updated = repository.updateProfile(
                "chat-1",
                new FitnessAgentStateRepository.ProfileUpdate("", "提高体能", "", "", "", "", ""));

        assertThat(updated.age()).isEqualTo("30");
        assertThat(updated.goal()).isEqualTo("提高体能");
        assertThat(updated.equipment()).isEqualTo("健身房");
    }

    @Test
    void storesTrainingLogsAndPlanByChatId() {
        repository.addTrainingLog("chat-1", new FitnessAgentStateRepository.TrainingLogInput(
                "2026-08-02", "卧推", "3x10", "40kg", "7", "完成顺利"));
        repository.savePlan("chat-1", new FitnessAgentStateRepository.PlanSummaryInput(
                "增肌", "每周一三五全身训练", "达到次数上限后加重"));

        assertThat(repository.recentTrainingLogs("chat-1"))
                .singleElement()
                .extracting(FitnessAgentStateRepository.TrainingLog::exercise)
                .isEqualTo("卧推");
        assertThat(repository.getPlan("chat-1").goal()).isEqualTo("增肌");
        assertThat(repository.recentTrainingLogs("another-chat")).isEmpty();
    }
}
