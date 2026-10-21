package com.example.usercenter.controller;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/db")
public class DatabaseTestController {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseTestController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/test")
    public Integer testDatabase() {
        return jdbcTemplate.queryForObject("SELECT 1", Integer.class);
    }
}