package com.fitplan.rag.service;

import com.fitplan.rag.knowledge.HybridFitnessKnowledgeRetriever;
import com.fitplan.rag.knowledge.HybridFitnessKnowledgeRetriever.RetrievedKnowledge;
import com.fitplan.rag.knowledge.FitnessQueryRewriter;
import com.fitplan.rag.service.FitnessAgentStateRepository;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FitnessAgentTools {

    private final HybridFitnessKnowledgeRetriever knowledgeRetriever;
    private final FitnessQueryRewriter queryRewriter;
    private final FitnessAgentStateRepository stateRepository;

    public FitnessAgentTools(
            HybridFitnessKnowledgeRetriever knowledgeRetriever,
            FitnessQueryRewriter queryRewriter,
            FitnessAgentStateRepository stateRepository) {
        this.knowledgeRetriever = knowledgeRetriever;
        this.queryRewriter = queryRewriter;
        this.stateRepository = stateRepository;
    }

    @Tool(name = "searchFitnessKnowledge",
            description = "从本地循证健身知识库检索与问题最相关的知识。回答训练、减脂、恢复、安全或计划问题前应调用。")
    public List<KnowledgeResult> searchFitnessKnowledge(
            @ToolParam(description = "需要检索的完整健身问题") String question,
            ToolContext toolContext) {
        AgentExecutionContext execution = execution(toolContext, "searchFitnessKnowledge");
        String retrievalQuery = queryRewriter.rewrite(question);
        List<RetrievedKnowledge> results = knowledgeRetriever.retrieve(question, retrievalQuery);
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
        return stateRepository.getProfile(execution.chatId());
    }

    @Tool(name = "updateUserProfile",
            description = "把用户本轮明确提供的画像信息保存到当前会话。不得猜测或填写用户没有提供的信息。")
    public FitnessAgentStateRepository.UserProfile updateUserProfile(
            @ToolParam(description = "只填写用户明确提供的字段；未知字段传空字符串")
            FitnessAgentStateRepository.ProfileUpdate update,
            ToolContext toolContext) {
        AgentExecutionContext execution = execution(toolContext, "updateUserProfile");
        return stateRepository.updateProfile(execution.chatId(), update);
    }

    @Tool(name = "saveTrainingLog",
            description = "保存用户明确报告的已完成训练记录，包括动作、组次、重量、RPE和备注。")
    public FitnessAgentStateRepository.TrainingLog saveTrainingLog(
            @ToolParam(description = "用户明确报告的训练记录")
            FitnessAgentStateRepository.TrainingLogInput input,
            ToolContext toolContext) {
        AgentExecutionContext execution = execution(toolContext, "saveTrainingLog");
        return stateRepository.addTrainingLog(execution.chatId(), input);
    }

    @Tool(name = "getRecentTrainingLogs",
            description = "读取当前会话最近10条训练记录。根据历史表现调整负荷或训练量前应调用。")
    public List<FitnessAgentStateRepository.TrainingLog> getRecentTrainingLogs(ToolContext toolContext) {
        AgentExecutionContext execution = execution(toolContext, "getRecentTrainingLogs");
        return stateRepository.recentTrainingLogs(execution.chatId());
    }

    @Tool(name = "savePlanSummary",
            description = "保存已经生成或调整完成的训练计划摘要，供下一轮对话继续修改。")
    public FitnessAgentStateRepository.PlanSummary savePlanSummary(
            @ToolParam(description = "训练计划的目标、周安排和递进规则")
            FitnessAgentStateRepository.PlanSummaryInput input,
            ToolContext toolContext) {
        AgentExecutionContext execution = execution(toolContext, "savePlanSummary");
        return stateRepository.savePlan(execution.chatId(), input);
    }

    @Tool(name = "getCurrentPlan",
            description = "读取当前会话最近保存的训练计划摘要。修改已有计划前应调用。")
    public FitnessAgentStateRepository.PlanSummary getCurrentPlan(ToolContext toolContext) {
        AgentExecutionContext execution = execution(toolContext, "getCurrentPlan");
        return stateRepository.getPlan(execution.chatId());
    }

    private static AgentExecutionContext execution(ToolContext toolContext, String toolName) {
        Object value = toolContext.getContext().get(AgentExecutionContext.TOOL_CONTEXT_KEY);
        if (!(value instanceof AgentExecutionContext execution)) {
            throw new IllegalStateException("Missing FitPlan Agent execution context");
        }
        execution.recordToolCall(toolName);
        return execution;
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
