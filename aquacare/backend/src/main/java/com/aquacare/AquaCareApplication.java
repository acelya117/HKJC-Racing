package com.aquacare;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the AquaCare backend.
 * Starts an embedded web server (default http://localhost:8080) that exposes
 * a JSON REST API under /api and serves the frontend from resources/static.
 */
@SpringBootApplication
public class AquaCareApplication {

    public static void main(String[] args) {
        SpringApplication.run(AquaCareApplication.class, args);
    }
}
