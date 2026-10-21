ALTER TABLE portfolio ADD COLUMN share_enabled TINYINT NOT NULL DEFAULT 0;
ALTER TABLE portfolio ADD COLUMN share_token VARCHAR(64);
ALTER TABLE portfolio ADD COLUMN shared_at TIMESTAMP;

CREATE UNIQUE INDEX uk_portfolio_share_token ON portfolio (share_token);
