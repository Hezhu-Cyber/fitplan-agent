package com.fitplan.rag.knowledge;

import com.fitplan.rag.knowledge.HybridFitnessKnowledgeRetriever.RetrievedKnowledge;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KnowledgePreflightServiceTest {

    private final HybridFitnessKnowledgeRetriever retriever = mock(HybridFitnessKnowledgeRetriever.class);
    private final FitnessQueryRewriter rewriter = mock(FitnessQueryRewriter.class);
    private final KnowledgePreflightService service = new KnowledgePreflightService(retriever, rewriter);

    @Test
    void refusesFitnessQuestionWithoutSufficientEvidence() {
        when(rewriter.rewrite(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(retriever.retrieve(anyString(), anyString())).thenReturn(List.of());

        KnowledgePreflightService.Assessment assessment = service.assess("如何制定四周增肌计划？");

        assertThat(assessment.required()).isTrue();
        assertThat(assessment.grounded()).isFalse();
        assertThat(assessment.refusal()).contains("不足", "不生成");
    }

    @Test
    void acceptsQuestionWithSubstantialRetrievedContext() {
        String text = "渐进超负荷应在动作标准的前提下逐步增加重量或次数，并安排恢复。".repeat(5);
        when(rewriter.rewrite(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(retriever.retrieve(anyString(), anyString())).thenReturn(List.of(
                new RetrievedKnowledge(text, "strength.md", "chunk-1", 1, 0.82, "dense+lexical+rrf")));

        KnowledgePreflightService.Assessment assessment = service.assess("增肌训练如何递进？");

        assertThat(assessment.grounded()).isTrue();
        assertThat(assessment.context()).contains("strength.md", "渐进超负荷");
    }

    @Test
    void doesNotRequireKnowledgeForGreeting() {
        KnowledgePreflightService.Assessment assessment = service.assess("你好");

        assertThat(assessment.required()).isFalse();
        assertThat(assessment.grounded()).isTrue();
    }
}
