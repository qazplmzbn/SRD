CREATE TABLE IF NOT EXISTS personal_posts (
    id VARCHAR(36) PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    source VARCHAR(40) NOT NULL,
    published_at TIMESTAMP NULL,
    imported_at TIMESTAMP NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    CONSTRAINT uq_personal_posts_owner_hash UNIQUE (owner_id, content_hash)
);

CREATE TABLE IF NOT EXISTS care_doctors (doctor_id BIGINT PRIMARY KEY, certified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE IF NOT EXISTS care_appointments (
    id VARCHAR(36) PRIMARY KEY, patient_id BIGINT NOT NULL, doctor_id BIGINT NOT NULL,
    state VARCHAR(12) NOT NULL, shared BOOLEAN NOT NULL, scheduled_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, snapshot MEDIUMTEXT,
    INDEX care_patient(patient_id), INDEX care_doctor(doctor_id)
);
CREATE TABLE IF NOT EXISTS care_live_grants (
    appointment_id VARCHAR(36) PRIMARY KEY, source_key VARCHAR(36) NOT NULL
);
CREATE TABLE IF NOT EXISTS care_analysis_grants (appointment_id VARCHAR(36) PRIMARY KEY);
CREATE TABLE IF NOT EXISTS care_booking_consent (appointment_id VARCHAR(36) PRIMARY KEY);

CREATE TABLE IF NOT EXISTS care_appointment_reviews (
 appointment_id VARCHAR(36) PRIMARY KEY, reviewer_id BIGINT NOT NULL,
 decision VARCHAR(8) NOT NULL, opinion VARCHAR(1000) NOT NULL,
 reviewed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS admin_appointment_opinions (
 appointment_id VARCHAR(36) PRIMARY KEY, reviewer_id BIGINT NOT NULL,
 opinion VARCHAR(1000) NOT NULL, reviewed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS douyin_auth_state (
 owner_id BIGINT PRIMARY KEY, nonce VARCHAR(64) NOT NULL, expires BIGINT NOT NULL
);
CREATE TABLE IF NOT EXISTS douyin_connections (
 owner_id BIGINT PRIMARY KEY, open_id VARCHAR(128) NOT NULL, credentials TEXT NOT NULL,
 generation VARCHAR(36) NOT NULL, page_cursor BIGINT NOT NULL, next_run BIGINT NOT NULL,
 status VARCHAR(20) NOT NULL, last_success BIGINT NOT NULL, INDEX(next_run)
);

CREATE TABLE IF NOT EXISTS douyin_sync_coverage (
 owner_id BIGINT PRIMARY KEY, generation VARCHAR(36) NOT NULL,
 pages INT NOT NULL, item_count INT NOT NULL, oldest_create_time BIGINT NULL,
 newest_create_time BIGINT NULL, upstream_has_more BOOLEAN NOT NULL, finished BOOLEAN NOT NULL
);

CREATE TABLE IF NOT EXISTS social_post_details (
 post_id VARCHAR(36) PRIMARY KEY, external_id VARCHAR(255) NOT NULL, metadata JSON NOT NULL,
 FOREIGN KEY (post_id) REFERENCES personal_posts(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS social_post_metrics (
 post_id VARCHAR(36) NOT NULL, sampled_at BIGINT NOT NULL, metrics JSON NOT NULL,
 PRIMARY KEY (post_id, sampled_at), FOREIGN KEY (post_id) REFERENCES personal_posts(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS douyin_metric_checks (
 post_id VARCHAR(36) PRIMARY KEY, next_check BIGINT NOT NULL, result VARCHAR(20) NOT NULL,
 FOREIGN KEY (post_id) REFERENCES personal_posts(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS weibo_connections (
 owner_id BIGINT PRIMARY KEY, credentials TEXT NOT NULL, uid VARCHAR(128) NOT NULL,
 status VARCHAR(24) NOT NULL, next_run BIGINT NOT NULL, last_success BIGINT NOT NULL,
 page_no INT NOT NULL, page_cursor VARCHAR(128) NOT NULL, initial_done BOOLEAN NOT NULL,
 auth_expires BIGINT NOT NULL, poll_seconds INT NOT NULL, INDEX(next_run)
);
