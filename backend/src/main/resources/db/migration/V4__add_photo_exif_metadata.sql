ALTER TABLE photo ADD COLUMN orientation INT;
ALTER TABLE photo ADD COLUMN focal_length_35mm INT;
ALTER TABLE photo ADD COLUMN exposure_bias DECIMAL(8, 3);
ALTER TABLE photo ADD COLUMN metering_mode VARCHAR(128);
ALTER TABLE photo ADD COLUMN white_balance VARCHAR(128);
ALTER TABLE photo ADD COLUMN flash_status VARCHAR(256);
ALTER TABLE photo ADD COLUMN metadata_status VARCHAR(32) NOT NULL DEFAULT 'PENDING';
ALTER TABLE photo ADD COLUMN metadata_error VARCHAR(1024);

CREATE INDEX idx_photo_user_metadata
    ON photo (user_id, metadata_status, is_delete);
