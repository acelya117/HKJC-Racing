-- AquaCare demonstration data
-- Run after 01_schema.sql.

USE aquacare;

INSERT INTO users (user_id, username, email) VALUES
  (1, 'alice_wong', 'alice.wong@example.com'),
  (2, 'ben_chan', 'ben.chan@example.com');

INSERT INTO tanks (
  tank_id,
  user_id,
  tank_name,
  volume_liters,
  water_type,
  image_url
) VALUES
  (1, 1, 'Living Room Reef', 180.00, 'Saltwater',
    '/images/living-room-reef.jpg'),
  (2, 1, 'Planted Nano', 45.00, 'Freshwater',
    '/images/planted-nano.jpg'),
  (3, 2, 'Office Community Tank', 90.00, 'Freshwater',
    '/images/office-community.jpg');

INSERT INTO maintenance_tasks (
  task_id,
  tank_id,
  task_name,
  description,
  frequency_days,
  scheduled_time,
  next_due_date,
  is_active
) VALUES
  (1, 1, 'Water change', 'Replace 25% of the tank water.',
    7, '10:00:00', CURRENT_DATE, TRUE),
  (2, 1, 'Feed fish', 'Feed one measured portion.',
    1, '08:00:00', CURRENT_DATE, TRUE),
  (3, 1, 'Clean protein skimmer', 'Empty and rinse the collection cup.',
    7, '18:00:00', CURRENT_DATE - INTERVAL 1 DAY, TRUE),
  (4, 2, 'Test water parameters', 'Check pH, ammonia, nitrite, and nitrate.',
    7, '19:00:00', CURRENT_DATE + INTERVAL 1 DAY, TRUE),
  (5, 2, 'Trim aquatic plants', 'Trim overgrown stems and remove dead leaves.',
    14, '15:00:00', CURRENT_DATE + INTERVAL 3 DAY, TRUE),
  (6, 3, 'Inspect filter', 'Check flow rate and rinse the filter sponge.',
    14, '17:30:00', CURRENT_DATE + INTERVAL 5 DAY, FALSE);

INSERT INTO task_completions (
  completion_id,
  task_id,
  completed_at,
  notes
) VALUES
  (1, 1, CURRENT_TIMESTAMP - INTERVAL 21 DAY,
    'Changed 25% of the water.'),
  (2, 1, CURRENT_TIMESTAMP - INTERVAL 14 DAY,
    'Changed 25% of the water and cleaned the glass.'),
  (3, 1, CURRENT_TIMESTAMP - INTERVAL 7 DAY,
    'Changed 30% after the nitrate reading increased.'),
  (4, 2, CURRENT_TIMESTAMP - INTERVAL 2 DAY,
    'Morning feeding completed.'),
  (5, 2, CURRENT_TIMESTAMP - INTERVAL 1 DAY,
    'Morning feeding completed.'),
  (6, 3, CURRENT_TIMESTAMP - INTERVAL 8 DAY,
    'Collection cup rinsed.'),
  (7, 4, CURRENT_TIMESTAMP - INTERVAL 7 DAY,
    'All readings were within the normal range.'),
  (8, 5, CURRENT_TIMESTAMP - INTERVAL 14 DAY,
    'Trimmed stem plants and removed damaged leaves.');

