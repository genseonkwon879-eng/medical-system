package com.example.medical.controller;

import com.example.medical.common.ApiResponse;
import com.example.medical.entity.Department;
import com.example.medical.dto.DepartmentRequest;
import com.example.medical.service.DepartmentService;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;

@Profile("mysql")
@RestController
@RequestMapping("/api/departments")
public class DepartmentController {
    private final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    @GetMapping
    public ApiResponse<List<Department>> list() {
        return ApiResponse.success(departmentService.findAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<Department> detail(@PathVariable long id) {
        return ApiResponse.success(departmentService.findById(id));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Department>> create(@Valid @RequestBody DepartmentRequest request) {
        Department department = departmentService.create(request);
        return ResponseEntity.created(URI.create("/api/departments/" + department.id()))
                .body(ApiResponse.success(department));
    }

    @PutMapping("/{id}")
    public ApiResponse<Department> update(@PathVariable long id, @Valid @RequestBody DepartmentRequest request) {
        return ApiResponse.success(departmentService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) {
        departmentService.delete(id);
        return ApiResponse.success(null);
    }
}
