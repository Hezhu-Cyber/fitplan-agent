package com.fitplan.rag.knowledge;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class Bm25LexicalIndexerTest {

    private static final List<Bm25LexicalIndexer.IndexedChunk> CHUNKS = List.of(
            chunk("c1", "有氧运动每周至少150分钟中等强度，可显著降低心血管疾病风险。", "aerobic.md", 0),
            chunk("c2", "力量训练应遵循渐进超负荷原则，逐步增加重量与次数。", "strength.md", 0),
            chunk("c3", "跑步、游泳、骑行等有氧运动能改善心肺耐力，降低静息心率。", "aerobic.md", 1),
            chunk("c4", "充足的睡眠有助于运动后恢复和肌肉生长。", "recovery.md", 0));

    @Test
    void returnsChineseLexicalMatchesThatTrigramSimilarityMissed() {
        Bm25LexicalIndexer indexer = new Bm25LexicalIndexer(null, new SimpleMeterRegistry());
        indexer.rebuildFrom(CHUNKS);

        List<RagCandidate> results = indexer.search("有氧运动", 4);

        List<String> ids = results.stream().map(RagCandidate::chunkId).toList();
        assertThat(ids).contains("c1", "c3");
        // 含完整“有氧运动”的块必须排在只含“运动”一词的弱匹配块之前
        assertThat(ids.indexOf("c1")).isLessThan(ids.indexOf("c4"));
        assertThat(ids.indexOf("c3")).isLessThan(ids.indexOf("c4"));
    }

    @Test
    void ranksChunksWithMoreRelevantTermsHigher() {
        Bm25LexicalIndexer indexer = new Bm25LexicalIndexer(null, new SimpleMeterRegistry());
        indexer.rebuildFrom(CHUNKS);

        List<RagCandidate> results = indexer.search("有氧运动 心肺耐力", 4);

        assertThat(results).isNotEmpty();
        assertThat(results.getFirst().chunkId()).isEqualTo("c3");
    }

    @Test
    void returnsEmptyWhenIndexIsNotBuilt() {
        Bm25LexicalIndexer indexer = new Bm25LexicalIndexer(null, new SimpleMeterRegistry());

        assertThat(indexer.search("有氧运动", 4)).isEmpty();
        assertThat(indexer.isReady()).isFalse();
    }

    @Test
    void rebuildSwapsInNewChunkSet() {
        Bm25LexicalIndexer indexer = new Bm25LexicalIndexer(null, new SimpleMeterRegistry());
        indexer.rebuildFrom(CHUNKS);
        indexer.rebuildFrom(List.of(chunk("c9", "瑜伽和拉伸可以提升柔韧性并缓解肌肉紧张。", "flex.md", 0)));

        List<RagCandidate> results = indexer.search("瑜伽", 4);

        assertThat(results).extracting(RagCandidate::chunkId).containsExactly("c9");
        assertThat(indexer.isReady()).isTrue();
    }

    private static Bm25LexicalIndexer.IndexedChunk chunk(
            String id, String content, String filename, int chunkIndex) {
        return new Bm25LexicalIndexer.IndexedChunk(id, content, filename, chunkIndex);
    }
}
