
package com.example.medical.repository;

import com.example.medical.entity.Doctor;
import com.example.medical.dto.DoctorRequest;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Statement;
import java.util.List;
import java.util.Optional;

@Profile("mysql")
@Repository
public class DoctorRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<Doctor> ROW_MAPPER =
        (row, rowNumber) -> new Doctor(
            row.getLong("id"),
            row.getLong("department_id"),
            row.getString("name"),
            row.getString("title")
        );

    public DoctorRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // 查询所有医生
    public List<Doctor> findAll() {
        return jdbcTemplate.query(
            "SELECT id, department_id, name, title " +
                "FROM doctor ORDER BY id",
            ROW_MAPPER
        );
    }

    // 根据ID查询医生
    public Optional<Doctor> findById(long id) {
        return jdbcTemplate.query(
            "SELECT id, department_id, name, title " +
                "FROM doctor WHERE id = ?",
            ROW_MAPPER,
            id
        ).stream().findFirst();
    }

    // 新增医生
    public long insert(DoctorRequest request) {

        var keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                "INSERT INTO doctor " +
                    "(department_id, name, title) " +
                    "VALUES (?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS
            );

            statement.setLong(1, request.departmentId());
            statement.setString(2, request.name());
            statement.setString(3, request.title());

            return statement;
        }, keyHolder);

        Number key = keyHolder.getKey();

        if (key == null) {
            throw new IllegalStateException(
                "数据库未返回医生ID"
            );
        }

        return key.longValue();
    }

    // 修改医生信息
    public int update(long id, DoctorRequest request) {
        return jdbcTemplate.update(
            "UPDATE doctor " +
                "SET department_id = ?, name = ?, title = ? " +
                "WHERE id = ?",
            request.departmentId(),
            request.name(),
            request.title(),
            id
        );
    }

    // 删除医生
    public int delete(long id) {
        return jdbcTemplate.update(
            "DELETE FROM doctor WHERE id = ?",
            id
        );
    }
}
