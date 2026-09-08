package com.fitplan.rag.service;

import com.fitplan.rag.service.AgentExecutionContext;
import com.fitplan.rag.service.FitnessAgentTools;
import com.fitplan.rag.knowledge.HybridFitnessKnowledgeRetriever;
import com.fitplan.rag.knowledge.HybridFitnessKnowledgeRetriever.RetrievedKnowledge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 评测专用服务：执行 RAG 检索并调用 Agent 生成答案，
 * 返回 {question, contexts, answer, source_files} 给外部 RAGAS 评估脚本。
 */
@Service
public class FitnessEvalService {

    private static final Logger log = LoggerFactory.getLogger(FitnessEvalService.class);
    private static final String SYSTEM_PROMPT = """
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
    private final HybridFitnessKnowledgeRetriever knowledgeRetriever;

    public FitnessEvalService(
            ChatClient.Builder chatClientBuilder,
            FitnessAgentTools agentTools,
            HybridFitnessKnowledgeRetriever knowledgeRetriever) {
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(20)
                .build();
        this.chatClient = chatClientBuilder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultTools(agentTools)
                .build();
        this.knowledgeRetriever = knowledgeRetriever;
    }

    public EvalResult evaluate(String question) {
        return evaluate(question, "hybrid");
    }

    public EvalResult evaluate(String question, String retrievalMode) {
        long start = System.currentTimeMillis();
        HybridFitnessKnowledgeRetriever.RetrievalMode mode = parseRetrievalMode(retrievalMode);

        List<RetrievedKnowledge> retrieved = knowledgeRetriever.retrieve(question, mode);
        List<String> contexts = retrieved.stream()
                .map(RetrievedKnowledge::text)
                .toList();
        Set<String> sources = new LinkedHashSet<>();
        for (RetrievedKnowledge r : retrieved) {
            sources.add(r.filename());
        }

        String chatId = "eval-" + UUID.randomUUID();
        AgentExecutionContext execution = new AgentExecutionContext(chatId);
        Map<String, Object> toolContext = Map.of(AgentExecutionContext.TOOL_CONTEXT_KEY, execution);

        String answer = chatClient.prompt()
                .user(question)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .toolContext(toolContext)
                .call()
                .chatResponse()
                .getResult()
                .getOutput()
                .getText();

        long latencyMs = System.currentTimeMillis() - start;
        log.info("Eval finished question='{}' contexts={} latencyMs={}",
                abbreviate(question), contexts.size(), latencyMs);

        return new EvalResult(
                question,
                new ArrayList<>(contexts),
                answer == null ? "" : answer,
                new ArrayList<>(sources),
                execution.toolCalls(),
                execution.contexts(),
                latencyMs);
    }

    private static HybridFitnessKnowledgeRetriever.RetrievalMode parseRetrievalMode(String mode) {
        if (mode == null) {
            return HybridFitnessKnowledgeRetriever.RetrievalMode.HYBRID;
        }
        return switch (mode.toLowerCase(Locale.ROOT)) {
            case "dense" -> HybridFitnessKnowledgeRetriever.RetrievalMode.DENSE;
            case "lexical" -> HybridFitnessKnowledgeRetriever.RetrievalMode.LEXICAL;
            default -> HybridFitnessKnowledgeRetriever.RetrievalMode.HYBRID;
        };
    }

    private static String abbreviate(String text) {
        if (text == null) return "";
        return text.length() > 40 ? text.substring(0, 40) + "..." : text;
    }

    public record EvalResult(
            String question,
            List<String> contexts,
            String answer,
            List<String> sourceFiles,
            List<String> toolCalls,
            List<String> agentContexts,
            long latencyMs) {
    }
}