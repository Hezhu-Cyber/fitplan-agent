package com.fitplan.rag.knowledge;

import com.fitplan.rag.config.FitnessRagProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FitnessQueryRewriterTest {

    @Test
    void returnsRewrittenQuery() {
        QueryTransformer transformer = mock(QueryTransformer.class);
        when(transformer.transform(new Query("一周练仨回咋排啊")))
                .thenReturn(new Query("每周训练三次的健身计划如何安排"));
        FitnessQueryRewriter rewriter = new FitnessQueryRewriter(
                transformer, new FitnessRagProperties(), new SimpleMeterRegistry());

        assertThat(rewriter.rewrite("一周练仨回咋排啊"))
                .isEqualTo("每周训练三次的健身计划如何安排");
    }

    @Test
    void fallsBackToOriginalQueryWhenRewriteFails() {
        QueryTransformer transformer = mock(QueryTransformer.class);
        when(transformer.transform(new Query("RPE 8 啥意思")))
                .thenThrow(new IllegalStateException("model unavailable"));
        FitnessQueryRewriter rewriter = new FitnessQueryRewriter(
                transformer, new FitnessRagProperties(), new SimpleMeterRegistry());

        assertThat(rewriter.rewrite(" RPE 8 啥意思 ")).isEqualTo("RPE 8 啥意思");
    }

    @Test
    void bypassesRewriteWhenDisabled() {
        FitnessRagProperties properties = new FitnessRagProperties();
        properties.setQueryRewriteEnabled(false);
        FitnessQueryRewriter rewriter = new FitnessQueryRewriter(
                mock(QueryTransformer.class), properties, new SimpleMeterRegistry());

        assertThat(rewriter.rewrite("咋练胸啊")).isEqualTo("咋练胸啊");
    }
}
