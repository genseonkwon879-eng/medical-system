-- 管理员手动替换口令后执行；请勿将真实口令提交到仓库。
-- 与 application-mysql.yml 默认 DB_HOST=127.0.0.1 对应。
CREATE USER IF NOT EXISTS 'medical_app'@'127.0.0.1'
    IDENTIFIED BY 'CHANGE_ME_before_execution_2026!';
GRANT SELECT, INSERT, UPDATE, DELETE ON medical_system.* TO 'medical_app'@'127.0.0.1';
-- 若用户已存在，CREATE USER IF NOT EXISTS 不会重设其密码。
