package com.example.medical;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class HelloControllerTest {
    @Autowired
    private MockMvc mvc;

    @Test
    void helloReturnsChineseJson() throws Exception {
        mvc.perform(get("/api/hello"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value("医疗系统 Hello World！"));
    }

    @Test
    void systemInfoIdentifiesProject() throws Exception {
        mvc.perform(get("/api/system/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("medical-system"));
    }

    @Test
    void demoDoesNotClaimDatabaseConnectivity() throws Exception {
        mvc.perform(get("/api/system/database")).andExpect(status().isNotFound());
    }
}
