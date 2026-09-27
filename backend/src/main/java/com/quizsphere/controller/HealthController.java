package com.quizsphere.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class HealthController {

    private final JdbcTemplate jdbc;

    public HealthController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        boolean db;
        try {
            db = Integer.valueOf(1).equals(jdbc.queryForObject("select 1", Integer.class));
        } catch (Exception e) {
            db = false;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", db ? "UP" : "DEGRADED");
        body.put("database", db ? "UP" : "DOWN");
        body.put("time", Instant.now());
        return ResponseEntity.status(db ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }
}
