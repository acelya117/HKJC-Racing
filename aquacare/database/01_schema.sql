-- AquaCare database schema
-- Target: MySQL 8.0+
-- Re-running this file rebuilds only the AquaCare objects listed below.

CREATE DATABASE IF NOT EXISTS aquacare
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

USE aquacare;

DROP PROCEDURE IF EXISTS complete_maintenance_task;
DROP VIEW IF EXISTS v_maintenance_history;
DROP VIEW IF EXISTS v_due_tasks;

DROP TABLE IF EXISTS task_completions;
DROP TABLE IF EXISTS maintenance_tasks;
DROP TABLE IF EXISTS tanks;
DROP TABLE IF EXISTS users;

CREATE TABLE users (
  user_id INT UNSIGNED NOT NULL AUTO_INCREMENT,
  username VARCHAR(50) NOT NULL,
  email VARCHAR(120) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id),
  CONSTRAINT uq_users_username UNIQUE (username),
  CONSTRAINT uq_users_email UNIQUE (email)
) ENGINE = InnoDB;

CREATE TABLE tanks (
  tank_id INT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id INT UNSIGNED NOT NULL,
  tank_name VARCHAR(80) NOT NULL,
  volume_liters DECIMAL(8,2) UNSIGNED NOT NULL,
  water_type ENUM('Freshwater', 'Saltwater', 'Brackish') NOT NULL,
  image_url VARCHAR(500) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
    ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (tank_id),
  CONSTRAINT uq_tanks_user_name UNIQUE (user_id, tank_name),
  CONSTRAINT chk_tanks_positive_volume CHECK (volume_liters > 0),
  CONSTRAINT fk_tanks_user
    FOREIGN KEY (user_id) REFERENCES users (user_id)
    ON UPDATE CASCADE
    ON DELETE CASCADE,
  INDEX idx_tanks_user (user_id)
) ENGINE = InnoDB;

CREATE TABLE maintenance_tasks (
  task_id INT UNSIGNED NOT NULL AUTO_INCREMENT,
  tank_id INT UNSIGNED NOT NULL,
  task_name VARCHAR(100) NOT NULL,
  description VARCHAR(500) NULL,
  frequency_days SMALLINT UNSIGNED NOT NULL DEFAULT 1,
  scheduled_time TIME NULL,
  next_due_date DATE NOT NULL,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
    ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (task_id),
  CONSTRAINT chk_tasks_frequency_days
    CHECK (frequency_days BETWEEN 1 AND 365),
  CONSTRAINT fk_tasks_tank
    FOREIGN KEY (tank_id) REFERENCES tanks (tank_id)
    ON UPDATE CASCADE
    ON DELETE CASCADE,
  INDEX idx_tasks_tank (tank_id),
  INDEX idx_tasks_due (is_active, next_due_date)
) ENGINE = InnoDB;

CREATE TABLE task_completions (
  completion_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  task_id INT UNSIGNED NOT NULL,
  completed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  notes VARCHAR(500) NULL,
  PRIMARY KEY (completion_id),
  CONSTRAINT fk_completions_task
    FOREIGN KEY (task_id) REFERENCES maintenance_tasks (task_id)
    ON UPDATE CASCADE
    ON DELETE CASCADE,
  INDEX idx_completions_task_time (task_id, completed_at)
) ENGINE = InnoDB;

CREATE VIEW v_due_tasks AS
SELECT
  mt.task_id,
  mt.task_name,
  mt.description,
  mt.frequency_days,
  mt.scheduled_time,
  mt.next_due_date,
  CASE
    WHEN mt.next_due_date < CURRENT_DATE THEN 'Overdue'
    ELSE 'Due today'
  END AS due_status,
  t.tank_id,
  t.tank_name,
  t.water_type,
  u.user_id,
  u.username
FROM maintenance_tasks AS mt
JOIN tanks AS t
  ON t.tank_id = mt.tank_id
JOIN users AS u
  ON u.user_id = t.user_id
WHERE mt.is_active = TRUE
  AND mt.next_due_date <= CURRENT_DATE;

CREATE VIEW v_maintenance_history AS
SELECT
  tc.completion_id,
  tc.completed_at,
  tc.notes,
  mt.task_id,
  mt.task_name,
  mt.frequency_days,
  t.tank_id,
  t.tank_name,
  u.user_id,
  u.username
FROM task_completions AS tc
JOIN maintenance_tasks AS mt
  ON mt.task_id = tc.task_id
JOIN tanks AS t
  ON t.tank_id = mt.tank_id
JOIN users AS u
  ON u.user_id = t.user_id;

DELIMITER $$

CREATE PROCEDURE complete_maintenance_task (
  IN p_task_id INT UNSIGNED,
  IN p_notes VARCHAR(500)
)
BEGIN
  DECLARE v_frequency_days SMALLINT UNSIGNED DEFAULT NULL;
  DECLARE v_next_due_date DATE DEFAULT NULL;

  START TRANSACTION;

  SELECT frequency_days, next_due_date
    INTO v_frequency_days, v_next_due_date
  FROM maintenance_tasks
  WHERE task_id = p_task_id
    AND is_active = TRUE
  FOR UPDATE;

  IF v_frequency_days IS NULL THEN
    ROLLBACK;
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'Active maintenance task not found';
  ELSE
    INSERT INTO task_completions (task_id, completed_at, notes)
    VALUES (p_task_id, CURRENT_TIMESTAMP, NULLIF(TRIM(p_notes), ''));

    UPDATE maintenance_tasks
    SET next_due_date = DATE_ADD(
      GREATEST(v_next_due_date, CURRENT_DATE),
      INTERVAL v_frequency_days DAY
    )
    WHERE task_id = p_task_id;

    COMMIT;
  END IF;
END$$

DELIMITER ;

