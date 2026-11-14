
package com.example.medical.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record DoctorRequest(

    @NotNull(message = "请选择所属科室")
    @Positive(message = "科室ID必须为正整数")
    Long departmentId,

    @NotBlank(message = "请输入医生姓名")
    @Size(max = 64, message = "医生姓名不能超过64个字符")
    String name,

    @Size(max = 64, message = "医生职称不能超过64个字符")
    String title

) {
    public DoctorRequest {
        name = name == null ? null : name.strip();
        title = title == null || title.isBlank()
            ? null : title.strip();
    }
}
