ALTER TABLE interpretation ADD COLUMN base_usage_hash char(64);
ALTER TABLE interpretation ADD COLUMN cache_key char(64);
ALTER TABLE interpretation ADD COLUMN result_metadata jsonb;
ALTER TABLE interpretation ADD COLUMN failure_code varchar(80);
ALTER TABLE interpretation ADD COLUMN transient_retries integer NOT NULL DEFAULT 0;
ALTER TABLE interpretation ADD COLUMN repair_attempts integer NOT NULL DEFAULT 0;
CREATE INDEX interpretation_cache ON interpretation(workspace_id,cache_key,created_at DESC) WHERE status IN ('READY','NEEDS_CLARIFICATION','UNSUPPORTED');
