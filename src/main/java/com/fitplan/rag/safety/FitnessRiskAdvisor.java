package com.fitplan.rag.safety;

import com.fitplan.rag.agent.SafetyGuardAgent;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 安全审查 Agent 的 Spring AI 适配器：把 {@link SafetyGuardAgent} 织入健身规划 Agent 的
 * 调用链（Advisor）。在会话记忆之前执行，确保被拦截的输入既不会进入模型、也不会写入聊天记忆。
 */
@Component
public final class FitnessRiskAdvisor implements CallAdvisor, StreamAdvisor {

    private static final String BLOCKED_CONTEXT_KEY = "fitplan.risk.blocked";
    private static final String REASON_CONTEXT_KEY = "fitplan.risk.reason";
    private static final String LEVEL_CONTEXT_KEY = "fitplan.risk.level";

    /**
     * 在会话记忆之前执行，确保被拦截的输入既不会进入模型，也不会写入聊天记忆。
     */
    public static final int ORDER = Advisor.DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER - 100;

    private final SafetyGuardAgent safetyGuardAgent;

    public FitnessRiskAdvisor(SafetyGuardAgent safetyGuardAgent) {
        this.safetyGuardAgent = safetyGuardAgent;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        return assess(request)
                .map(decision -> blockedResponse(request, decision))
                .orElseGet(() -> chain.nextCall(request));
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        return assess(request)
                .<Flux<ChatClientResponse>>map(decision -> Flux.just(blockedResponse(request, decision)))
                .orElseGet(() -> chain.nextStream(request));
    }

    @Override
    public String getName() {
        return FitnessRiskAdvisor.class.getSimpleName();
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    public static boolean blocked(ChatClientResponse response) {
        return response != null
                && response.context() != null
                && Boolean.TRUE.equals(response.context().get(BLOCKED_CONTEXT_KEY));
    }

    public static boolean blocked(ChatResponse response) {
        return response != null
                && response.getMetadata() != null
                && Boolean.TRUE.equals(response.getMetadata().get(BLOCKED_CONTEXT_KEY));
    }

    private Optional<SafetyGuardAgent.SafetyDecision> assess(ChatClientRequest request) {
        String latestUserMessage = request.prompt().getUserMessage().getText();
        SafetyGuardAgent.SafetyDecision decision = safetyGuardAgent.assess(latestUserMessage);
        return decision.blocked() ? Optional.of(decision) : Optional.empty();
    }

    private static ChatClientResponse blockedResponse(
            ChatClientRequest request,
            SafetyGuardAgent.SafetyDecision decision) {
        Map<String, Object> responseContext = new HashMap<>(request.context());
        responseContext.put(BLOCKED_CONTEXT_KEY, true);
        responseContext.put(REASON_CONTEXT_KEY, decision.reason());
        responseContext.put(LEVEL_CONTEXT_KEY, decision.level().name());

        ChatResponse chatResponse = ChatResponse.builder()
                .generations(List.of(new Generation(new AssistantMessage(decision.userMessage()))))
                .build();
        return ChatClientResponse.builder()
                .chatResponse(chatResponse)
                .context(responseContext)
                .build();
    }
}
