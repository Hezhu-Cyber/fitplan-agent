package com.fitplan.rag.knowledge;

import com.fitplan.rag.config.FitnessRagProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class IncrementalRagIndexerTest {

    @Test
    void indexesReviewedMarkdownSectionsAndKeepsRequiredMetadata() {
        FitnessDocumentLoader loader = new FitnessDocumentLoader(
                new PathMatchingResourcePatternResolver(),
                "classpath:markdown-corpus-test/valid/*.md");
        IncrementalRagIndexer indexer = indexer(
                mock(VectorStore.class), loader, mock(RagIndexRepository.class));
        FitnessDocumentLoader.KnowledgeSource source = loader.loadSources().getFirst();

        List<Document> chunks = indexer.createChunks(source);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.getFirst().getText()).contains("成年人每周至少应进行两天肌肉强化活动");
        assertThat(chunks.get(1).getText()).contains("充足睡眠有助于身体恢复");
        assertThat(chunks).allSatisfy(document -> {
            assertThat(document.getMetadata())
                    .containsEntry("licenseId", "US-PD-ODPHP")
                    .containsEntry("language", "zh-CN")
                    .containsEntry("source_id", "sample.md")
                    .containsEntry("document_title", "官方健身指南")
                    .containsKey("section_title")
                    .containsKey("section_index")
                    .containsKey("chunk_index")
                    .containsEntry("chunk_schema_version", IncrementalRagIndexer.CHUNK_SCHEMA_VERSION)
                    .containsEntry("chunkSchemaVersion", IncrementalRagIndexer.CHUNK_SCHEMA_VERSION);
        });
        assertThat(chunks).extracting(document -> document.getMetadata().get("sourceChunkIndex"))
                .containsExactly(0, 1);
    }

    @Test
    void tokenSplitsLongChineseSectionWithoutCrossingSectionBoundary() {
        IncrementalRagIndexer indexer = indexer(
                mock(VectorStore.class), mock(FitnessDocumentLoader.class), mock(RagIndexRepository.class));
        String longSection = "渐进超负荷需要根据训练表现逐步调整重量、次数和训练量。".repeat(500);
        Map<String, Object> metadata = Map.of(
                "sourceChunkIndex", 0,
                "corpusChunkId", "11111111-1111-5111-8111-111111111111",
                "sectionTitle", "渐进超负荷",
                "sourceTitle", "力量训练指南",
                "language", "zh-CN");
        FitnessDocumentLoader.KnowledgeSource source = new FitnessDocumentLoader.KnowledgeSource(
                "strength.md", "strength.md", "hash-one",
                List.of(new Document(longSection, metadata)));

        List<Document> chunks = indexer.createChunks(source);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).extracting(document -> document.getMetadata().get("section_index"))
                .containsOnly(0);
        assertThat(chunks).extracting(document -> document.getMetadata().get("section_title"))
                .containsOnly("渐进超负荷");
        assertThat(chunks).extracting(document -> document.getMetadata().get("chunk_index"))
                .containsExactlyElementsOf(java.util.stream.IntStream.range(0, chunks.size()).boxed().toList());
    }

    @Test
    void createsStableIdsForSameSourceVersionAndNewIdsWhenContentChanges() {
        IncrementalRagIndexer indexer = new IncrementalRagIndexer(
                mock(VectorStore.class),
                mock(FitnessDocumentLoader.class),
                mock(RagIndexRepository.class),
                new FitnessRagProperties(),
                new SimpleMeterRegistry(),
                mock(ApplicationEventPublisher.class),
                "text-embedding-v4",
                1024);
        FitnessDocumentLoader.KnowledgeSource versionOne = source("hash-one");
        FitnessDocumentLoader.KnowledgeSource versionTwo = source("hash-two");

        List<Document> firstRun = indexer.createChunks(versionOne);
        List<Document> secondRun = indexer.createChunks(versionOne);
        List<Document> changedRun = indexer.createChunks(versionTwo);

        assertThat(firstRun).extracting(Document::getId)
                .containsExactlyElementsOf(secondRun.stream().map(Document::getId).toList());
        assertThat(changedRun).extracting(Document::getId)
                .doesNotContainAnyElementsOf(firstRun.stream().map(Document::getId).toList());
        assertThat(firstRun).allSatisfy(document -> {
            assertThat(document.getText()).startsWith("力量训练应采用渐进超负荷");
            assertThat(document.getMetadata()).containsEntry("contentHash", "hash-one");
        });
    }

    @Test
    void skipsEmbeddingWhenSourceAndEmbeddingConfigurationAreUnchanged() {
        VectorStore vectorStore = mock(VectorStore.class);
        FitnessDocumentLoader loader = mock(FitnessDocumentLoader.class);
        RagIndexRepository repository = mock(RagIndexRepository.class);
        FitnessDocumentLoader.KnowledgeSource source = source("hash-one");
        UUID jobId = UUID.randomUUID();
        when(repository.tryAcquireLock(anyString(), anyString(), anyInt())).thenReturn(true);
        when(repository.createJob(anyString())).thenReturn(jobId);
        when(loader.loadSources()).thenReturn(List.of(source));
        when(repository.findManifests()).thenReturn(Map.of("strength.md", manifest("hash-one")));

        IncrementalRagIndexer indexer = indexer(vectorStore, loader, repository);
        IncrementalRagIndexer.IndexOutcome outcome = indexer.indexNow();

        assertThat(outcome.stats().skippedSources()).isEqualTo(1);
        assertThat(outcome.stats().indexedChunks()).isZero();
        verify(vectorStore, never()).add(anyList());
        verify(repository).completeJob(eq(jobId), any());
        verify(repository).releaseLock(anyString(), anyString());
    }

    @Test
    void deletesOldSourceChunksBeforeRebuildingAndPreservesManifestWhenEmbeddingFails() {
        VectorStore vectorStore = mock(VectorStore.class);
        FitnessDocumentLoader loader = mock(FitnessDocumentLoader.class);
        RagIndexRepository repository = mock(RagIndexRepository.class);
        UUID jobId = UUID.randomUUID();
        when(repository.tryAcquireLock(anyString(), anyString(), anyInt())).thenReturn(true);
        when(repository.createJob(anyString())).thenReturn(jobId);
        when(loader.loadSources()).thenReturn(List.of(source("new-hash")));
        when(repository.findManifests()).thenReturn(Map.of("strength.md", manifest("old-hash")));
        when(repository.findVectorIds("strength.md")).thenReturn(List.of("old-vector"));
        doThrow(new IllegalStateException("embedding unavailable")).when(vectorStore).add(anyList());

        IncrementalRagIndexer indexer = indexer(vectorStore, loader, repository);

        assertThatThrownBy(indexer::indexNow)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("embedding unavailable");
        InOrder writeOrder = inOrder(vectorStore);
        writeOrder.verify(vectorStore).delete(List.of("old-vector"));
        writeOrder.verify(vectorStore).add(anyList());
        verify(repository, never()).saveManifest(any());
        verify(repository).failJob(eq(jobId), contains("embedding unavailable"));
        verify(repository).releaseLock(anyString(), anyString());
    }

    private static IncrementalRagIndexer indexer(
            VectorStore vectorStore,
            FitnessDocumentLoader loader,
            RagIndexRepository repository) {
        return new IncrementalRagIndexer(
                vectorStore,
                loader,
                repository,
                new FitnessRagProperties(),
                new SimpleMeterRegistry(),
                mock(ApplicationEventPublisher.class),
                "text-embedding-v4",
                1024);
    }

    private static RagIndexRepository.SourceManifest manifest(String hash) {
        return new RagIndexRepository.SourceManifest(
                "strength.md",
                "strength.md",
                hash,
                1,
                "text-embedding-v4",
                1024,
                IncrementalRagIndexer.CHUNK_SCHEMA_VERSION);
    }

    private static FitnessDocumentLoader.KnowledgeSource source(String hash) {
        Map<String, Object> metadata = Map.of(
                "sourceChunkIndex", 0,
                "corpusChunkId", "11111111-1111-5111-8111-111111111111",
                "sectionTitle", "Progressive overload",
                "sourceTitle", "strength.md",
                "publisher", "Example Health Agency",
                "canonicalUrl", "https://example.gov/strength",
                "riskScope", "general_fitness",
                "audience", List.of("healthy_adults"),
                "chunkType", "knowledge");
        return new FitnessDocumentLoader.KnowledgeSource(
                "strength.md",
                "strength.md",
                hash,
                List.of(new Document("力量训练应采用渐进超负荷，并根据训练表现逐步调整负荷。", metadata)));
    }
}
