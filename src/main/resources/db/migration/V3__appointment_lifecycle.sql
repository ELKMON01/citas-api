ALTER TABLE appointments ADD COLUMN rejection_reason VARCHAR(500) NULL;

CREATE TABLE rescheduling_statuses (
  id SMALLINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(20) NOT NULL UNIQUE,
  name VARCHAR(80) NOT NULL,
  is_terminal BOOLEAN NOT NULL DEFAULT FALSE
) ENGINE=InnoDB;
INSERT INTO rescheduling_statuses(code,name,is_terminal) VALUES
 ('PENDING','Pendiente',FALSE),('APPROVED','Aprobada',TRUE),('REJECTED','Rechazada',TRUE);

CREATE TABLE rescheduling_requests (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  appointment_id BIGINT UNSIGNED NOT NULL,
  status_id SMALLINT UNSIGNED NOT NULL,
  requested_start_at DATETIME NOT NULL,
  requested_end_at DATETIME NOT NULL,
  reason VARCHAR(500) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  decided_by_user_id BIGINT UNSIGNED NULL,
  decision_source VARCHAR(20) NULL,
  decided_at TIMESTAMP NULL,
  FOREIGN KEY(appointment_id) REFERENCES appointments(id),
  FOREIGN KEY(status_id) REFERENCES rescheduling_statuses(id),
  FOREIGN KEY(decided_by_user_id) REFERENCES users(id),
  INDEX ix_reschedule_status(status_id,created_at),
  INDEX ix_reschedule_appointment(appointment_id,created_at)
) ENGINE=InnoDB;

CREATE TABLE rescheduling_slots (
  rescheduling_request_id BIGINT UNSIGNED NOT NULL,
  professional_slot_id BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY(rescheduling_request_id,professional_slot_id),
  UNIQUE KEY uq_rescheduling_slot(professional_slot_id),
  FOREIGN KEY(rescheduling_request_id) REFERENCES rescheduling_requests(id),
  FOREIGN KEY(professional_slot_id) REFERENCES professional_slots(id),
  INDEX ix_rescheduling_slot_request(rescheduling_request_id)
) ENGINE=InnoDB;
