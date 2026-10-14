package com.careerconnect.health.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class HealthRepository {

    private static final Logger log = LoggerFactory.getLogger(HealthRepository.class);
    private final JdbcTemplate jdbcTemplate;

    public HealthRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isDatabaseConnected() {
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return result != null && result == 1;
        } catch (Exception ex) {
            log.error("Database health check failed: {}", ex.getMessage());
            return false;
        }
    }
}
