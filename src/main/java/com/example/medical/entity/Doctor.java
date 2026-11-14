package com.example.medical.entity;

public record Doctor(
    Long id,
    Long departmentId,
    String name,
    String title
) { }
