package com.fitplan.rag.service;

import com.fitplan.rag.knowledge.HybridFitnessKnowledgeRetriever;
import com.fitplan.rag.knowledge.FitnessQueryRewriter;
import com.fitplan.rag.service.FitnessAgentStateRepository;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FitnessAgentToolsTest {

    @Test
    void exposesSevenToolsWithoutLeakingToolContextIntoModelSchema() {
        FitnessAgentTools tools = new FitnessAgentTools(
                mock(HybridFitnessKnowledgeRetriever.class), passthroughRewriter(),
                new FitnessAgentStateRepository());

        ToolCallback[] callbacks = ToolCallbacks.from(tools);

        assertThat(callbacks).hasSize(7);
        assertThat(Arrays.stream(callbacks)
                .map(callback -> callback.getToolDefinition().name()))
                .containsExactlyInAnyOrder(
                        "searchFitnessKnowledge",
                        "getUserProfile",
                        "updateUserProfile",
                        "saveTrainingLog",
                        "getRecentTrainingLogs",
                        "savePlanSummary",
                        "getCurrentPlan");
        assertThat(Arrays.stream(callbacks)
                .map(callback -> callback.getToolDefinition().inputSchema()))
                .allMatch(schema -> !schema.contains("ToolContext") && !schema.contains("chatId"));
    }

    @Test
    void knowledgeToolReturnsTextAndTracksActualSource() {
        HybridFitnessKnowledgeRetriever retriever = mock(HybridFitnessKnowledgeRetriever.class);
        when(retriever.retrieve("RPE 8 是什么", "RPE 8 是什么")).thenReturn(List.of(
                new HybridFitnessKnowledgeRetriever.RetrievedKnowledge(
                        "RPE 8 表示大约还可以完成2次重复",
                        "02-strength-training.md",
                        "chunk-1",
                        3,
                        0.82,
                        "dense+lexical+rrf")));
        FitnessAgentTools tools = new FitnessAgentTools(
                retriever, passthroughRewriter(), new FitnessAgentStateRepository());
        AgentExecutionContext execution = new AgentExecutionContext("chat-1");
        ToolContext toolContext = new ToolContext(Map.of(
                AgentExecutionContext.TOOL_CONTEXT_KEY, execution));

        List<FitnessAgentTools.KnowledgeResult> results = tools.searchFitnessKnowledge(
                "RPE 8 是什么", toolContext);

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.filename()).isEqualTo("02-strength-training.md");
            assertThat(result.chunkId()).isEqualTo("chunk-1");
            assertThat(result.relevanceScore()).isEqualTo(0.82);
            assertThat(result.retrievalMethod()).isEqualTo("dense+lexical+rrf");
        });
        assertThat(execution.sourceFiles()).containsExactly("02-strength-training.md");
        assertThat(execution.toolCalls()).containsExactly("searchFitnessKnowledge");
    }

    @Test
    void rejectsMissingExecutionContext() {
        FitnessAgentTools tools = new FitnessAgentTools(
                mock(HybridFitnessKnowledgeRetriever.class), passthroughRewriter(),
                new FitnessAgentStateRepository());

        assertThatThrownBy(() -> tools.getUserProfile(new ToolContext(Map.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("execution context");
    }

    @Test
    void stopsRunawayToolCalls() {
        AgentExecutionContext execution = new AgentExecutionContext("chat-1");
        for (int index = 0; index < 8; index++) {
            execution.recordToolCall("tool-" + index);
        }

        assertThatThrownBy(() -> execution.recordToolCall("tool-9"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("limit exceeded");
    }

    private static FitnessQueryRewriter passthroughRewriter() {
        FitnessQueryRewriter rewriter = mock(FitnessQueryRewriter.class);
        when(rewriter.rewrite(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        return rewriter;
    }
}
