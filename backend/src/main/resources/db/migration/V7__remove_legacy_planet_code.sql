-- The legacy User Center identifier is not part of Photography Archive.
-- User ownership and authorization use the primary user id instead.
ALTER TABLE `user` DROP COLUMN planet_code;
