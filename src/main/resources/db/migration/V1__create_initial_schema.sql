-- V1__create_initial_schema.sql
-- 注意：vector_store 表由 Spring AI pgvector-starter 自动管理，无需在此创建

-- 知识条目表（纯业务，不含 embedding）
CREATE TABLE IF NOT EXISTS knowledge_items (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    raw_content     TEXT NOT NULL,                  -- 原始输入文本
    clean_content   TEXT NOT NULL,                  -- 清洗后文本（用于 Embedding）
    content_type    VARCHAR(10) NOT NULL,           -- TEXT / AUDIO / IMAGE
    source_type     VARCHAR(20),                    -- MANUAL / VOICE / UPLOAD / WEB_CLIP
    auto_category   VARCHAR(30),                    -- AI 自动分类
    user_category   VARCHAR(30),                    -- 用户修正分类
    summary         VARCHAR(200),                   -- AI 生成的一句话摘要
    metadata_json   TEXT,                           -- JSON 扩展字段（duration, imageUrl...）
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE knowledge_items IS '知识条目 — 文字/语音/图片三种模态的统一业务表';
COMMENT ON COLUMN knowledge_items.raw_content IS '原始输入文本';
COMMENT ON COLUMN knowledge_items.clean_content IS 'LLM 标准化清洗后的内容（用于 Embedding 和 Lucene 索引）';
COMMENT ON COLUMN knowledge_items.content_type IS '输入模态：TEXT（文字）、AUDIO（语音）、IMAGE（图片）';
COMMENT ON COLUMN knowledge_items.source_type IS '来源：MANUAL（手动输入）、VOICE（语音）、UPLOAD（上传）、WEB_CLIP（网页剪藏）';

-- 标签表
CREATE TABLE IF NOT EXISTS tags (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name         VARCHAR(50) NOT NULL UNIQUE,
    usage_count  INT DEFAULT 1,
    is_new       BOOLEAN DEFAULT TRUE,
    created_at   TIMESTAMP NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE tags IS '标签表';

-- 条目-标签关联
CREATE TABLE IF NOT EXISTS item_tags (
    item_id  UUID NOT NULL REFERENCES knowledge_items(id) ON DELETE CASCADE,
    tag_id   UUID NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
    source   VARCHAR(10) DEFAULT 'AI',              -- AI / USER
    PRIMARY KEY (item_id, tag_id)
);

COMMENT ON TABLE item_tags IS '知识条目与标签的多对多关联表';

-- 分类反馈表（人在回路）
CREATE TABLE IF NOT EXISTS classification_feedback (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    item_id             UUID NOT NULL REFERENCES knowledge_items(id) ON DELETE CASCADE,
    original_category   VARCHAR(30),
    corrected_category  VARCHAR(30),
    original_tags       TEXT,                        -- JSON 数组字符串
    corrected_tags      TEXT,
    feedback_text       TEXT,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE classification_feedback IS '用户在回路分类修正记录 — 积累后用于 few-shot 优化';
