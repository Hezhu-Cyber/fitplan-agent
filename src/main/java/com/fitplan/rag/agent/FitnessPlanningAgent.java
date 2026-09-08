package com.fitplan.rag.agent;

import com.fitplan.rag.knowledge.RetrievedSourceFormatter;
import com.fitplan.rag.safety.FitnessRiskAdvisor;
import com.fitplan.rag.service.AgentExecutionContext;
import com.fitplan.rag.service.FitnessAgentTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Agent 1：健身规划 Agent。
 *
 * <p>基于 Function Calling 的多步工具调用循环：模型自主决定何时检索知识、读写用户画像、
 * 记录训练日志、保存/读取计划摘要，最终生成个性化训练计划并以 SSE 流式输出。
 * 安全审查 Agent 以 {@link FitnessRiskAdvisor} 的形式织入本 Agent 的调用链，
 * 先于会话记忆执行（fail-closed：被拦截的输入不进模型、不入记忆）。
 */
@Component
public class FitnessPlanningAgent implements Agent {

    public static final String ID = "fitness-planning";
    public static final String NAME = "健身规划 Agent";
    public static final String DESCRIPTION =
            "面向普通健康成年人的循证健身规划智能体：自主调用知识检索、用户画像、训练日志、"
                    + "计划摘要等 7 个工具完成多轮个性化训练计划，并以 SSE 流式输出。";

    /** 与 {@link FitnessAgentTools} 中 @Tool 名称保持一致，用于注册表展示与审计。 */
    public static final List<String> TOOL_NAMES = List.of(
            "searchFitnessKnowledge",
            "getUserProfile",
            "updateUserProfile",
            "saveTrainingLog",
            "getRecentTrainingLogs",
            "savePlanSummary",
            "getCurrentPlan");

    private static final Logger log = LoggerFactory.getLogger(FitnessPlanningAgent.class);

    private static final String BLOCKED_CONTEXT_KEY = "fitplan.risk.blocked";

    public static final String SYSTEM_PROMPT = """
            你是 FitPlan Agent，一名面向普通健康成年人的循证健身计划智能体。

            你可以自主选择并调用以下能力：检索健身知识、读取或更新用户画像、保存或读取训练记录、保存或读取训练计划摘要。
            必须遵守以下执行规则：
            1. 回答训练、减脂、恢复、安全或计划问题前，调用 searchFitnessKnowledge 查询本地知识库。
            2. 制定个性化计划前，先调用 getUserProfile；把用户本轮明确提供的新信息通过 updateUserProfile 保存。
            3. 画像缺少年龄、目标、训练经验、训练天数、单次时长、器械或健康限制时，先简短追问，不得猜测。
            4. 用户明确报告已经完成的训练时，调用 saveTrainingLog；调整负荷前调用 getRecentTrainingLogs。
            5. 修改已有计划前调用 getCurrentPlan；生成完整计划后调用 savePlanSummary 保存目标、周安排和递进规则。
            6. 不得声称调用了实际没有调用的工具，不得编造工具结果、用户资料、训练记录或知识来源。
            7. 完整计划包含周安排、动作、组次或时长、RPE/RIR、休息、热身、递进方法、恢复和复盘指标。
            8. RPE 6 约保留 4 次，RPE 7 约保留 3 次，RPE 8 约保留 2 次；新手不安排做到力竭。
            9. 只提供一般健身教育，不做疾病诊断、伤病康复处方或极端饮食设计。
            10. 使用清晰中文，输出简洁、可执行。知识文件名和工具执行轨迹由系统自动追加，不要自行编造。
            """;

    private final ChatClient chatClient;

    public FitnessPlanningAgent(
            ChatClient.Builder chatClientBuilder,
            FitnessAgentTools agentTools,
            FitnessRiskAdvisor riskAdvisor) {
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(20)
                .build();
        this.chatClient = chatClientBuilder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        riskAdvisor,
                        MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultTools(agentTools)
                .build();
    }

    public Flux<String> streamPlan(String message, String chatId) {
        String safeMessage = message == null ? "" : message;
        AgentExecutionContext execution = new AgentExecutionContext(chatId);
        Map<String, Object> toolContext = Map.of(AgentExecutionContext.TOOL_CONTEXT_KEY, execution);

        try {
            // Non-streaming call: avoids the MessageAggregator NoSuchElementException bug.
            String response = chatClient.prompt()
                    .user(safeMessage)
                    .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                    .toolContext(toolContext)
                    .call()
                    .chatResponse()
                    .getResult()
                    .getOutput()
                    .getText();

            if (response != null && !response.isBlank()) {
                return Flux.just(response);
            }
        } catch (Exception e) {
            log.warn("Non-streaming call failed, falling back to streaming: {}", e.getMessage());
        }

        // Streaming fallback: guards the known MessageAggregator bug.
        return streamAgent(safeMessage, chatId)
                .onErrorResume(NoSuchElementException.class, ex -> {
                    log.warn("Streaming call failed: {}", ex.getMessage());
                    return Flux.empty();
                });
    }

    private Flux<String> streamAgent(String message, String chatId) {
        AgentExecutionContext execution = new AgentExecutionContext(chatId);
        Map<String, Object> toolContext = Map.of(AgentExecutionContext.TOOL_CONTEXT_KEY, execution);
        AtomicBoolean riskBlocked = new AtomicBoolean();

        return chatClient.prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .toolContext(toolContext)
                .stream()
                .chatResponse()
                .doOnError(NoSuchElementException.class, ex ->
                        log.warn("NoSuchElementException caught while aggregating stream, resuming: {}", ex.getMessage()))
                .onErrorResume(NoSuchElementException.class, ex -> Flux.empty())
                .filter(chatResponse -> {
                    if (chatResponse == null || chatResponse.getMetadata() == null) {
                        return false;
                    }
                    Object blocked = chatResponse.getMetadata().get(BLOCKED_CONTEXT_KEY);
                    if (Boolean.TRUE.equals(blocked)) {
                        riskBlocked.set(true);
                    }
                    return true;
                })
                .map(FitnessPlanningAgent::extractText)
                .filter(text -> !text.isEmpty())
                .concatWith(Flux.defer(() -> riskBlocked.get()
                        ? Flux.empty()
                        : Flux.just(formatExecution(execution))));
    }

    private static String formatExecution(AgentExecutionContext execution) {
        String tools = execution.toolCalls().isEmpty()
                ? "未调用工具"
                : String.join(" → ", execution.toolCalls());
        String sources = RetrievedSourceFormatter.format(execution.sourceFiles());
        return "\n\nAgent 工具：" + tools + sources;
    }

    private static String extractText(ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getResult() == null) {
            return "";
        }
        var output = chatResponse.getResult().getOutput();
        if (output == null) {
            return "";
        }
        return output.getText();
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return DESCRIPTION;
    }

    @Override
    public String systemPrompt() {
        return SYSTEM_PROMPT;
    }

    @Override
    public List<String> toolNames() {
        return TOOL_NAMES;
    }
}
