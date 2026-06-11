-- V2__create_vector_store.sql
-- Spring AI PgVectorStore 自动建表在某些环境下不可靠，通过 Flyway 显式创建

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS vector_store (
    id        UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    content   TEXT,
    metadata  JSONB,
    embedding vector(1024)
);

CREATE INDEX IF NOT EXISTS vector_store_hnsw_idx
    ON vector_store USING hnsw (embedding vector_cosine_ops);
