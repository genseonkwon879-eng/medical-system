-- MySQL 8.x。非破坏性初始化：不包含 DROP / TRUNCATE。
-- 表已存在时不会替换结构；后续结构变更应使用单独的迁移脚本。
CREATE DATABASE IF NOT EXISTS medical_system
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE medical_system;

CREATE TABLE IF NOT EXISTS department (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(32) NOT NULL UNIQUE,
    name VARCHAR(64) NOT NULL,
    description VARCHAR(255),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS doctor (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    department_id BIGINT NOT NULL,
    name VARCHAR(64) NOT NULL,
    title VARCHAR(64),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_doctor_department FOREIGN KEY (department_id) REFERENCES department(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS patient (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(64) NOT NULL,
    gender VARCHAR(16),
    birth_date DATE,
    phone VARCHAR(32),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS appointment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    appointment_no VARCHAR(40) NOT NULL UNIQUE,
    patient_id BIGINT NOT NULL,
    doctor_id BIGINT NOT NULL,
    appointment_time DATETIME NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_appointment_doctor_time (doctor_id, appointment_time),
    INDEX idx_appointment_patient_time (patient_id, appointment_time),
    CONSTRAINT fk_appointment_patient FOREIGN KEY (patient_id) REFERENCES patient(id),
    CONSTRAINT fk_appointment_doctor FOREIGN KEY (doctor_id) REFERENCES doctor(id),
    CONSTRAINT chk_appointment_status CHECK (status IN ('PENDING', 'COMPLETED', 'CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 演示数据仅含科室，不含真实个人或诊疗信息。
INSERT INTO department (code, name, description)
SELECT 'INTERNAL', '内科', '教学演示科室'
WHERE NOT EXISTS (SELECT 1 FROM department WHERE code = 'INTERNAL');
INSERT INTO department (code, name, description)
SELECT 'SURGERY', '外科', '教学演示科室'
WHERE NOT EXISTS (SELECT 1 FROM department WHERE code = 'SURGERY');
INSERT INTO department (code, name, description)
SELECT 'PEDIATRICS', '儿科', '教学演示科室'
WHERE NOT EXISTS (SELECT 1 FROM department WHERE code = 'PEDIATRICS');
