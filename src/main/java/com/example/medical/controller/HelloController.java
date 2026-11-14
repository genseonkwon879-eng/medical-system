package com.example.medical.controller;

import com.example.medical.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HelloController {
    @GetMapping("/hello")
    public ApiResponse<String> hello() {
        return ApiResponse.success("医疗系统 Hello World！");
    }

    @GetMapping("/system/info")
    public ApiResponse<SystemInfo> info() {
        return ApiResponse.success(new SystemInfo("medical-system", "0.0.1-SNAPSHOT", "实验一：工程初始化"));
    }

    public record SystemInfo(String name, String version, String stage) { }
}
