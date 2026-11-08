package com.aquacare.repository;

import com.aquacare.model.Models.DueTask;
import com.aquacare.model.Models.MaintenanceTask;
import com.aquacare.model.Models.TaskRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * SQL for table maintenance_tasks (CRUD), view v_due_tasks, and the stored
 * procedure complete_maintenance_task.
 */
@Repository
public class TaskRepository {

    private static final String SELECT = """
            SELECT mt.task_id, mt.tank_id, t.tank_name, mt.task_name, mt.description,
                   mt.frequency_days, mt.scheduled_time, mt.next_due_date, mt.is_active,
                   mt.created_at, mt.updated_at
            FROM maintenance_tasks mt
            JOIN tanks t ON t.tank_id = mt.tank_id
            """;

    private static final RowMapper<MaintenanceTask> MAPPER = (rs, i) -> new MaintenanceTask(
            rs.getLong("task_id"),
            rs.getLong("tank_id"),
            rs.getString("tank_name"),
            rs.getString("task_name"),
            rs.getString("description"),
            rs.getInt("frequency_days"),
            rs.getObject("scheduled_time", LocalTime.class),
            rs.getObject("next_due_date", LocalDate.class),
            rs.getBoolean("is_active"),
            rs.getObject("created_at", LocalDateTime.class),
            rs.getObject("updated_at", LocalDateTime.class));

    private static final RowMapper<DueTask> DUE_MAPPER = (rs, i) -> new DueTask(
            rs.getLong("task_id"),
            rs.getString("task_name"),
            rs.getString("description"),
            rs.getInt("frequency_days"),
            rs.getObject("scheduled_time", LocalTime.class),
            rs.getObject("next_due_date", LocalDate.class),
            rs.getString("due_status"),
            rs.getLong("tank_id"),
            rs.getString("tank_name"),
            rs.getString("water_type"),
            rs.getLong("user_id"),
            rs.getString("username"));

    private final JdbcTemplate jdbc;

    public TaskRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** READ: tasks of one tank, or all tasks when tankId is null. */
    public List<MaintenanceTask> findAll(Long tankId) {
        String order = " ORDER BY mt.is_active DESC, mt.next_due_date, mt.scheduled_time";
        if (tankId == null) {
            return jdbc.query(SELECT + order, MAPPER);
        }
        return jdbc.query(SELECT + " WHERE mt.tank_id = ?" + order, MAPPER, tankId);
    }

    public Optional<MaintenanceTask> findById(long taskId) {
        return jdbc.query(SELECT + " WHERE mt.task_id = ?", MAPPER, taskId).stream().findFirst();
    }

    /** READ: due / overdue active tasks from view v_due_tasks. */
    public List<DueTask> findDue(Long userId) {
        String sql = """
                SELECT task_id, task_name, description, frequency_days, scheduled_time,
                       next_due_date, due_status, tank_id, tank_name, water_type,
                       user_id, username
                FROM v_due_tasks
                """;
        String order = " ORDER BY next_due_date, scheduled_time, tank_name";
        if (userId == null) {
            return jdbc.query(sql + order, DUE_MAPPER);
        }
        return jdbc.query(sql + " WHERE user_id = ?" + order, DUE_MAPPER, userId);
    }

    /** CREATE: returns the generated task_id. */
    public long create(TaskRequest r) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO maintenance_tasks
                        (tank_id, task_name, description, frequency_days,
                         scheduled_time, next_due_date, is_active)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, r.tankId());
            ps.setString(2, r.taskName().trim());
            ps.setString(3, TankRepository.blankToNull(r.description()));
            ps.setInt(4, r.frequencyDays());
            ps.setObject(5, r.scheduledTime(), Types.TIME);
            ps.setObject(6, r.nextDueDate(), Types.DATE);
            ps.setBoolean(7, r.isActive() == null || r.isActive());
            return ps;
        }, keys);
        return keys.getKey().longValue();
    }

    /** UPDATE: returns false when the task does not exist. */
    public boolean update(long taskId, TaskRequest r) {
        int rows = jdbc.update("""
                        UPDATE maintenance_tasks
                        SET task_name = ?, description = ?, frequency_days = ?,
                            scheduled_time = ?, next_due_date = ?, is_active = ?
                        WHERE task_id = ?
                        """,
                r.taskName().trim(), TankRepository.blankToNull(r.description()),
                r.frequencyDays(), r.scheduledTime(), r.nextDueDate(),
                r.isActive() == null || r.isActive(), taskId);
        return rows == 1;
    }

    /** UPDATE: pause or resume a task without touching the other columns. */
    public boolean setActive(long taskId, boolean active) {
        return jdbc.update("UPDATE maintenance_tasks SET is_active = ? WHERE task_id = ?",
                active, taskId) == 1;
    }

    /** DELETE: its completion history is removed by ON DELETE CASCADE. */
    public boolean delete(long taskId) {
        return jdbc.update("DELETE FROM maintenance_tasks WHERE task_id = ?", taskId) == 1;
    }

    /**
     * Marks a task as done by calling the stored procedure, which inserts a
     * task_completions row and advances next_due_date in one transaction.
     * The procedure raises SQLSTATE 45000 if the task is missing or inactive.
     */
    public void complete(long taskId, String notes) {
        jdbc.update("CALL complete_maintenance_task(?, ?)", taskId, notes);
    }
}
