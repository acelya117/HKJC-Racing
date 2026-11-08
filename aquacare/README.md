# AquaCare – Aquarium Maintenance Manager (COMP3278 Final Project)

AquaCare helps aquarium owners keep track of their tanks and recurring
maintenance work (water changes, feeding, filter cleaning, ...). Users can
create tanks, schedule maintenance tasks, see what is due or overdue, mark a
task as done (which records history and schedules the next due date), and
review the completion history.

```
aquacare/
├── database/              MySQL schema, seed data, key SQL (Part 1)
│   ├── 01_schema.sql      tables, views, stored procedure
│   ├── 02_seed.sql        demonstration data
│   ├── 03_key_queries.sql representative SQL used in the presentation
│   └── DATABASE_SETUP.md
└── backend/               Java web backend – Spring Boot + JDBC (Part 2)
    ├── pom.xml
    ├── mvnw / mvnw.cmd     Maven wrapper (no Maven install needed)
    └── src/main/
        ├── java/com/aquacare/
        │   ├── AquaCareApplication.java   entry point
        │   ├── controller/   REST endpoints (/api/...)
        │   ├── repository/   all SQL statements (JdbcTemplate, prepared statements)
        │   ├── model/        row + request records
        │   ├── config/       CORS
        │   └── web/          JSON error handling
        └── resources/
            ├── application.properties   DB connection settings
            └── static/                   frontend files (Part 3) are served from here
```

---

## 1. Requirements

| Software | Version | Check |
|---|---|---|
| Java JDK | 17 or later | `java -version` |
| MySQL Community Server | 8.0 or later | `mysql --version` |

Maven does **not** need to be installed – the bundled wrapper (`mvnw`)
downloads it automatically on the first run (internet connection required
the first time).

---

## 2. Database setup (schema + initial data)

Run these commands from the `aquacare/` folder. Enter your MySQL password when
prompted.

```bash
mysql -u root -p < database/01_schema.sql
```

```bash
mysql -u root -p < database/02_seed.sql
```

`01_schema.sql` creates the `aquacare` database with 4 tables
(`users`, `tanks`, `maintenance_tasks`, `task_completions`), 2 views
(`v_due_tasks`, `v_maintenance_history`) and 1 stored procedure
(`complete_maintenance_task`). `02_seed.sql` inserts 2 users, 3 tanks,
6 maintenance tasks and 8 completion records. Due dates are relative to the
import date, so there is always something due today.

Verify:

```bash
mysql -u root -p -e "USE aquacare; SELECT COUNT(*) FROM users; SELECT COUNT(*) FROM tanks; SELECT COUNT(*) FROM maintenance_tasks; SELECT COUNT(*) FROM task_completions;"
```

Expected counts: `2`, `3`, `6`, `8`.

To reset to the original demo data at any time, run both scripts again in the
same order.

> **Linux note:** on Ubuntu/Debian the MySQL `root` account often uses socket
> authentication and cannot log in from Java with a password. In that case
> create a dedicated account and use it in step 3:
>
> ```bash
> sudo mysql -e "CREATE USER 'aquacare'@'localhost' IDENTIFIED BY 'aquacare123'; GRANT ALL ON aquacare.* TO 'aquacare'@'localhost';"
> ```

---

## 3. Configure the database connection

The backend reads three environment variables (defaults in brackets):

| Variable | Default |
|---|---|
| `DB_URL` | `jdbc:mysql://localhost:3306/aquacare?useSSL=false&serverTimezone=Asia/Hong_Kong&allowPublicKeyRetrieval=true` |
| `DB_USER` | `root` |
| `DB_PASSWORD` | *(empty)* |
| `PORT` | `8080` (web server port) |

Change the port in `DB_URL` if your MySQL does not run on 3306. Do not write
your real password into `application.properties`.

---

## 4. Install and run the backend

From the `aquacare/backend` folder:

**macOS / Linux**

```bash
cd backend
```

```bash
DB_USER=root DB_PASSWORD=your_mysql_password ./mvnw spring-boot:run
```

**Windows (PowerShell)**

```powershell
cd backend
$env:DB_USER="root"; $env:DB_PASSWORD="your_mysql_password"; .\mvnw.cmd spring-boot:run
```

The first run downloads dependencies (about 1 minute). When the log shows
`Tomcat started on port 8080`, open:

- <http://localhost:8080/api/health> – should return `"database":"connected"`
- <http://localhost:8080/> – the web app (frontend in `src/main/resources/static`)

Stop the server with `Ctrl + C`.

*Alternative (build a jar once, then run it):*

```bash
./mvnw package -DskipTests
```

```bash
DB_USER=root DB_PASSWORD=your_mysql_password java -jar target/aquacare-backend.jar
```

---

## 5. REST API

All responses are JSON. Field names are **snake_case** and match the database
columns (e.g. `tank_id`, `next_due_date`). Dates use `yyyy-MM-dd`, times use
`HH:mm` or `HH:mm:ss`. Errors return `{"status": <code>, "error": "<message>"}`.

### Users

| Method | URL | Description |
|---|---|---|
| GET | `/api/users` | list users |
| GET | `/api/users/{id}` | one user |
| POST | `/api/users` | create – body `{"username","email"}` |
| DELETE | `/api/users/{id}` | delete user (cascades to tanks/tasks/history) |

### Tanks (CRUD)

| Method | URL | Description |
|---|---|---|
| GET | `/api/tanks?user_id=1` | tanks of a user (`user_id` optional), incl. `task_count` |
| GET | `/api/tanks/{id}` | one tank |
| GET | `/api/tanks/{id}/tasks` | maintenance tasks of the tank |
| POST | `/api/tanks` | create – body below |
| PUT | `/api/tanks/{id}` | update – same body (`user_id` ignored) |
| DELETE | `/api/tanks/{id}` | delete tank (cascades to its tasks and history) |

```json
{ "user_id": 1, "tank_name": "Demo Tank", "volume_liters": 60,
  "water_type": "Freshwater", "image_url": "/images/demo.jpg" }
```
`water_type` must be `Freshwater`, `Saltwater` or `Brackish`.

### Maintenance tasks (CRUD + complete)

| Method | URL | Description |
|---|---|---|
| GET | `/api/tasks?tank_id=1` | tasks (`tank_id` optional) |
| GET | `/api/tasks/due?user_id=1` | due today + overdue (view `v_due_tasks`) |
| GET | `/api/tasks/{id}` | one task |
| POST | `/api/tasks` | create – body below |
| PUT | `/api/tasks/{id}` | update – same body (`tank_id` ignored) |
| PATCH | `/api/tasks/{id}/active` | pause/resume – `{"is_active": false}` |
| DELETE | `/api/tasks/{id}` | delete task (cascades to its history) |
| POST | `/api/tasks/{id}/complete` | mark as done – optional `{"notes": "..."}`; returns the task with its new `next_due_date` |

```json
{ "tank_id": 1, "task_name": "Glass cleaning", "description": "Use the scraper",
  "frequency_days": 7, "scheduled_time": "16:00", "next_due_date": "2026-11-20",
  "is_active": true }
```
`frequency_days` must be 1–365; `is_active` defaults to `true`.

### History

| Method | URL | Description |
|---|---|---|
| GET | `/api/history?user_id=&tank_id=&task_id=&limit=100` | completion history, newest first (all filters optional) |
| GET | `/api/history/summary?user_id=1&days=30` | completions per task in the last N days |
| DELETE | `/api/history/{completion_id}` | remove a wrong completion record |

### Quick test with curl

```bash
curl http://localhost:8080/api/tanks?user_id=1
```

```bash
curl -X POST http://localhost:8080/api/tanks -H "Content-Type: application/json" -d '{"user_id":1,"tank_name":"Demo Tank","volume_liters":60,"water_type":"Freshwater"}'
```

```bash
curl -X POST http://localhost:8080/api/tasks/1/complete -H "Content-Type: application/json" -d '{"notes":"Changed 25% of the water"}'
```

```bash
curl "http://localhost:8080/api/history?tank_id=1"
```

---

## 6. Where the key SQL lives (for the presentation)

| Feature | SQL | File |
|---|---|---|
| List tanks + task count | `SELECT ... (SELECT COUNT(*) FROM maintenance_tasks ...) FROM tanks WHERE user_id = ?` | `repository/TankRepository.java` |
| Create / update / delete tank | `INSERT INTO tanks ...`, `UPDATE tanks SET ...`, `DELETE FROM tanks ...` | `repository/TankRepository.java` |
| Task CRUD (with JOIN to tanks) | `INSERT/UPDATE/DELETE maintenance_tasks` | `repository/TaskRepository.java` |
| Due / overdue tasks | `SELECT ... FROM v_due_tasks WHERE user_id = ?` | `repository/TaskRepository.java`, view in `01_schema.sql` |
| Mark task done (transaction) | `CALL complete_maintenance_task(?, ?)` | `repository/TaskRepository.java`, procedure in `01_schema.sql` |
| History | `SELECT ... FROM v_maintenance_history ... ORDER BY completed_at DESC` | `repository/HistoryRepository.java` |
| Monthly summary | `COUNT(*) ... GROUP BY tank_id, task_id` | `repository/HistoryRepository.java` |

All user-supplied values are passed through prepared-statement `?`
parameters (no string concatenation of user input), which prevents SQL
injection.

---

## 7. Troubleshooting

| Symptom | Fix |
|---|---|
| `/api/health` shows `"database":"disconnected"` | MySQL is not running, or `DB_USER` / `DB_PASSWORD` / `DB_URL` are wrong. |
| `Access denied for user 'root'@'localhost'` | Wrong password, or root uses socket auth (see Linux note in §2). |
| `Unknown database 'aquacare'` | Run `database/01_schema.sql` first. |
| `Port 8080 was already in use` | Start with another port: `PORT=8081 ./mvnw spring-boot:run`. |
| `./mvnw: Permission denied` | `chmod +x mvnw` |
