package com.fitplan.rag.safety;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QwenRiskClassifierAgentTest {

    @Test
    void returnsStructuredRiskDecisionFromDedicatedModelCall() {
        ChatModel chatModel = mock(ChatModel.class);
        stubDefaultOptions(chatModel);
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
                .thenReturn(response("""
                        {
                          "level": "URGENT",
                          "reason": "运动时出现压迫性胸部不适",
                          "currentRisk": true,
                          "allowFitnessAdvice": false
                        }
                        """));
        QwenRiskClassifierAgent classifier = new QwenRiskClassifierAgent(
                chatModel, new SimpleMeterRegistry(), "qwen-plus", true);

        FitnessRiskClassifier.RiskDecision decision = classifier.classify("跑步时心口像压着石头");

        assertThat(decision.level()).isEqualTo(FitnessRiskClassifier.RiskLevel.URGENT);
        assertThat(decision.currentRisk()).isTrue();
        assertThat(decision.allowFitnessAdvice()).isFalse();
    }

    @Test
    void failsConservativelyWhenClassifierCallFails() {
        ChatModel chatModel = mock(ChatModel.class);
        stubDefaultOptions(chatModel);
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
                .thenThrow(new IllegalStateException("classifier unavailable"));
        QwenRiskClassifierAgent classifier = new QwenRiskClassifierAgent(
                chatModel, new SimpleMeterRegistry(), "qwen-plus", true);

        FitnessRiskClassifier.RiskDecision decision = classifier.classify("普通问题");

        assertThat(decision.level()).isEqualTo(FitnessRiskClassifier.RiskLevel.CLARIFY);
        assertThat(decision.allowFitnessAdvice()).isFalse();
    }

    @Test
    void canBeDisabledWhileKeepingDeterministicRulesAvailable() {
        ChatModel chatModel = mock(ChatModel.class);
        QwenRiskClassifierAgent classifier = new QwenRiskClassifierAgent(
                chatModel, new SimpleMeterRegistry(), "qwen-plus", false);

        FitnessRiskClassifier.RiskDecision decision = classifier.classify("RPE 8 是什么意思？");

        assertThat(decision).isEqualTo(FitnessRiskClassifier.RiskDecision.safe());
    }

    private static ChatResponse response(String text) {
        return ChatResponse.builder()
                .generations(List.of(new Generation(new AssistantMessage(text))))
                .build();
    }

    private static void stubDefaultOptions(ChatModel chatModel) {
        when(chatModel.getOptions()).thenReturn(OpenAiChatOptions.builder()
                .model("qwen-plus")
                .build());
    }
}
