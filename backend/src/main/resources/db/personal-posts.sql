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
