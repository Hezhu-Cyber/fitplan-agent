package com.fitplan.rag.safety;

import com.fitplan.rag.agent.SafetyGuardAgent;
import com.fitplan.rag.safety.FitnessRiskAssessmentService;
import com.fitplan.rag.safety.FitnessRiskClassifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FitnessRiskAdvisorTest {

    private FitnessRiskClassifier riskClassifier;
    private FitnessRiskAdvisor advisor;

    @BeforeEach
    void setUp() {
        riskClassifier = mock(FitnessRiskClassifier.class);
        when(riskClassifier.classify(any())).thenReturn(FitnessRiskClassifier.RiskDecision.safe());
        advisor = new FitnessRiskAdvisor(new SafetyGuardAgent(new FitnessRiskAssessmentService(), riskClassifier));
    }

    @Test
    void blocksStreamingRequestWithoutCallingDownstreamChain() {
        StreamAdvisorChain chain = mock(StreamAdvisorChain.class);
        ChatClientRequest request = request("我跑步时胸口很痛，还想继续做间歇训练");

        ChatClientResponse response = advisor.adviseStream(request, chain).single().block();

        assertThat(response).isNotNull();
        assertThat(FitnessRiskAdvisor.blocked(response)).isTrue();
        assertThat(text(response)).contains("停止训练", "医生", "04-recovery-safety.md");
        verify(chain, never()).nextStream(any());
        verify(riskClassifier, never()).classify(any());
    }

    @Test
    void blocksSynchronousRequestWithoutCallingDownstreamChain() {
        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        ChatClientRequest request = request("请诊断我的膝盖疼痛是什么病");

        ChatClientResponse response = advisor.adviseCall(request, chain);

        assertThat(FitnessRiskAdvisor.blocked(response)).isTrue();
        assertThat(text(response)).contains("不能进行疾病诊断");
        verify(chain, never()).nextCall(any());
    }

    @Test
    void forwardsOrdinaryRequestToDownstreamChain() {
        StreamAdvisorChain chain = mock(StreamAdvisorChain.class);
        ChatClientRequest request = request("RPE 8 表示什么意思？");
        ChatClientResponse downstream = response("普通回答");
        when(chain.nextStream(request)).thenReturn(Flux.just(downstream));

        ChatClientResponse actual = advisor.adviseStream(request, chain).single().block();

        assertThat(actual).isSameAs(downstream);
        verify(chain).nextStream(request);
    }

    @Test
    void blocksSemanticRiskReportedByClassifier() {
        StreamAdvisorChain chain = mock(StreamAdvisorChain.class);
        ChatClientRequest request = request("跑步时心口像压着一块石头，还冒冷汗");
        when(riskClassifier.classify(any())).thenReturn(new FitnessRiskClassifier.RiskDecision(
                FitnessRiskClassifier.RiskLevel.URGENT,
                "运动中出现疑似健康红旗",
                true,
                false));

        ChatClientResponse response = advisor.adviseStream(request, chain).single().block();

        assertThat(response).isNotNull();
        assertThat(FitnessRiskAdvisor.blocked(response)).isTrue();
        assertThat(text(response)).contains("停止训练", "医疗评估");
        verify(chain, never()).nextStream(any());
    }

    @Test
    void asksForSafetyDetailsWhenClassifierIsUncertain() {
        StreamAdvisorChain chain = mock(StreamAdvisorChain.class);
        ChatClientRequest request = request("练完以后感觉很不舒服");
        when(riskClassifier.classify(any())).thenReturn(new FitnessRiskClassifier.RiskDecision(
                FitnessRiskClassifier.RiskLevel.CLARIFY,
                "症状描述不明确",
                true,
                false));

        ChatClientResponse response = advisor.adviseStream(request, chain).single().block();

        assertThat(response).isNotNull();
        assertThat(text(response)).contains("信息还不够明确", "暂停增加训练强度");
        verify(chain, never()).nextStream(any());
    }

    @Test
    void assessesOnlyLatestUserMessageInsteadOfSystemPromptOrHistory() {
        StreamAdvisorChain chain = mock(StreamAdvisorChain.class);
        ChatClientRequest request = new ChatClientRequest(
                new Prompt(List.of(
                        new SystemMessage("遇到胸痛时必须停止训练。"),
                        new UserMessage("以前问过什么是疾病诊断"),
                        new AssistantMessage("历史回答"),
                        new UserMessage("RPE 8 表示什么意思？"))),
                Map.of());
        ChatClientResponse downstream = response("普通回答");
        when(chain.nextStream(request)).thenReturn(Flux.just(downstream));

        ChatClientResponse actual = advisor.adviseStream(request, chain).single().block();

        assertThat(actual).isSameAs(downstream);
        verify(chain).nextStream(request);
        verify(riskClassifier).classify("RPE 8 表示什么意思？");
    }

    @Test
    void runsBeforeChatMemoryAdvisor() {
        assertThat(advisor.getOrder()).isLessThan(Advisor.DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER);
    }

    private static ChatClientRequest request(String userMessage) {
        return new ChatClientRequest(
                new Prompt(List.of(
                        new SystemMessage("你是健身助手。"),
                        new UserMessage(userMessage))),
                Map.of("request-id", "test-request"));
    }

    private static ChatClientResponse response(String text) {
        return ChatClientResponse.builder()
                .chatResponse(ChatResponse.builder()
                        .generations(List.of(new Generation(new AssistantMessage(text))))
                        .build())
                .context(Map.of())
                .build();
    }

    private static String text(ChatClientResponse response) {
        return response.chatResponse().getResult().getOutput().getText();
    }
}
