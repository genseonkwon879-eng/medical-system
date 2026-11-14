-- 实验二增量迁移。由 scripts/Update-MedicalSchema.ps1 执行并记录校验和。
-- 仅对已选中的项目数据库执行；不含 DROP/TRUNCATE，不重写旧数据。
-- 执行前必须已有 01-schema.sql 中的4张基础表。MySQL 8.0.16及以上。

ALTER TABLE doctor
    ADD COLUMN doctor_no VARCHAR(32) NULL COMMENT '医生工号，旧数据允许暂缺',
    ADD COLUMN phone VARCHAR(32) NULL COMMENT '医生联系电话，应用层校验',
    ADD CONSTRAINT uk_doctor_no UNIQUE (doctor_no),
    ADD INDEX idx_doctor_name (name);

ALTER TABLE patient
    ADD COLUMN identity_card VARCHAR(18) NULL COMMENT '身份证号，旧数据允许暂缺，接口需校验并脱敏',
    ADD CONSTRAINT uk_patient_identity_card UNIQUE (identity_card),
    ADD INDEX idx_patient_name (name),
    ADD INDEX idx_patient_phone (phone);

CREATE TABLE doctor_schedule (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '排班ID',
    doctor_id BIGINT NOT NULL COMMENT '出诊医生',
    work_date DATE NOT NULL COMMENT '出诊日期',
    time_slot VARCHAR(16) NOT NULL COMMENT 'MORNING/AFTERNOON/EVENING',
    start_time TIME NOT NULL COMMENT '时段开始，本设计不跨日',
    end_time TIME NOT NULL COMMENT '时段结束',
    total_capacity INT NOT NULL COMMENT '该时段总号源',
    remaining_capacity INT NOT NULL COMMENT '当前剩余号源，事务内条件扣减',
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN可预约/CLOSED停诊',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_schedule_doctor_date_slot UNIQUE (doctor_id, work_date, time_slot),
    CONSTRAINT uk_schedule_id_doctor UNIQUE (id, doctor_id),
    INDEX idx_schedule_date_status (work_date, status),
    CONSTRAINT fk_schedule_doctor FOREIGN KEY (doctor_id) REFERENCES doctor(id),
    CONSTRAINT chk_schedule_slot CHECK (time_slot IN ('MORNING', 'AFTERNOON', 'EVENING')),
    CONSTRAINT chk_schedule_time CHECK (start_time < end_time),
    CONSTRAINT chk_schedule_capacity CHECK (total_capacity >= 0 AND remaining_capacity >= 0 AND remaining_capacity <= total_capacity),
    CONSTRAINT chk_schedule_status CHECK (status IN ('OPEN', 'CLOSED')),
    CONSTRAINT chk_schedule_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='医生按日期和时段排班';

ALTER TABLE appointment
    ADD COLUMN schedule_id BIGINT NULL COMMENT '排班ID，NULL仅兼容迁移前挂号',
    ADD COLUMN active_schedule_id BIGINT GENERATED ALWAYS AS (
        CASE WHEN status IN ('PENDING', 'COMPLETED') THEN schedule_id ELSE NULL END
    ) STORED COMMENT '有效占号键，取消后释放，禁止手动写入',
    ADD INDEX idx_appointment_schedule_doctor (schedule_id, doctor_id),
    ADD CONSTRAINT uk_appointment_patient_active_schedule UNIQUE (patient_id, active_schedule_id),
    ADD CONSTRAINT fk_appointment_schedule_doctor FOREIGN KEY (schedule_id, doctor_id)
        REFERENCES doctor_schedule(id, doctor_id);

CREATE TABLE medicine (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(32) NOT NULL COMMENT '药品唯一编码',
    name VARCHAR(128) NOT NULL COMMENT '药品名称',
    specification VARCHAR(128) NOT NULL COMMENT '规格',
    unit VARCHAR(16) NOT NULL COMMENT '最小库存及发药计量单位，例如盒或片',
    manufacturer VARCHAR(128) NULL COMMENT '生产厂家',
    unit_price DECIMAL(12,2) NOT NULL COMMENT '当前每库存单位价格，禁止浮点金额',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '1可用/0停用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_medicine_code UNIQUE (code),
    INDEX idx_medicine_name (name),
    CONSTRAINT chk_medicine_price CHECK (unit_price >= 0),
    CONSTRAINT chk_medicine_enabled CHECK (enabled IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='药品目录，库存使用整数最小单位';

CREATE TABLE medicine_inventory (
    medicine_id BIGINT NOT NULL COMMENT '药品ID，同时作为主键，单库房教学模型',
    stock_quantity INT NOT NULL DEFAULT 0 COMMENT '可用库存',
    warning_quantity INT NOT NULL DEFAULT 0 COMMENT '低库存预警阈值',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (medicine_id),
    CONSTRAINT fk_inventory_medicine FOREIGN KEY (medicine_id) REFERENCES medicine(id),
    CONSTRAINT chk_inventory_quantity CHECK (stock_quantity >= 0 AND warning_quantity >= 0),
    CONSTRAINT chk_inventory_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='每种药品一条库存记录，不存药品名称价格';

CREATE TABLE prescription (
    id BIGINT NOT NULL AUTO_INCREMENT,
    prescription_no VARCHAR(40) NOT NULL COMMENT '处方业务编号',
    appointment_id BIGINT NOT NULL COMMENT '就诊挂号，患者与医生通过挂号关联取得',
    diagnosis VARCHAR(500) NULL COMMENT '诊断摘要，后续须按权限隔离',
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT草稿/ISSUED已开立/CANCELLED已作废',
    issued_at DATETIME NULL COMMENT '开立时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_prescription_no UNIQUE (prescription_no),
    CONSTRAINT uk_prescription_appointment UNIQUE (appointment_id),
    INDEX idx_prescription_status_created (status, created_at),
    CONSTRAINT fk_prescription_appointment FOREIGN KEY (appointment_id) REFERENCES appointment(id),
    CONSTRAINT chk_prescription_status CHECK (status IN ('DRAFT', 'ISSUED', 'CANCELLED')),
    CONSTRAINT chk_prescription_issued_time CHECK (status <> 'ISSUED' OR issued_at IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='单次挂号至多一张处方的课程模型';

CREATE TABLE prescription_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    prescription_id BIGINT NOT NULL,
    line_no SMALLINT NOT NULL COMMENT '处方内行号',
    medicine_id BIGINT NOT NULL,
    quantity INT NOT NULL COMMENT '发药数量，与medicine.unit一致',
    unit_price DECIMAL(12,2) NOT NULL COMMENT '开立时成交单价快照，后续目录调价不修改',
    instructions VARCHAR(255) NOT NULL COMMENT '用法说明，由医生业务输入',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_prescription_item_line UNIQUE (prescription_id, line_no),
    INDEX idx_prescription_item_medicine (medicine_id),
    CONSTRAINT fk_item_prescription FOREIGN KEY (prescription_id) REFERENCES prescription(id),
    CONSTRAINT fk_item_medicine FOREIGN KEY (medicine_id) REFERENCES medicine(id),
    CONSTRAINT chk_item_line CHECK (line_no > 0),
    CONSTRAINT chk_item_quantity CHECK (quantity > 0),
    CONSTRAINT chk_item_price CHECK (unit_price >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='处方明细，金额由数量乘历史单价计算';

CREATE TABLE inventory_movement (
    id BIGINT NOT NULL AUTO_INCREMENT,
    business_key VARCHAR(64) NOT NULL COMMENT '幂等键，例如处方明细发药操作的唯一标识',
    medicine_id BIGINT NOT NULL,
    change_quantity INT NOT NULL COMMENT '入库正数、出库负数，不得为零',
    balance_after INT NOT NULL COMMENT '该次变动后余额历史快照',
    reason VARCHAR(128) NOT NULL COMMENT '入库/发药/退药/盘点等操作原因',
    reference_no VARCHAR(64) NULL COMMENT '关联业务单据号，仅追踪标识，不作为外键',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_inventory_movement_business UNIQUE (business_key),
    INDEX idx_movement_medicine_created (medicine_id, created_at),
    CONSTRAINT fk_movement_inventory FOREIGN KEY (medicine_id) REFERENCES medicine_inventory(medicine_id),
    CONSTRAINT chk_movement_change CHECK (change_quantity <> 0),
    CONSTRAINT chk_movement_balance CHECK (balance_after >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='不可变库存流水，余额为历史审计快照';
