CREATE TABLE IF NOT EXISTS doctor_applications (
    id VARCHAR(36) PRIMARY KEY,
    applicant_id BIGINT NOT NULL,
    real_name VARCHAR(80) NOT NULL,
    institution VARCHAR(200) NOT NULL,
    qualification VARCHAR(100) NOT NULL,
    credential_number VARCHAR(120) NOT NULL,
    notes VARCHAR(1000) NOT NULL,
    status VARCHAR(20) NOT NULL,
    submitted_at TIMESTAMP NOT NULL,
    reviewed_at TIMESTAMP NULL,
    reviewer_id BIGINT NULL,
    review_reason VARCHAR(1000) NULL,
    proof_type VARCHAR(50) NOT NULL,
    proof_content LONGBLOB NOT NULL,
    consent_version VARCHAR(40) NOT NULL
);
CREATE TABLE IF NOT EXISTS doctor_application_events (
    id VARCHAR(36) PRIMARY KEY,
    application_id VARCHAR(36) NOT NULL,
    actor_id BIGINT NOT NULL,
    action VARCHAR(30) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP NOT NULL
);
