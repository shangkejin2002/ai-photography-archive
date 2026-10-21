ALTER TABLE photo ADD COLUMN analysis_model VARCHAR(128);
ALTER TABLE photo ADD COLUMN analysis_version VARCHAR(64);
ALTER TABLE photo ADD COLUMN analysis_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE photo ADD COLUMN analyzed_at TIMESTAMP;

CREATE INDEX idx_photo_user_analysis
    ON photo (user_id, analysis_status, is_delete);
