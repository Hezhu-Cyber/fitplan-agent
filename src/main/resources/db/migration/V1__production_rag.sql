CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS pg_trgm;

--   保存每一个知识 Chunk 的文本、向量、元数据
CREATE TABLE IF NOT EXISTS vector_store (
    id uuid DEFAULT uuid_generate_v4() PRIMARY KEY,
    content text NOT NULL,
    metadata json NOT NULL DEFAULT '{}'::json,
    embedding vector(1024) NOT NULL
);

CREATE INDEX IF NOT EXISTS fitplan_vector_hnsw_idx
    ON vector_store USING hnsw (embedding vector_cosine_ops);

CREATE INDEX IF NOT EXISTS fitplan_vector_content_trgm_idx
    ON vector_store USING gin (content gin_trgm_ops);

CREATE INDEX IF NOT EXISTS fitplan_vector_source_idx
    ON vector_store ((metadata->>'sourceId'));

CREATE INDEX IF NOT EXISTS fitplan_vector_source_snake_idx
    ON vector_store ((metadata->>'source_id'));

-- 保存每个 Markdown 文件当前已索引的版本信息
CREATE TABLE IF NOT EXISTS rag_source_manifest (
    source_id varchar(255) PRIMARY KEY,
    filename varchar(255) NOT NULL,
    content_hash char(64) NOT NULL,
    chunk_count integer NOT NULL,
    embedding_model varchar(128) NOT NULL,
    embedding_dimensions integer NOT NULL,
    chunk_schema_version varchar(64) NOT NULL,
    indexed_at timestamptz NOT NULL DEFAULT now()
);

-- rag_index_lock→ 防止多个后端实例同时构建索引

CREATE TABLE IF NOT EXISTS rag_index_lock (
    lock_name varchar(128) PRIMARY KEY,
    owner_id varchar(128) NOT NULL,
    locked_until timestamptz NOT NULL
);
-- rag_index_job→ 记录每一次索引任务的状态和结果
CREATE TABLE IF NOT EXISTS rag_index_job (
    job_id uuid PRIMARY KEY,
    owner_id varchar(128) NOT NULL,
    status varchar(32) NOT NULL,
    discovered_sources integer NOT NULL DEFAULT 0,
    changed_sources integer NOT NULL DEFAULT 0,
    indexed_chunks integer NOT NULL DEFAULT 0,
    skipped_sources integer NOT NULL DEFAULT 0,
    deleted_sources integer NOT NULL DEFAULT 0,
    error_message text,
    started_at timestamptz NOT NULL DEFAULT now(),
    finished_at timestamptz
);
