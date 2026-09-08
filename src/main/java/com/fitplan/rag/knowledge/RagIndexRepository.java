package com.fitplan.rag.knowledge;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class RagIndexRepository {

    private final JdbcTemplate jdbcTemplate;

    public RagIndexRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, SourceManifest> findManifests() {
        return jdbcTemplate.query(
                        """
                        SELECT source_id, filename, content_hash, chunk_count,
                               embedding_model, embedding_dimensions, chunk_schema_version
                        FROM rag_source_manifest
                        """,
                        (rs, rowNum) -> new SourceManifest(
                                rs.getString("source_id"),
                                rs.getString("filename"),
                                rs.getString("content_hash"),
                                rs.getInt("chunk_count"),
                                rs.getString("embedding_model"),
                                rs.getInt("embedding_dimensions"),
                                rs.getString("chunk_schema_version")))
                .stream()
                .collect(java.util.stream.Collectors.toMap(SourceManifest::sourceId, value -> value));
    }

    public void saveManifest(SourceManifest manifest) {
        jdbcTemplate.update(
                """
                INSERT INTO rag_source_manifest(
                    source_id, filename, content_hash, chunk_count,
                    embedding_model, embedding_dimensions, chunk_schema_version, indexed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, now())
                ON CONFLICT(source_id) DO UPDATE SET
                    filename = EXCLUDED.filename,
                    content_hash = EXCLUDED.content_hash,
                    chunk_count = EXCLUDED.chunk_count,
                    embedding_model = EXCLUDED.embedding_model,
                    embedding_dimensions = EXCLUDED.embedding_dimensions,
                    chunk_schema_version = EXCLUDED.chunk_schema_version,
                    indexed_at = now()
                """,
                manifest.sourceId(), manifest.filename(), manifest.contentHash(), manifest.chunkCount(),
                manifest.embeddingModel(), manifest.embeddingDimensions(), manifest.chunkSchemaVersion());
    }

    public void deleteManifest(String sourceId) {
        jdbcTemplate.update("DELETE FROM rag_source_manifest WHERE source_id = ?", sourceId);
    }

    public List<String> findVectorIds(String sourceId) {
        return jdbcTemplate.queryForList(
                "SELECT id::text FROM vector_store "
                        + "WHERE COALESCE(metadata->>'source_id', metadata->>'sourceId') = ?",
                String.class,
                sourceId);
    }

    public boolean tryAcquireLock(String lockName, String ownerId, int leaseSeconds) {
        int changed = jdbcTemplate.update(
                """
                INSERT INTO rag_index_lock(lock_name, owner_id, locked_until)
                VALUES (?, ?, now() + (? * interval '1 second'))
                ON CONFLICT(lock_name) DO UPDATE SET
                    owner_id = EXCLUDED.owner_id,
                    locked_until = EXCLUDED.locked_until
                WHERE rag_index_lock.locked_until < now()
                   OR rag_index_lock.owner_id = EXCLUDED.owner_id
                """,
                lockName, ownerId, leaseSeconds);
        return changed == 1;
    }

    public void releaseLock(String lockName, String ownerId) {
        jdbcTemplate.update(
                "DELETE FROM rag_index_lock WHERE lock_name = ? AND owner_id = ?",
                lockName,
                ownerId);
    }

    public UUID createJob(String ownerId) {
        UUID jobId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO rag_index_job(job_id, owner_id, status) VALUES (?, ?, 'RUNNING')",
                jobId,
                ownerId);
        return jobId;
    }

    public void completeJob(UUID jobId, IndexStats stats) {
        jdbcTemplate.update(
                """
                UPDATE rag_index_job SET
                    status = 'SUCCESS', discovered_sources = ?, changed_sources = ?,
                    indexed_chunks = ?, skipped_sources = ?, deleted_sources = ?, finished_at = now()
                WHERE job_id = ?
                """,
                stats.discoveredSources(), stats.changedSources(), stats.indexedChunks(),
                stats.skippedSources(), stats.deletedSources(), jobId);
    }

    public void failJob(UUID jobId, String message) {
        jdbcTemplate.update(
                """
                UPDATE rag_index_job SET status = 'FAILED', error_message = ?, finished_at = now()
                WHERE job_id = ?
                """,
                abbreviate(message, 4000),
                jobId);
    }

    public boolean hasSuccessfulIndex() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM rag_source_manifest",
                Integer.class);
        return count != null && count > 0;
    }

    private static String abbreviate(String value, int maxLength) {
        if (value == null) {
            return "unknown error";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    public record SourceManifest(
            String sourceId,
            String filename,
            String contentHash,
            int chunkCount,
            String embeddingModel,
            int embeddingDimensions,
            String chunkSchemaVersion) {
    }

    public record IndexStats(
            int discoveredSources,
            int changedSources,
            int indexedChunks,
            int skippedSources,
            int deletedSources) {
    }
}
