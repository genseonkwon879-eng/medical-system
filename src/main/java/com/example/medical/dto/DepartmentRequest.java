package com.example.medical.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public record DepartmentRequest(
        @NotBlank(message = "请输入科室编码")
        @Pattern(regexp = "[A-Z][A-Z0-9_]{0,31}", message = "科室编码须以字母开头，仅含字母、数字或下划线，最长32位")
        String code,
        @NotBlank(message = "请输入科室名称")
        @Size(max = 64, message = "科室名称不能超过64个字符")
        String name,
        @Size(max = 255, message = "科室简介不能超过255个字符")
        String description) {
    public DepartmentRequest {
        code = code == null ? null : code.strip().toUpperCase(Locale.ROOT);
        name = name == null ? null : name.strip();
        description = description == null ? null : description.strip();
    }
}
