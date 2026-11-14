package com.example.medical;

import com.example.medical.controller.DepartmentController;
import com.example.medical.service.DepartmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DepartmentController.class)
@ActiveProfiles("mysql")
class DepartmentConflictTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private DepartmentService service;

    @Test
    void databaseUniqueConstraintRaceReturns409WithoutSqlDetails() throws Exception {
        given(service.create(any())).willThrow(new DuplicateKeyException("internal SQL and connection details"));
        mvc.perform(post("/api/departments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"CLINIC\",\"name\":\"测试科室\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(40901))
                .andExpect(jsonPath("$.message").value("科室编码已存在，请使用其他编码"));
    }
}
