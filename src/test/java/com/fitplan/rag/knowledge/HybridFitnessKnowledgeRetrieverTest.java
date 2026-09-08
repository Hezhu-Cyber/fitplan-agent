package com.fitplan.rag.knowledge;

import com.fitplan.rag.config.FitnessRagProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HybridFitnessKnowledgeRetrieverTest {

    @Test
    void rrfRewardsChunksFoundByBothRetrievalChannels() {
        FitnessRagProperties properties = new FitnessRagProperties();
        properties.setCandidateK(10);
        HybridFitnessKnowledgeRetriever retriever = new HybridFitnessKnowledgeRetriever(
                mock(VectorStore.class),
                mock(Bm25LexicalIndexer.class),
                properties,
                mock(DashScopeReranker.class),
                new SimpleMeterRegistry());

        RagCandidate sharedDense = candidate("shared", "dense");
        RagCandidate denseOnly = candidate("dense-only", "dense");
        RagCandidate sharedLexical = candidate("shared", "lexical");
        RagCandidate lexicalOnly = candidate("lexical-only", "lexical");

        List<RagCandidate> fused = retriever.reciprocalRankFusion(
                List.of(sharedDense, denseOnly),
                List.of(lexicalOnly, sharedLexical));

        assertThat(fused).extracting(RagCandidate::chunkId)
                .containsExactly("shared", "lexical-only", "dense-only");
        assertThat(fused.getFirst().retrievalMethod()).isEqualTo("dense+lexical+rrf");
    }

    @Test
    void fallsBackToLexicalRetrievalWhenQueryEmbeddingIsUnavailable() {
        FitnessRagProperties properties = new FitnessRagProperties();
        VectorStore vectorStore = mock(VectorStore.class);
        Bm25LexicalIndexer bm25 = mock(Bm25LexicalIndexer.class);
        DashScopeReranker reranker = mock(DashScopeReranker.class);
        when(vectorStore.similaritySearch(org.mockito.ArgumentMatchers.any(SearchRequest.class)))
                .thenThrow(new IllegalStateException("embedding quota exhausted"));
        when(bm25.search(anyString(), anyInt())).thenReturn(List.of());
        when(reranker.rerank(anyString(), anyList(), anyInt()))
                .thenAnswer(invocation -> invocation.getArgument(1));

        HybridFitnessKnowledgeRetriever retriever = new HybridFitnessKnowledgeRetriever(
                vectorStore, bm25, properties, reranker, new SimpleMeterRegistry());

        assertThat(retriever.retrieve("每周三练增肌")).isEmpty();
    }

    @Test
    void usesRewrittenQueryForDenseAndOriginalQuestionForLexicalAndRerank() {
        FitnessRagProperties properties = new FitnessRagProperties();
        VectorStore vectorStore = mock(VectorStore.class);
        Bm25LexicalIndexer bm25 = mock(Bm25LexicalIndexer.class);
        DashScopeReranker reranker = mock(DashScopeReranker.class);
        when(vectorStore.similaritySearch(org.mockito.ArgumentMatchers.any(SearchRequest.class)))
                .thenReturn(List.of());
        when(bm25.search(anyString(), anyInt())).thenReturn(List.of());
        when(reranker.rerank(anyString(), anyList(), anyInt()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        HybridFitnessKnowledgeRetriever retriever = new HybridFitnessKnowledgeRetriever(
                vectorStore, bm25, properties, reranker, new SimpleMeterRegistry());

        retriever.retrieve("RPE 8 啥意思", "RPE 8 对应的剩余重复次数");

        ArgumentCaptor<SearchRequest> requestCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getQuery()).isEqualTo("RPE 8 对应的剩余重复次数");
        verify(bm25).search("RPE 8 啥意思", properties.getLexicalCandidateK());
        verify(reranker).rerank("RPE 8 啥意思", List.of(), properties.getCandidateK());
    }

    private static RagCandidate candidate(String id, String method) {
        return new RagCandidate(id, "text-" + id, "source.md", 0, 0.5, method);
    }
}
