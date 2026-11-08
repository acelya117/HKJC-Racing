-- AquaCare representative SQL
-- The Java backend should use prepared statements for all values supplied by a
-- user. The MySQL variables below make the examples runnable in a SQL client.

USE aquacare;

-- 1. READ: show every tank belonging to one user.
SET @user_id = 1;

SELECT
  tank_id,
  tank_name,
  volume_liters,
  water_type,
  image_url
FROM tanks
WHERE user_id = @user_id
ORDER BY tank_name;

-- 2. READ: show due and overdue work for one user.
SELECT
  task_id,
  tank_name,
  task_name,
  next_due_date,
  scheduled_time,
  due_status
FROM v_due_tasks
WHERE user_id = @user_id
ORDER BY next_due_date, scheduled_time, tank_name;

-- 3. CREATE, READ, UPDATE, DELETE: reversible tank CRUD demonstration.
-- ROLLBACK restores the seed data when the section finishes.
START TRANSACTION;

INSERT INTO tanks (
  user_id,
  tank_name,
  volume_liters,
  water_type,
  image_url
) VALUES (
  @user_id,
  'Demo Tank',
  60.00,
  'Freshwater',
  '/images/demo-tank.jpg'
);

SET @demo_tank_id = LAST_INSERT_ID();

SELECT *
FROM tanks
WHERE tank_id = @demo_tank_id;

UPDATE tanks
SET tank_name = 'Updated Demo Tank',
    volume_liters = 75.00
WHERE tank_id = @demo_tank_id;

SELECT *
FROM tanks
WHERE tank_id = @demo_tank_id;

DELETE FROM tanks
WHERE tank_id = @demo_tank_id;

SELECT COUNT(*) AS rows_after_delete
FROM tanks
WHERE tank_id = @demo_tank_id;

ROLLBACK;

-- 4. CREATE, READ, UPDATE, DELETE: reversible task CRUD demonstration.
START TRANSACTION;

INSERT INTO maintenance_tasks (
  tank_id,
  task_name,
  description,
  frequency_days,
  scheduled_time,
  next_due_date
) VALUES (
  1,
  'Demo glass cleaning',
  'Clean the inside glass with an algae scraper.',
  7,
  '16:00:00',
  CURRENT_DATE
);

SET @demo_task_id = LAST_INSERT_ID();

SELECT *
FROM maintenance_tasks
WHERE task_id = @demo_task_id;

UPDATE maintenance_tasks
SET frequency_days = 14,
    scheduled_time = '17:00:00'
WHERE task_id = @demo_task_id;

SELECT *
FROM maintenance_tasks
WHERE task_id = @demo_task_id;

DELETE FROM maintenance_tasks
WHERE task_id = @demo_task_id;

SELECT COUNT(*) AS rows_after_delete
FROM maintenance_tasks
WHERE task_id = @demo_task_id;

ROLLBACK;

-- 5. COMPLETE A TASK: call this separately because it intentionally changes
-- the database. It inserts a history record and advances next_due_date in one
-- transaction.
-- CALL complete_maintenance_task(1, 'Changed 25% of the water');

-- 6. HISTORY: show the completion history of one tank.
SET @tank_id = 1;

SELECT
  completion_id,
  task_name,
  completed_at,
  notes
FROM v_maintenance_history
WHERE tank_id = @tank_id
ORDER BY completed_at DESC;

-- 7. SUMMARY: count completions by task during the last 30 days.
SELECT
  tank_name,
  task_name,
  COUNT(*) AS completion_count,
  MAX(completed_at) AS most_recent_completion
FROM v_maintenance_history
WHERE completed_at >= CURRENT_TIMESTAMP - INTERVAL 30 DAY
GROUP BY tank_id, tank_name, task_id, task_name
ORDER BY tank_name, task_name;

