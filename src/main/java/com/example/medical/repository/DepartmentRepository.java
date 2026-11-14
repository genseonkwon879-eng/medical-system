package com.example.medical.repository;

import com.example.medical.entity.Department;
import com.example.medical.dto.DepartmentRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.sql.Statement;

@Profile("mysql")
@Repository
public class DepartmentRepository {
    private final JdbcTemplate jdbcTemplate;
    private static final RowMapper<Department> ROW_MAPPER = (row, rowNumber) ->
            new Department(row.getLong("id"), row.getString("code"),
                    row.getString("name"), row.getString("description"));

    public DepartmentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Department> findAll() {
        return jdbcTemplate.query(
                "SELECT id, code, name, description FROM department ORDER BY id",
                ROW_MAPPER);
    }

    public Optional<Department> findById(long id) {
        return jdbcTemplate.query("SELECT id, code, name, description FROM department WHERE id = ?",
                ROW_MAPPER, id).stream().findFirst();
    }

    public boolean codeExists(String code, Long excludedId) {
        Integer count = excludedId == null
                ? jdbcTemplate.queryForObject("SELECT COUNT(*) FROM department WHERE code = ?", Integer.class, code)
                : jdbcTemplate.queryForObject("SELECT COUNT(*) FROM department WHERE code = ? AND id <> ?",
                        Integer.class, code, excludedId);
        return count != null && count > 0;
    }

    public long insert(DepartmentRequest request) {
        var keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                    "INSERT INTO department (code, name, description) VALUES (?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, request.code());
            statement.setString(2, request.name());
            statement.setString(3, request.description());
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) throw new IllegalStateException("Database did not return the generated department ID");
        return key.longValue();
    }

    public int update(long id, DepartmentRequest request) {
        return jdbcTemplate.update("UPDATE department SET code = ?, name = ?, description = ? WHERE id = ?",
                request.code(), request.name(), request.description(), id);
    }

    public boolean hasDoctors(long id) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM doctor WHERE department_id = ?",
                Integer.class, id);
        return count != null && count > 0;
    }

    public int delete(long id) {
        return jdbcTemplate.update("DELETE FROM department WHERE id = ?", id);
    }
}
