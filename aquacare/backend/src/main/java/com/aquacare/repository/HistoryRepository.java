package com.aquacare.repository;

import com.aquacare.model.Models.HistoryEntry;
import com.aquacare.model.Models.HistorySummary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** SQL for view v_maintenance_history and table task_completions. */
@Repository
public class HistoryRepository {

    private static final RowMapper<HistoryEntry> MAPPER = (rs, i) -> new HistoryEntry(
            rs.getLong("completion_id"),
            rs.getObject("completed_at", LocalDateTime.class),
            rs.getString("notes"),
            rs.getLong("task_id"),
            rs.getString("task_name"),
            rs.getInt("frequency_days"),
            rs.getLong("tank_id"),
            rs.getString("tank_name"),
            rs.getLong("user_id"),
            rs.getString("username"));

    private static final RowMapper<HistorySummary> SUMMARY_MAPPER = (rs, i) -> new HistorySummary(
            rs.getLong("tank_id"),
            rs.getString("tank_name"),
            rs.getLong("task_id"),
            rs.getString("task_name"),
            rs.getLong("completion_count"),
            rs.getObject("most_recent_completion", LocalDateTime.class));

    private final JdbcTemplate jdbc;

    public HistoryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** READ: completion history, optionally filtered by user, tank and/or task. */
    public List<HistoryEntry> find(Long userId, Long tankId, Long taskId, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT completion_id, completed_at, notes, task_id, task_name,
                       frequency_days, tank_id, tank_name, user_id, username
                FROM v_maintenance_history
                WHERE 1 = 1
                """);
        List<Object> args = new ArrayList<>();
        if (userId != null) {
            sql.append(" AND user_id = ?");
            args.add(userId);
        }
        if (tankId != null) {
            sql.append(" AND tank_id = ?");
            args.add(tankId);
        }
        if (taskId != null) {
            sql.append(" AND task_id = ?");
            args.add(taskId);
        }
        sql.append(" ORDER BY completed_at DESC LIMIT ?");
        args.add(limit);
        return jdbc.query(sql.toString(), MAPPER, args.toArray());
    }

    /** READ (aggregate): completions per task during the last N days. */
    public List<HistorySummary> summary(Long userId, int days) {
        StringBuilder sql = new StringBuilder("""
                SELECT tank_id, tank_name, task_id, task_name,
                       COUNT(*) AS completion_count,
                       MAX(completed_at) AS most_recent_completion
                FROM v_maintenance_history
                WHERE completed_at >= CURRENT_TIMESTAMP - INTERVAL ? DAY
                """);
        List<Object> args = new ArrayList<>();
        args.add(days);
        if (userId != null) {
            sql.append(" AND user_id = ?");
            args.add(userId);
        }
        sql.append(" GROUP BY tank_id, tank_name, task_id, task_name ORDER BY tank_name, task_name");
        return jdbc.query(sql.toString(), SUMMARY_MAPPER, args.toArray());
    }

    /** DELETE: remove one wrongly recorded completion. */
    public boolean delete(long completionId) {
        return jdbc.update("DELETE FROM task_completions WHERE completion_id = ?", completionId) == 1;
    }
}
