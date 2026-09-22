package com.fitplan.rag.service;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FitnessAgentStateRepositoryTest {

    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_OWNER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final FitnessAgentStateRepository repository = new InMemoryFitnessAgentStateRepository();

    @Test
    void mergesOnlyNewProfileFields() {
        repository.updateProfile(OWNER, "chat-1", new FitnessAgentStateRepository.ProfileUpdate(
                "30", "增肌", "新手", "3", "60", "健身房", "无伤病"));

        FitnessAgentStateRepository.UserProfile updated = repository.updateProfile(
                OWNER,
                "chat-1",
                new FitnessAgentStateRepository.ProfileUpdate("", "提高体能", "", "", "", "", ""));

        assertThat(updated.age()).isEqualTo("30");
        assertThat(updated.goal()).isEqualTo("提高体能");
        assertThat(updated.equipment()).isEqualTo("健身房");
    }

    @Test
    void storesTrainingLogsAndPlanByOwnerAndChatId() {
        repository.addTrainingLog(OWNER, "chat-1", new FitnessAgentStateRepository.TrainingLogInput(
                "2026-08-02", "卧推", "3x10", "40kg", "7", "完成顺利"), "log-1");
        repository.savePlan(OWNER, "chat-1", new FitnessAgentStateRepository.PlanSummaryInput(
                "增肌", "每周一三五全身训练", "达到次数上限后加重"));

        assertThat(repository.recentTrainingLogs(OWNER, "chat-1"))
                .singleElement()
                .extracting(FitnessAgentStateRepository.TrainingLog::exercise)
                .isEqualTo("卧推");
        assertThat(repository.getPlan(OWNER, "chat-1").goal()).isEqualTo("增肌");
        assertThat(repository.recentTrainingLogs(OWNER, "another-chat")).isEmpty();
    }

    @Test
    void isolatesIdenticalChatIdsAcrossOwners() {
        repository.updateProfile(OWNER, "shared-chat", new FitnessAgentStateRepository.ProfileUpdate(
                "30", "增肌", "新手", "3", "60", "健身房", "无伤病"));

        assertThat(repository.getProfile(OTHER_OWNER, "shared-chat")).isEqualTo(
                FitnessAgentStateRepository.UserProfile.empty());
    }

    @Test
    void rejectsMissingOrOversizedChatIds() {
        assertThatThrownBy(() -> repository.getProfile(OWNER, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chatId");
        assertThatThrownBy(() -> repository.getProfile(OWNER, "x".repeat(129)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("128");
    }
}
