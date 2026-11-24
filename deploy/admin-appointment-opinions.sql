CREATE TABLE IF NOT EXISTS admin_appointment_opinions (
    appointment_id VARCHAR(36) NOT NULL PRIMARY KEY,
    reviewer_id BIGINT NOT NULL,
    opinion VARCHAR(1000) NOT NULL,
    reviewed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (appointment_id) REFERENCES care_appointments(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;