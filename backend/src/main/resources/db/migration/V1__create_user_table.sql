CREATE TABLE IF NOT EXISTS `user` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(256),
    user_account VARCHAR(256) NOT NULL,
    avatar_url VARCHAR(1024),
    gender TINYINT,
    user_password VARCHAR(255) NOT NULL,
    phone VARCHAR(32),
    email VARCHAR(256),
    user_status INT NOT NULL DEFAULT 0,
    user_role INT NOT NULL DEFAULT 0,
    planet_code VARCHAR(32) NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_delete TINYINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_user_account UNIQUE (user_account),
    CONSTRAINT uk_planet_code UNIQUE (planet_code)
);
