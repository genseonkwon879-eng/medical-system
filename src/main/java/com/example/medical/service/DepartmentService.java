package com.example.medical.service;

import com.example.medical.entity.Department;
import com.example.medical.dto.DepartmentRequest;
import com.example.medical.common.BusinessException;
import com.example.medical.repository.DepartmentRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Profile("mysql")
@Service
public class DepartmentService {
    private final DepartmentRepository departmentRepository;

    public DepartmentService(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    public List<Department> findAll() {
        return departmentRepository.findAll();
    }

    public Department findById(long id) {
        validateId(id);
        return departmentRepository.findById(id).orElseThrow(this::notFound);
    }

    @Transactional
    public Department create(DepartmentRequest request) {
        ensureUniqueCode(request.code(), null);
        long id = departmentRepository.insert(request);
        return new Department(id, request.code(), request.name(), request.description());
    }

    @Transactional
    public Department update(long id, DepartmentRequest request) {
        findById(id);
        ensureUniqueCode(request.code(), id);
        if (departmentRepository.update(id, request) == 0) throw notFound();
        return new Department(id, request.code(), request.name(), request.description());
    }

    @Transactional
    public void delete(long id) {
        findById(id);
        if (departmentRepository.hasDoctors(id)) throw linkedDoctors();
        try {
            if (departmentRepository.delete(id) == 0) throw notFound();
        } catch (DataIntegrityViolationException exception) {
            // The FK also protects a doctor inserted concurrently with this deletion.
            throw linkedDoctors();
        }
    }

    private void ensureUniqueCode(String code, Long excludedId) {
        if (departmentRepository.codeExists(code, excludedId)) {
            throw new BusinessException(HttpStatus.CONFLICT, 40901, "科室编码已存在，请使用其他编码");
        }
    }

    private void validateId(long id) {
        if (id <= 0) throw new BusinessException(HttpStatus.BAD_REQUEST, 40001, "科室ID必须为正整数");
    }

    private BusinessException notFound() {
        return new BusinessException(HttpStatus.NOT_FOUND, 40401, "科室不存在或已被删除，请刷新列表");
    }

    private BusinessException linkedDoctors() {
        return new BusinessException(HttpStatus.CONFLICT, 40902, "该科室已关联医生，请先调整医生所属科室");
    }
}
