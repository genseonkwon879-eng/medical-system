package com.example.medical.service;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Profile("mysql")
@Service
public class DatabaseService {
    private final JdbcTemplate jdbcTemplate;

    public DatabaseService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public DatabaseStatus checkConnection() {
        Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        return new DatabaseStatus(Integer.valueOf(1).equals(result), "MySQL");
    }

    public record DatabaseStatus(boolean connected, String database) { }
}
