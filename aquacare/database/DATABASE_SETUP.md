# AquaCare Database Setup

## Requirements

- MySQL Community Server 8.0 or later
- A MySQL account that can create a database, tables, views, and procedures

## 1. Create the database structure

From the `aquacare/` project folder (the parent of this `database/` folder), run:

```bash
mysql -u root -p < database/01_schema.sql
```

The script creates the `aquacare` database. It is designed as a clean rebuild:
it drops only AquaCare views, procedures, and tables inside that database before
recreating them. Do not run it against a database containing data that must be
kept.

## 2. Import demonstration data

```bash
mysql -u root -p < database/02_seed.sql
```

The seed file creates:

- 2 users
- 3 tanks
- 6 maintenance tasks
- 8 completion records

The task dates are calculated relative to the import date, so the due-task page
will always contain useful demonstration data.

## 3. Verify the installation

```bash
mysql -u root -p -e "USE aquacare; SELECT COUNT(*) AS users FROM users; SELECT COUNT(*) AS tanks FROM tanks; SELECT COUNT(*) AS tasks FROM maintenance_tasks; SELECT COUNT(*) AS completions FROM task_completions; SELECT * FROM v_due_tasks;"
```

Expected row counts are `2`, `3`, `6`, and `8`. The final query should return at
least two active tasks that are due or overdue.

## 4. Java backend configuration

Use environment variables or a local configuration file excluded from version
control. Do not put a real database password in the source code.

```text
DB_URL=jdbc:mysql://localhost:3306/aquacare?useSSL=false&serverTimezone=Asia/Hong_Kong&allowPublicKeyRetrieval=true
DB_USER=<your MySQL username>
DB_PASSWORD=<your MySQL password>
```

If the local MySQL server uses another port, replace `3306` in `DB_URL`.

## 5. Backend-facing database objects

- `v_due_tasks`: active due/overdue tasks with user and tank details.
- `v_maintenance_history`: completed-task history with user, tank, and task
  details.
- `complete_maintenance_task(task_id, notes)`: inserts a completion record and
  advances the task's next due date in one transaction.

Example:

```sql
CALL complete_maintenance_task(1, 'Changed 25% of the water');

SELECT *
FROM v_maintenance_history
WHERE tank_id = 1
ORDER BY completed_at DESC;
```

## Rebuild

To return to the original demonstration state, rerun `01_schema.sql` and then
`02_seed.sql` in that order.

