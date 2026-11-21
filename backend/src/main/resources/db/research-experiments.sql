CREATE TABLE IF NOT EXISTS research_experiments (
 id VARCHAR(36) PRIMARY KEY,
 owner_id BIGINT NOT NULL,
 version_id VARCHAR(36) NOT NULL,
 version_name VARCHAR(160) NOT NULL,
 dataset_checksum VARCHAR(64) NOT NULL,
 model_id BIGINT NOT NULL,
 model_name VARCHAR(200) NOT NULL,
 model_snapshot TEXT NOT NULL,
 config_hash VARCHAR(64) NOT NULL,
 prompt_version VARCHAR(40) NOT NULL,
 status VARCHAR(30) NOT NULL,
 total INT NOT NULL,
 completed INT NOT NULL DEFAULT 0,
 message VARCHAR(300),
 created_at TIMESTAMP NOT NULL,
 finished_at TIMESTAMP NULL
);
CREATE TABLE IF NOT EXISTS research_experiment_results (
 experiment_id VARCHAR(36) NOT NULL,
 sample_index INT NOT NULL,
 expected_label INT NOT NULL,
 predicted_label INT NULL,
 response_text TEXT,
 error_message VARCHAR(300),
 elapsed_ms BIGINT NOT NULL,
 PRIMARY KEY(experiment_id,sample_index)
);
