CREATE TABLE IF NOT EXISTS photo (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    original_filename VARCHAR(512) NOT NULL,
    original_url VARCHAR(1024) NOT NULL,
    thumbnail_url VARCHAR(1024) NOT NULL,
    original_storage_key VARCHAR(1024) NOT NULL,
    thumbnail_storage_key VARCHAR(1024) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    file_size BIGINT NOT NULL,
    width INT NOT NULL,
    height INT NOT NULL,
    favorite TINYINT NOT NULL DEFAULT 0,
    camera_make VARCHAR(128),
    camera_model VARCHAR(128),
    lens_model VARCHAR(256),
    focal_length DECIMAL(10, 3),
    aperture DECIMAL(8, 3),
    shutter_speed VARCHAR(64),
    iso_value INT,
    taken_at TIMESTAMP,
    gps_latitude DECIMAL(10, 7),
    gps_longitude DECIMAL(10, 7),
    location_name VARCHAR(512),
    ai_description TEXT,
    analysis_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    analysis_error VARCHAR(1024),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_delete TINYINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_photo_user FOREIGN KEY (user_id) REFERENCES `user` (id)
);

CREATE TABLE IF NOT EXISTS tag (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    tag_type VARCHAR(64) NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_tag_user_type_name UNIQUE (user_id, tag_type, name),
    CONSTRAINT fk_tag_user FOREIGN KEY (user_id) REFERENCES `user` (id)
);

CREATE TABLE IF NOT EXISTS photo_tag (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    photo_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    confidence DECIMAL(6, 5),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_photo_tag UNIQUE (photo_id, tag_id),
    CONSTRAINT fk_photo_tag_user FOREIGN KEY (user_id) REFERENCES `user` (id),
    CONSTRAINT fk_photo_tag_photo FOREIGN KEY (photo_id) REFERENCES photo (id),
    CONSTRAINT fk_photo_tag_tag FOREIGN KEY (tag_id) REFERENCES tag (id)
);

CREATE TABLE IF NOT EXISTS portfolio (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(256) NOT NULL,
    subtitle VARCHAR(512),
    cover_photo_id BIGINT,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_delete TINYINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_portfolio_user FOREIGN KEY (user_id) REFERENCES `user` (id),
    CONSTRAINT fk_portfolio_cover FOREIGN KEY (cover_photo_id) REFERENCES photo (id)
);

CREATE TABLE IF NOT EXISTS portfolio_photo (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    portfolio_id BIGINT NOT NULL,
    photo_id BIGINT NOT NULL,
    sort_order INT NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_portfolio_photo UNIQUE (portfolio_id, photo_id),
    CONSTRAINT uk_portfolio_sort UNIQUE (portfolio_id, sort_order),
    CONSTRAINT fk_portfolio_photo_user FOREIGN KEY (user_id) REFERENCES `user` (id),
    CONSTRAINT fk_portfolio_photo_portfolio FOREIGN KEY (portfolio_id) REFERENCES portfolio (id),
    CONSTRAINT fk_portfolio_photo_photo FOREIGN KEY (photo_id) REFERENCES photo (id)
);
