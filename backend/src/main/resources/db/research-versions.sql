CREATE TABLE IF NOT EXISTS research_versions (
 id VARCHAR(36) PRIMARY KEY, owner_id BIGINT NOT NULL, name VARCHAR(100) NOT NULL,
 version_name VARCHAR(40) NOT NULL, source VARCHAR(500) NOT NULL, usage_scope VARCHAR(1000) NOT NULL,
 checksum VARCHAR(64) NOT NULL, sample_count INT NOT NULL, samples_json LONGTEXT NOT NULL,
 created_at TIMESTAMP NOT NULL, UNIQUE(owner_id,name,version_name)
);
CREATE TABLE IF NOT EXISTS research_version_grants (
 version_id VARCHAR(36) NOT NULL, user_id BIGINT NOT NULL, PRIMARY KEY(version_id,user_id)
);
CREATE TABLE IF NOT EXISTS research_version_events (
 id VARCHAR(36) PRIMARY KEY, version_id VARCHAR(36) NOT NULL, actor_id BIGINT NOT NULL,
 target_id BIGINT NULL, action VARCHAR(20) NOT NULL, created_at TIMESTAMP NOT NULL
);
