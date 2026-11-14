package com.example.medical.controller;

import com.example.medical.common.ApiResponse;
import com.example.medical.service.DatabaseService;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("mysql")
@RestController
@RequestMapping("/api/system")
public class DatabaseController {
    private final DatabaseService databaseService;

    public DatabaseController(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    @GetMapping("/database")
    public ApiResponse<DatabaseService.DatabaseStatus> database() {
        return ApiResponse.success(databaseService.checkConnection());
    }
}
