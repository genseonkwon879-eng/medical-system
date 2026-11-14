package com.example.medical;

import com.example.medical.dto.DepartmentRequest;
import com.example.medical.repository.DepartmentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Opt in with MEDICAL_MYSQL_TESTS=true and DB_*; every test rolls back its own fixtures. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("mysql")
@Transactional
@EnabledIfEnvironmentVariable(named = "MEDICAL_MYSQL_TESTS", matches = "true")
class DepartmentIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private DepartmentRepository departments;
    @Autowired private JdbcTemplate jdbc;

    private String uniqueCode() {
        return "TEST_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
    }

    private String body(String code, String name, String description) throws Exception {
        return json.writeValueAsString(Map.of("code", code, "name", name, "description", description));
    }

    private long fixture(String code) {
        return departments.insert(new DepartmentRequest(code, "自动化测试科室", "事务回滚演示数据"));
    }

    @Test
    void completeCrudNormalizesInputAndAllowsUnchangedCode() throws Exception {
        String code = uniqueCode();
        var created = mvc.perform(post("/api/departments").contentType(MediaType.APPLICATION_JSON)
                        .content(body(" " + code.toLowerCase() + " ", "  测试门诊  ", "  介绍  ")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value(code))
                .andExpect(jsonPath("$.data.name").value("测试门诊"))
                .andReturn();
        long id = json.readTree(created.getResponse().getContentAsString()).at("/data/id").asLong();
        mvc.perform(get("/api/departments/{id}", id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.description").value("介绍"));
        mvc.perform(put("/api/departments/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content(body(code, "已修改门诊", "更新介绍")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("已修改门诊"));
        mvc.perform(delete("/api/departments/{id}", id)).andExpect(status().isOk());
        mvc.perform(get("/api/departments/{id}", id)).andExpect(status().isNotFound());
        assertTrue(departments.findById(id).isEmpty());
    }

    @Test
    void invalidInputAndMalformedJsonReturn400() throws Exception {
        for (String invalidBody : new String[]{
                body(uniqueCode(), "   ", ""),
                body("1_INVALID", "门诊", ""),
                body(uniqueCode(), "门".repeat(65), ""),
                body(uniqueCode(), "门诊", "介".repeat(256)),
                "{\"name\":\"门诊\"}", "{broken-json"
        }) {
            mvc.perform(post("/api/departments").contentType(MediaType.APPLICATION_JSON).content(invalidBody))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40001));
        }
    }

    @Test
    void duplicateCodesAreRejectedOnCreateAndUpdate() throws Exception {
        String code = uniqueCode();
        long firstId = fixture(code);
        long secondId = fixture(uniqueCode());
        mvc.perform(post("/api/departments").contentType(MediaType.APPLICATION_JSON)
                        .content(body(code.toLowerCase(), "重复科室", "")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(40901));
        mvc.perform(put("/api/departments/{id}", secondId).contentType(MediaType.APPLICATION_JSON)
                        .content(body(code, "重复科室", "")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(40901));
        assertEquals(code, departments.findById(firstId).orElseThrow().code());
        assertNotEquals(code, departments.findById(secondId).orElseThrow().code());
    }

    @Test
    void linkedDoctorPreventsDeletionWithoutChangingEitherRecord() throws Exception {
        long id = fixture(uniqueCode());
        jdbc.update("INSERT INTO doctor (department_id, name, title) VALUES (?, ?, ?)",
                id, "自动化测试医生", "教学测试");
        mvc.perform(delete("/api/departments/{id}", id))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(40902));
        assertTrue(departments.findById(id).isPresent());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM doctor WHERE department_id=?", Integer.class, id));
    }

    @Test
    void missingAndInvalidIdentifiersReturnUsefulStatusCodes() throws Exception {
        long absent = Long.MAX_VALUE;
        mvc.perform(get("/api/departments/{id}", absent)).andExpect(status().isNotFound());
        mvc.perform(put("/api/departments/{id}", absent).contentType(MediaType.APPLICATION_JSON)
                        .content(body(uniqueCode(), "不存在的科室", ""))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/departments/{id}", absent)).andExpect(status().isNotFound());
        mvc.perform(get("/api/departments/0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/departments/not-a-number")).andExpect(status().isBadRequest());
    }
}
