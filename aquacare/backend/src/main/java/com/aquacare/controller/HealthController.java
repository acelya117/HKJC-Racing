package com.aquacare.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** GET /api/health : quick check that the server and MySQL are reachable. */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final JdbcTemplate jdbc;

    public HealthController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        try {
            String db = jdbc.queryForObject("SELECT DATABASE()", String.class);
            return ResponseEntity.ok(Map.of("server", "up", "database", "connected", "schema", String.valueOf(db)));
        } catch (Exception e) {
            return ResponseEntity.status(503).body(Map.of("server", "up", "database", "disconnected",
                    "error", String.valueOf(e.getMessage())));
        }
    }
}
