package com.example.medical;

import com.example.medical.controller.DatabaseController;
import com.example.medical.service.DatabaseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DatabaseController.class)
@ActiveProfiles("mysql")
class DatabaseControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private DatabaseService databaseService;

    @Test
    void databaseFailureReturns503WithoutConnectionDetails() throws Exception {
        given(databaseService.checkConnection())
                .willThrow(new DataAccessResourceFailureException("internal connection details"));
        mvc.perform(get("/api/system/database"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(50301))
                .andExpect(jsonPath("$.message").value("数据库暂不可用，请检查服务、端口、账号和初始化脚本"))
                .andExpect(jsonPath("$.data").isEmpty());
    }
}
