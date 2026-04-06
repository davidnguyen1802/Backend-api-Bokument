-- SQL Script for Supabase (PostgreSQL)
-- Run this script in the Supabase SQL Editor.

-- 1. Add title, description, download_count, page_count, and processing_status to documents table
ALTER TABLE documents ADD COLUMN title VARCHAR(255);
ALTER TABLE documents ADD COLUMN description TEXT;
ALTER TABLE documents ADD COLUMN download_count BIGINT NOT NULL DEFAULT 0;
ALTER TABLE documents ADD COLUMN page_count INT NOT NULL DEFAULT 0;
ALTER TABLE documents ADD COLUMN processing_status VARCHAR(20) NOT NULL DEFAULT 'READY';

-- Enable unaccent extension for Vietnamese non-accented text search
CREATE EXTENSION IF NOT EXISTS unaccent;

-- Postgres unaccent() is STABLE, but GENERATED columns require IMMUTABLE functions.
-- We create an IMMUTABLE wrapper for it.
CREATE OR REPLACE FUNCTION f_unaccent(text)
  RETURNS text
  LANGUAGE sql IMMUTABLE STRICT AS
$func$
SELECT public.unaccent('unaccent', $1);
$func$;

-- Add Supabase Full text search generated column
ALTER TABLE documents 
ADD COLUMN fts tsvector GENERATED ALWAYS AS (
    to_tsvector('simple', f_unaccent(coalesce(title, '')) || ' ' || f_unaccent(coalesce(description, '')) || ' ' || f_unaccent(original_name))
) STORED;

-- Create GIN index for fast search
CREATE INDEX documents_fts_idx ON documents USING GIN (fts);

-- 2. Create tags table for PBI-010
CREATE TABLE tags (
    id SERIAL PRIMARY KEY,
    name VARCHAR(50) UNIQUE NOT NULL
);

-- Insert some default tags if needed
INSERT INTO tags (name) VALUES ('Math'), ('Physics'), ('Computer Science'), ('Literature'), ('History') ON CONFLICT (name) DO NOTHING;

-- 3. Create document_tags bridge table
CREATE TABLE document_tags (
    document_id UUID REFERENCES documents(id) ON DELETE CASCADE,
    tag_id INT REFERENCES tags(id) ON DELETE CASCADE,
    PRIMARY KEY (document_id, tag_id)
);

-- 4. Create document_downloads table for quota tracking (PBI-16: uploads >= downloads)
-- Each row records one download event by a user.
-- Race condition prevention: use SERIALIZABLE transaction in application layer.
CREATE TABLE document_downloads (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       INT         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    document_id   UUID        NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    downloaded_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Indexes for fast quota queries
CREATE INDEX doc_downloads_user_idx ON document_downloads(user_id);
CREATE INDEX doc_downloads_doc_idx  ON document_downloads(document_id);