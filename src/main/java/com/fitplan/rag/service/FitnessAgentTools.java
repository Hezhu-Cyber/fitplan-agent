package com.fitplan.rag.service;

import com.fitplan.rag.knowledge.HybridFitnessKnowledgeRetriever;
import com.fitplan.rag.knowledge.HybridFitnessKnowledgeRetriever.RetrievedKnowledge;
import com.fitplan.rag.knowledge.FitnessQueryRewriter;
import com.fitplan.rag.safety.PromptInjectionDetector;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FitnessAgentTools {

    private final HybridFitnessKnowledgeRetriever knowledgeRetriever;
    private final FitnessQueryRewriter queryRewriter;
    private final FitnessAgentStateRepository stateRepository;
    private final FileMemoryStore memoryStore;
    private final ContextArtifactStore artifactStore;
    private final PromptInjectionDetector promptInjectionDetector;

    /** Compatibility constructor for focused unit tests and local callers. */
    public FitnessAgentTools(
            HybridFitnessKnowledgeRetriever knowledgeRetriever,
            FitnessQueryRewriter queryRewriter,
            FitnessAgentStateRepository stateRepository) {
        this(knowledgeRetriever, queryRewriter, stateRepository, new FileMemoryStore("Data/memory"),
                new ContextArtifactStore("Data/memory-artifacts"), new PromptInjectionDetector());
    }

    @Autowired
    public FitnessAgentTools(
            HybridFitnessKnowledgeRetriever knowledgeRetriever,
            FitnessQueryRewriter queryRewriter,
            FitnessAgentStateRepository stateRepository,
            FileMemoryStore memoryStore,
            ContextArtifactStore artifactStore,
            PromptInjectionDetector promptInjectionDetector) {
        this.knowledgeRetriever = knowledgeRetriever;
        this.queryRewriter = queryRewriter;
        this.stateRepository = stateRepository;
        this.memoryStore = memoryStore;
        this.artifactStore = artifactStore;
        this.promptInjectionDetector = promptInjectionDetector;
    }

    @Tool(name = "searchFitnessKnowledge",
            description = "从本地循证健身知识库检索与问题最相关的知识。回答训练、减脂、恢复、安全或计划问题前应调用。")
    public List<KnowledgeResult> searchFitnessKnowledge(
            @ToolParam(description = "需要检索的完整健身问题") String question,
            ToolContext toolContext) {
        AgentExecutionContext execution = execution(toolContext, "searchFitnessKnowledge");
        List<RetrievedKnowledge> results = execution.cachedKnowledge(question).orElseGet(() -> {
            String retrievalQuery = queryRewriter.rewrite(question);
            List<RetrievedKnowledge> retrieved = knowledgeRetriever.retrieve(question, retrievalQuery);
            execution.cacheKnowledge(question, retrieved);
            return retrieved;
        });
        for (RetrievedKnowledge document : results) {
            execution.addSource(document.filename());
            execution.addContext(document.text());
        }
        return results.stream().map(document -> new KnowledgeResult(
                document.text(), document.filename(), document.chunkId(), document.chunkIndex(),
                document.relevanceScore(), document.retrievalMethod())).toList();
    }

    @Tool(name = "getUserProfile",
            description = "读取当前会话已保存的用户年龄、目标、经验、时间、器械和健康限制。制定或调整计划前应调用。")
    public FitnessAgentStateRepository.UserProfile getUserProfile(ToolContext toolContext) {
        AgentExecutionContext execution = execution(toolContext, "getUserProfile");
        return stateRepository.getProfile(execution.ownerId(), execution.chatId());
    }

    @Tool(name = "updateUserProfile",
            description = "把用户本轮明确提供的画像信息保存到当前会话。不得猜测或填写用户没有提供的信息。")
    public FitnessAgentStateRepository.UserProfile updateUserProfile(
            @ToolParam(description = "只填写用户明确提供的字段；未知字段传空字符串")
            FitnessAgentStateRepository.ProfileUpdate update,
            ToolContext toolContext) {
        AgentExecutionContext execution = execution(toolContext, "updateUserProfile");
        rejectPersistentInjection(String.join("|",
                safe(update.age()), safe(update.goal()), safe(update.experience()), safe(update.weeklyDays()),
                safe(update.sessionMinutes()), safe(update.equipment()), safe(update.healthNotes())));
        return stateRepository.updateProfile(execution.ownerId(), execution.chatId(), update);
    }

    @Tool(name = "saveTrainingLog",
            description = "保存用户明确报告的已完成训练记录，包括动作、组次、重量、RPE和备注。")
    public FitnessAgentStateRepository.TrainingLog saveTrainingLog(
            @ToolParam(description = "用户明确报告的训练记录")
            FitnessAgentStateRepository.TrainingLogInput input,
            ToolContext toolContext) {
        AgentExecutionContext execution = execution(toolContext, "saveTrainingLog");
        rejectPersistentInjection(String.join("|",
                safe(input.date()), safe(input.exercise()), safe(input.setsAndReps()),
                safe(input.load()), safe(input.rpe()), safe(input.notes())));
        String fingerprint = String.join("|",
                safe(input.date()), safe(input.exercise()), safe(input.setsAndReps()),
                safe(input.load()), safe(input.rpe()), safe(input.notes()));
        return stateRepository.addTrainingLog(
                execution.ownerId(),
                execution.chatId(),
                input,
                execution.scopedOperationId(fingerprint));
    }

    @Tool(name = "getRecentTrainingLogs",
            description = "读取当前会话最近10条训练记录。根据历史表现调整负荷或训练量前应调用。")
    public List<FitnessAgentStateRepository.TrainingLog> getRecentTrainingLogs(ToolContext toolContext) {
        AgentExecutionContext execution = execution(toolContext, "getRecentTrainingLogs");
        return stateRepository.recentTrainingLogs(execution.ownerId(), execution.chatId());
    }

    @Tool(name = "savePlanSummary",
            description = "保存已经生成或调整完成的训练计划摘要，供下一轮对话继续修改。")
    public FitnessAgentStateRepository.PlanSummary savePlanSummary(
            @ToolParam(description = "训练计划的目标、周安排和递进规则")
            FitnessAgentStateRepository.PlanSummaryInput input,
            ToolContext toolContext) {
        AgentExecutionContext execution = execution(toolContext, "savePlanSummary");
        rejectPersistentInjection(String.join("|",
                safe(input.goal()), safe(input.weeklySchedule()), safe(input.progressionRule())));
        return stateRepository.savePlan(execution.ownerId(), execution.chatId(), input);
    }

    @Tool(name = "getCurrentPlan",
            description = "读取当前会话最近保存的训练计划摘要。修改已有计划前应调用。")
    public FitnessAgentStateRepository.PlanSummary getCurrentPlan(ToolContext toolContext) {
        AgentExecutionContext execution = execution(toolContext, "getCurrentPlan");
        return stateRepository.getPlan(execution.ownerId(), execution.chatId());
    }

    @Tool(name = "rememberUserFact",
            description = "保存用户明确表达、未来对话仍有价值的偏好或长期事实为 Markdown 记忆。不要保存临时任务状态、代码或未经确认的推断。")
    public String rememberUserFact(
            @ToolParam(description = "简短稳定的记忆标题，例如 equipment-preference") String title,
            @ToolParam(description = "记忆分类，例如 preference、constraint、feedback") String category,
            @ToolParam(description = "要长期保留的事实，必须来自用户明确表达") String content,
            ToolContext toolContext) {
        AgentExecutionContext execution = execution(toolContext, "rememberUserFact");
        rejectPersistentInjection(String.join("|", safe(title), safe(category), safe(content)));
        memoryStore.save(execution.ownerId(), execution.chatId(), title, category, content);
        return "已保存长期记忆：" + title;
    }

    @Tool(name = "readContextArtifact",
            description = "读取被上下文压缩归档的完整工具结果。只有需要完整结果时才调用。")
    public String readContextArtifact(
            @ToolParam(description = "压缩消息中提供的归档文件路径") String path,
            ToolContext toolContext) {
        execution(toolContext, "readContextArtifact");
        return artifactStore.read(path);
    }

    private static AgentExecutionContext execution(ToolContext toolContext, String toolName) {
        Object value = toolContext.getContext().get(AgentExecutionContext.TOOL_CONTEXT_KEY);
        if (!(value instanceof AgentExecutionContext execution)) {
            throw new IllegalStateException("Missing FitPlan Agent execution context");
        }
        execution.recordToolCall(toolName);
        return execution;
    }

    private void rejectPersistentInjection(String text) {
        if (promptInjectionDetector.inspect(text).blocked()) {
            throw new IllegalArgumentException("Tool input rejected by prompt-injection policy");
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public record KnowledgeResult(
            String text,
            String filename,
            String chunkId,
            int chunkIndex,
            double relevanceScore,
            String retrievalMethod) {
    }
}
