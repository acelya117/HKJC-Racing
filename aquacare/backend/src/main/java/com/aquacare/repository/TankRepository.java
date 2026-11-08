package com.aquacare.repository;

import com.aquacare.model.Models.Tank;
import com.aquacare.model.Models.TankRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** SQL for table tanks (CRUD). */
@Repository
public class TankRepository {

    /** Base SELECT; task_count is a correlated sub-query on maintenance_tasks. */
    private static final String SELECT = """
            SELECT t.tank_id, t.user_id, t.tank_name, t.volume_liters, t.water_type,
                   t.image_url, t.created_at, t.updated_at,
                   (SELECT COUNT(*) FROM maintenance_tasks mt
                     WHERE mt.tank_id = t.tank_id) AS task_count
            FROM tanks t
            """;

    private static final RowMapper<Tank> MAPPER = (rs, i) -> new Tank(
            rs.getLong("tank_id"),
            rs.getLong("user_id"),
            rs.getString("tank_name"),
            rs.getBigDecimal("volume_liters"),
            rs.getString("water_type"),
            rs.getString("image_url"),
            rs.getObject("created_at", LocalDateTime.class),
            rs.getObject("updated_at", LocalDateTime.class),
            rs.getInt("task_count"));

    private final JdbcTemplate jdbc;

    public TankRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** READ: every tank, or only those of one user when userId is given. */
    public List<Tank> findAll(Long userId) {
        if (userId == null) {
            return jdbc.query(SELECT + " ORDER BY t.user_id, t.tank_name", MAPPER);
        }
        return jdbc.query(SELECT + " WHERE t.user_id = ? ORDER BY t.tank_name", MAPPER, userId);
    }

    public Optional<Tank> findById(long tankId) {
        return jdbc.query(SELECT + " WHERE t.tank_id = ?", MAPPER, tankId).stream().findFirst();
    }

    /** CREATE: returns the generated tank_id. */
    public long create(TankRequest r) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO tanks (user_id, tank_name, volume_liters, water_type, image_url)
                    VALUES (?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, r.userId());
            ps.setString(2, r.tankName().trim());
            ps.setBigDecimal(3, r.volumeLiters());
            ps.setString(4, r.waterType());
            ps.setString(5, blankToNull(r.imageUrl()));
            return ps;
        }, keys);
        return keys.getKey().longValue();
    }

    /** UPDATE: returns false when the tank does not exist. */
    public boolean update(long tankId, TankRequest r) {
        int rows = jdbc.update("""
                        UPDATE tanks
                        SET tank_name = ?, volume_liters = ?, water_type = ?, image_url = ?
                        WHERE tank_id = ?
                        """,
                r.tankName().trim(), r.volumeLiters(), r.waterType(),
                blankToNull(r.imageUrl()), tankId);
        return rows == 1;
    }

    /** DELETE: its tasks and completion history are removed by ON DELETE CASCADE. */
    public boolean delete(long tankId) {
        return jdbc.update("DELETE FROM tanks WHERE tank_id = ?", tankId) == 1;
    }

    static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
