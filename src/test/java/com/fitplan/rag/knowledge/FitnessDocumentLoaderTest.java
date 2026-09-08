package com.fitplan.rag.knowledge;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FitnessDocumentLoaderTest {

    @Test
    void loadsReviewedMarkdownFixtureAndPreservesProvenance() {
        FitnessDocumentLoader loader = new FitnessDocumentLoader(
                new PathMatchingResourcePatternResolver(),
                "classpath:markdown-corpus-test/valid/*.md");

        List<FitnessDocumentLoader.KnowledgeSource> sources = loader.loadSources();

        assertThat(sources).singleElement().satisfies(source -> {
            assertThat(source.sourceId()).isEqualTo("sample.md");
            assertThat(source.contentHash()).matches("[0-9a-f]{64}");
            assertThat(source.documents()).hasSize(2);
            assertThat(source.documents()).extracting(document ->
                    document.getMetadata().get("sectionTitle"))
                    .containsExactly("肌肉强化", "恢复与睡眠");
            assertThat(source.documents()).allSatisfy(document -> {
                assertThat(document.getText()).isNotBlank();
                assertThat(document.getText())
                        .doesNotContain("license_id")
                        .doesNotContain("---");
                assertThat(document.getMetadata())
                        .containsEntry("sourceTitle", "官方健身指南")
                        .containsEntry("language", "zh-CN")
                        .containsEntry("chunkType", "knowledge")
                        .containsEntry("licenseId", "US-PD-ODPHP")
                        .containsEntry("rightsStatus", "approved")
                        .containsEntry("publisher", "Example Health Agency")
                        .containsEntry("canonicalUrl", "https://example.gov/fitness-guidance")
                        .containsKey("sourceChunkIndex")
                        .containsKey("corpusChunkId")
                        .containsKey("contentHash")
                        .containsKey("normalizedPath");
            });
        });
    }

    @Test
    void rejectsCorpusRowsWhoseLicenseIsNotAllowlisted() {
        FitnessDocumentLoader loader = new FitnessDocumentLoader(
                new PathMatchingResourcePatternResolver(),
                "classpath:markdown-corpus-test/invalid/*.md");

        assertThatThrownBy(loader::loadSources)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("license_id is missing or not allowlisted");
    }

    @Test
    void failsWhenCorpusIsMissing() {
        FitnessDocumentLoader loader = new FitnessDocumentLoader(
                new PathMatchingResourcePatternResolver(),
                "classpath:no-such-markdown-corpus/*.md");

        assertThatThrownBy(loader::loadSources)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No Markdown corpus matched");
    }

    @Test
    void loadsReviewedDataDirectoryCorpus() {
        FitnessDocumentLoader loader = new FitnessDocumentLoader(
                new PathMatchingResourcePatternResolver(),
                "file:Data/knowledge-base/**/*.md");

        List<FitnessDocumentLoader.KnowledgeSource> sources = loader.loadSources();

        assertThat(sources).hasSize(49);
        assertThat(sources).extracting(FitnessDocumentLoader.KnowledgeSource::sourceId)
                .contains("01-身体活动指南/hhs-pag-2018-second-edition.md");
        assertThat(sources).allSatisfy(source -> {
            assertThat(source.sourceId()).isNotBlank();
            assertThat(source.contentHash()).matches("[0-9a-f]{64}");
            assertThat(source.documents()).isNotEmpty();
            assertThat(source.documents()).allSatisfy(document -> {
                assertThat(document.getText()).isNotBlank();
                assertThat(document.getMetadata())
                        .containsEntry("language", "zh-CN")
                        .containsEntry("chunkType", "knowledge")
                        .containsEntry("rightsStatus", "approved")
                        .containsKey("sourceId")
                        .containsKey("sourceTitle")
                        .containsKey("sectionTitle")
                        .containsKey("sourceChunkIndex")
                        .containsKey("corpusChunkId")
                        .containsKey("contentHash")
                        .containsKey("licenseId")
                        .containsKey("publisher")
                        .containsKey("canonicalUrl");
            });
        });
    }

    @Test
    void chunkTextIsValidForRetrievalMetadata() {
        FitnessDocumentLoader loader = new FitnessDocumentLoader(
                new PathMatchingResourcePatternResolver(),
                "classpath:markdown-corpus-test/valid/*.md");

        Map<String, Object> metadata = loader.loadSources().getFirst().documents().getFirst().getMetadata();

        assertThat(metadata).containsKeys(
                "sourceId", "sourceTitle", "sectionTitle", "sourceChunkIndex",
                "corpusChunkId", "language", "contentHash", "normalizedPath", "chunkType",
                "licenseId", "licenseName", "licenseUrl", "rightsStatus",
                "publisher", "canonicalUrl", "jurisdiction", "authorityTier",
                "evidenceRole", "guidanceStatus", "sourceLanguage", "translationModel");
    }
}
