package com.aquacare.repository;

import com.aquacare.model.Models.User;
import com.aquacare.model.Models.UserRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

/** SQL for table users. */
@Repository
public class UserRepository {

    private static final RowMapper<User> MAPPER = (rs, i) -> new User(
            rs.getLong("user_id"),
            rs.getString("username"),
            rs.getString("email"),
            rs.getObject("created_at", java.time.LocalDateTime.class));

    private final JdbcTemplate jdbc;

    public UserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<User> findAll() {
        return jdbc.query(
                "SELECT user_id, username, email, created_at FROM users ORDER BY user_id",
                MAPPER);
    }

    public Optional<User> findById(long userId) {
        return jdbc.query(
                "SELECT user_id, username, email, created_at FROM users WHERE user_id = ?",
                MAPPER, userId).stream().findFirst();
    }

    public long create(UserRequest r) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO users (username, email) VALUES (?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, r.username().trim());
            ps.setString(2, r.email().trim());
            return ps;
        }, keys);
        return keys.getKey().longValue();
    }

    /** Deleting a user cascades to their tanks, tasks and history (ON DELETE CASCADE). */
    public boolean delete(long userId) {
        return jdbc.update("DELETE FROM users WHERE user_id = ?", userId) == 1;
    }
}
