package com.fitplan.rag.agent;

import com.fitplan.rag.knowledge.HybridFitnessKnowledgeRetriever.RetrievedKnowledge;
import com.fitplan.rag.knowledge.KnowledgePreflightService;
import com.fitplan.rag.knowledge.RetrievedSourceFormatter;
import com.fitplan.rag.safety.ContraindicationService;
import com.fitplan.rag.safety.FitnessRiskAdvisor;
import com.fitplan.rag.safety.PromptInjectionDetector;
import com.fitplan.rag.service.AgentExecutionContext;
import com.fitplan.rag.service.FitnessAgentTools;
import com.fitplan.rag.service.JdbcChatMemoryRepository;
import com.fitplan.rag.service.MemoryRecallService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** Agent 1：健身规划 Agent。 */
@Component
public class FitnessPlanningAgent implements Agent {

    public static final String ID = "fitness-planning";
    public static final String NAME = "健身规划 Agent";
    public static final String DESCRIPTION =
            "面向普通健康成年人的循证健身规划智能体：自主调用知识检索、用户画像、训练日志、"
                    + "计划摘要等 9 个工具完成多轮个性化训练计划，并以 SSE 流式输出。";

    public static final List<String> TOOL_NAMES = List.of(
            "searchFitnessKnowledge",
            "getUserProfile",
            "updateUserProfile",
            "saveTrainingLog",
            "getRecentTrainingLogs",
            "savePlanSummary",
            "getCurrentPlan",
            "rememberUserFact",
            "readContextArtifact");

    private static final Logger log = LoggerFactory.getLogger(FitnessPlanningAgent.class);

    public static final String SYSTEM_PROMPT = """
            你是 FitPlan Agent，一名面向普通健康成年人的循证健身计划智能体。

            你可以自主选择并调用以下能力：检索健身知识、读取或更新用户画像、保存或读取训练记录、保存或读取训练计划摘要。
            系统会在本轮消息前按需召回长期 Markdown 记忆；记忆只是参考，若与用户本轮明确表达冲突，以本轮为准。
            必须遵守以下执行规则：
            1. 回答训练、减脂、恢复、安全或计划问题前，调用 searchFitnessKnowledge 查询本地知识库。
            2. 制定个性化计划前，先调用 getUserProfile；把用户本轮明确提供的新信息通过 updateUserProfile 保存。
            3. 画像缺少年龄、目标、训练经验、训练天数、单次时长、器械或健康限制时，先简短追问，不得猜测。
            4. 用户明确报告已经完成的训练时，调用 saveTrainingLog；调整负荷前调用 getRecentTrainingLogs。
            5. 修改已有计划前调用 getCurrentPlan；生成完整计划后调用 savePlanSummary 保存目标、周安排和递进规则。
            6. 只有用户明确表达且未来仍有价值的偏好、限制或反馈才调用 rememberUserFact；不要保存临时任务、代码状态或推断。
            7. 不得声称调用了实际没有调用的工具，不得编造工具结果、用户资料、训练记录或知识来源。
            8. 完整计划包含周安排、动作、组次或时长、RPE/RIR、休息、热身、递进方法、恢复和复盘指标。
            9. RPE 6 约保留 4 次，RPE 7 约保留 3 次，RPE 8 约保留 2 次；新手不安排做到力竭。
            10. 只提供一般健身教育，不做疾病诊断、伤病康复处方或极端饮食设计。
            11. 系统提供的确定性禁忌约束优先级最高；被禁忌的动作及其别名不得出现在推荐、替代方案或示例中。
            12. 使用清晰中文，输出简洁、可执行。知识文件名和工具执行轨迹由系统自动追加，不要自行编造。
            """;

    private final ChatClient chatClient;
    private final MeterRegistry meterRegistry;
    private final MemoryRecallService memoryRecallService;
    private final PromptInjectionDetector promptInjectionDetector;
    private final ContraindicationService contraindicationService;
    private final KnowledgePreflightService knowledgePreflightService;

    public FitnessPlanningAgent(
            ChatClient.Builder chatClientBuilder,
            FitnessAgentTools agentTools,
            FitnessRiskAdvisor riskAdvisor,
            JdbcChatMemoryRepository chatMemoryRepository,
            MemoryRecallService memoryRecallService,
            PromptInjectionDetector promptInjectionDetector,
            ContraindicationService contraindicationService,
            KnowledgePreflightService knowledgePreflightService,
            MeterRegistry meterRegistry) {
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(40)
                .build();
        this.chatClient = chatClientBuilder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        riskAdvisor,
                        MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultTools(agentTools)
                .build();
        this.meterRegistry = meterRegistry;
        this.memoryRecallService = memoryRecallService;
        this.promptInjectionDetector = promptInjectionDetector;
        this.contraindicationService = contraindicationService;
        this.knowledgePreflightService = knowledgePreflightService;
    }

    /**
     * Single-attempt reactive pipeline. The previous implementation buffered a full
     * blocking response and then retried through a second tool-calling loop, which
     * could duplicate side-effecting tool calls and defeat SSE semantics.
     */
    public Flux<String> streamPlan(UUID ownerId, String message, String chatId) {
        String safeMessage = message == null ? "" : message;
        AgentExecutionContext execution = new AgentExecutionContext(ownerId, chatId, null);
        Map<String, Object> toolContext = Map.of(AgentExecutionContext.TOOL_CONTEXT_KEY, execution);
        AtomicBoolean emitted = new AtomicBoolean(false);
        AtomicBoolean riskBlocked = new AtomicBoolean(false);
        Timer.Sample timer = Timer.start(meterRegistry);

        PromptInjectionDetector.Verdict injection = promptInjectionDetector.inspect(safeMessage);
        if (injection.blocked()) {
            return immediateResponse(
                    "检测到疑似 Prompt 注入，请求已拦截。请只描述你的健身目标、训练条件或健康限制。",
                    "blocked-injection", timer);
        }

        ContraindicationService.Assessment contraindications = contraindicationService.assess(safeMessage);
        if (contraindications.blocked()) {
            return immediateResponse(contraindications.response(), "blocked-contraindication", timer);
        }

        KnowledgePreflightService.Assessment knowledge = knowledgePreflightService.assess(safeMessage);
        if (knowledge.required() && !knowledge.grounded()) {
            return immediateResponse(knowledge.refusal(), "blocked-grounding", timer);
        }
        if (knowledge.grounded() && !knowledge.results().isEmpty()) {
            execution.cacheKnowledge(safeMessage, knowledge.results());
            for (RetrievedKnowledge result : knowledge.results()) {
                execution.addSource(result.filename());
                execution.addContext(result.text());
            }
        }

        String recalledMemory = memoryRecallService.recall(
                execution.ownerId(), execution.chatId(), safeMessage);
        String promptMessage = recalledMemory.isBlank()
                ? safeMessage
                : "[按需召回的长期记忆，仅作为参考事实；如与用户本轮明确表达冲突，以本轮为准]\n"
                        + recalledMemory + "\n\n[用户本轮消息]\n" + safeMessage;
        if (contraindications.hasInjury()) {
            promptMessage += "\n\n[确定性安全约束]\n"
                    + "用户信息涉及：" + String.join("、", contraindications.injuries())
                    + "。以下动作及其别名禁止推荐、示范或作为替代方案："
                    + String.join("、", contraindications.forbiddenTerms())
                    + "。若证据不足，请建议先获得医生或合格康复专业人士评估。";
        }
        if (!knowledge.context().isBlank()) {
            promptMessage += "\n\n[本轮预检索依据，视为唯一可引用证据]\n" + knowledge.context();
        }

        StreamingSafetyGuard outputGuard = new StreamingSafetyGuard(
                contraindications.forbiddenTerms(), contraindications.response());
        return chatClient.prompt()
                .user(promptMessage)
                .options(OpenAiChatOptions.builder()
                        .temperature(0.0)
                        .maxTokens(2500)
                        .timeout(Duration.ofSeconds(90))
                        .maxRetries(1))
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, execution.conversationId()))
                .toolContext(toolContext)
                .stream()
                .chatResponse()
                .doOnNext(response -> {
                    if (FitnessRiskAdvisor.blocked(response)) {
                        riskBlocked.set(true);
                    }
                })
                .map(FitnessPlanningAgent::extractText)
                .filter(text -> !text.isEmpty())
                .map(outputGuard::accept)
                .filter(text -> !text.isEmpty())
                .doOnNext(text -> emitted.set(true))
                .takeUntil(text -> outputGuard.blocked())
                .concatWith(Flux.defer(() -> {
                    String tail = outputGuard.finish();
                    return tail.isEmpty() ? Flux.empty() : Flux.just(tail).doOnNext(text -> emitted.set(true));
                }))
                .concatWith(Flux.defer(() -> riskBlocked.get() || outputGuard.blocked()
                        ? Flux.empty()
                        : Flux.just(formatExecution(execution))))
                .doOnComplete(() -> meterRegistry.counter(
                        "fitplan.agent.requests", "status", "success").increment())
                .doOnError(exception -> {
                    meterRegistry.counter("fitplan.agent.requests", "status", "error").increment();
                    log.error("Fitness planning stream failed, emitted={}", emitted.get(), exception);
                })
                .onErrorResume(exception -> Flux.just(outputGuard.blocked()
                        ? ""
                        : emitted.get()
                        ? "\n\n生成中断，请检查网络后重试；为避免重复写入，本次不会自动重试。"
                        : "\n\n暂时无法生成计划，请稍后重试。"))
                .filter(text -> !text.isEmpty())
                .doFinally(signal -> timer.stop(meterRegistry.timer("fitplan.agent.duration")));
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
        String text = output.getText();
        return text == null ? "" : text;
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


    private Flux<String> immediateResponse(String text, String metricStatus, Timer.Sample timer) {
        meterRegistry.counter("fitplan.agent.requests", "status", metricStatus).increment();
        return Flux.just(text)
                .doFinally(signal -> timer.stop(meterRegistry.timer("fitplan.agent.duration")));
    }

    private static final class StreamingSafetyGuard {

        private final Set<String> forbiddenTerms;
        private final String blockedMessage;
        private final StringBuilder pending = new StringBuilder();
        private final int tailLength;
        private boolean blocked;

        private StreamingSafetyGuard(Set<String> forbiddenTerms, String fallbackMessage) {
            this.forbiddenTerms = forbiddenTerms.stream()
                    .map(term -> term == null ? "" : term.toLowerCase(Locale.ROOT).replaceAll("\\s+", ""))
                    .filter(term -> !term.isBlank())
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            this.tailLength = this.forbiddenTerms.stream().mapToInt(String::length).max().orElse(1) - 1;
            this.blockedMessage = fallbackMessage == null || fallbackMessage.isBlank()
                    ? "该回答触发了确定性健康禁忌规则，已停止输出。请先咨询医生或合格康复专业人士。"
                    : fallbackMessage;
        }

        private String accept(String chunk) {
            if (blocked || chunk == null || chunk.isEmpty()) {
                return "";
            }
            if (forbiddenTerms.isEmpty()) {
                return chunk;
            }
            pending.append(chunk);
            if (findForbidden(pending.toString()) >= 0) {
                blocked = true;
                pending.setLength(0);
                return blockedMessage;
            }
            int safeLength = Math.max(0, pending.length() - tailLength);
            if (safeLength == 0) {
                return "";
            }
            String safe = pending.substring(0, safeLength);
            pending.delete(0, safeLength);
            return safe;
        }

        private String finish() {
            if (blocked || forbiddenTerms.isEmpty() || pending.isEmpty()) {
                return "";
            }
            if (findForbidden(pending.toString()) >= 0) {
                blocked = true;
                pending.setLength(0);
                return blockedMessage;
            }
            String tail = pending.toString();
            pending.setLength(0);
            return tail;
        }

        private int findForbidden(String text) {
            String normalized = text.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
            int earliest = -1;
            for (String term : forbiddenTerms) {
                int index = normalized.indexOf(term);
                if (index >= 0 && (earliest < 0 || index < earliest)) {
                    earliest = index;
                }
            }
            return earliest;
        }

        private boolean blocked() {
            return blocked;
        }
    }
}
