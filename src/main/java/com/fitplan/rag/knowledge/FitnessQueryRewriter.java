package com.fitplan.rag.knowledge;

import com.fitplan.rag.config.FitnessRagProperties;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Rewrites conversational fitness questions into standalone retrieval queries. */
@Component
public class FitnessQueryRewriter {

    private static final Logger log = LoggerFactory.getLogger(FitnessQueryRewriter.class);
    private static final String TARGET_SEARCH_SYSTEM =
            "a Chinese evidence-based fitness knowledge base searched by pgvector semantic search and Lucene BM25";

    private final QueryTransformer queryTransformer;
    private final FitnessRagProperties properties;
    private final MeterRegistry meterRegistry;

    @Autowired
    public FitnessQueryRewriter(
            ChatClient.Builder chatClientBuilder,
            FitnessRagProperties properties,
            MeterRegistry meterRegistry) {
        this(RewriteQueryTransformer.builder()
                        .chatClientBuilder(chatClientBuilder)
                        .targetSearchSystem(TARGET_SEARCH_SYSTEM)
                        .build(),
                properties,
                meterRegistry);
    }

    FitnessQueryRewriter(
            QueryTransformer queryTransformer,
            FitnessRagProperties properties,
            MeterRegistry meterRegistry) {
        this.queryTransformer = queryTransformer;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    public String rewrite(String question) {
        String original = question == null ? "" : question.trim();
        if (original.isEmpty() || !properties.isQueryRewriteEnabled()) {
            meterRegistry.counter("fitplan.rag.query.rewrite", "status", "bypassed").increment();
            return original;
        }

        try {
            Query rewritten = queryTransformer.transform(new Query(original));
            String text = rewritten == null || rewritten.text() == null ? "" : rewritten.text().trim();
            if (text.isEmpty()) {
                throw new IllegalStateException("query transformer returned an empty query");
            }
            meterRegistry.counter("fitplan.rag.query.rewrite", "status", "success").increment();
            log.debug("Rewrote retrieval query: original='{}', rewritten='{}'", original, text);
            return text;
        } catch (RuntimeException exception) {
            meterRegistry.counter("fitplan.rag.query.rewrite", "status", "fallback").increment();
            log.warn("Query rewrite failed; using the original query: {}", exception.getMessage());
            return original;
        }
    }
}
