CREATE INDEX idx_user_username ON `user` (username);
CREATE INDEX idx_photo_user_created ON photo (user_id, is_delete, create_time);
CREATE INDEX idx_photo_user_favorite ON photo (user_id, favorite, is_delete);
CREATE INDEX idx_photo_tag_user_photo ON photo_tag (user_id, photo_id);
CREATE INDEX idx_portfolio_user_created ON portfolio (user_id, is_delete, create_time);
CREATE INDEX idx_portfolio_photo_order ON portfolio_photo (user_id, portfolio_id, sort_order);
