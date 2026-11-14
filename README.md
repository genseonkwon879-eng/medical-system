# 医疗管理系统 · 实验一初始化框架

本工程已完成“开发环境搭建与医疗系统 Hello World”基础，并新增可操作的科室管理页面和增删改查接口。医生、患者、预约挂号为后续业务扩展。

**本机当前入口：<http://localhost:18082/>。** 首页已升级为科室管理，支持搜索、新增、编辑、删除与输入校验。完整操作、验收与测试方法见 [科室管理实验操作步骤](docs/科室管理实验操作步骤.md)。

实验二数据库扩展设计、ER图、字段说明、增量执行与恢复边界见 [实验二数据库设计与增量迁移](docs/实验二数据库设计与增量迁移.md)。对已有库不要重新执行建库初始化，使用带版本与校验和的V002增量工具；是否已落库以迁移验证结果为准。

完整设计见 [框架设计与实验指导](docs/框架设计与实验指导.md)，报告填写见 [实验报告模板](docs/实验报告模板.md)，交付时的实际验证情况见 [验证记录](docs/验证记录.md)。

当前电脑的独立启动、离线构建、本机口令输入和 MySQL 初始化工具见 [本机启动与联调](docs/本机启动与联调.md)。

本机真实 MySQL 联调已完成，数据库模式访问 <http://localhost:18082/>，已验证数据库连接和科室列表。后续可双击“启动医疗系统.cmd”启动（已在运行时直接访问即可）；本机加密连接配置不包含在源码 ZIP 中。

## 1. 版本约定

| 工具 | 本工程约定 | 说明 |
| --- | --- | --- |
| JDK | 17 | POM 编译目标为 17；命令行、IDEA SDK、Maven 使用同一主版本 |
| Spring Boot | 3.5.7 | 固定的教学复现版本，不代表当前最新版 |
| Maven | 3.9.x | 验证工具为 IDEA 随附的 3.9.6；也可独立安装 |
| MySQL | 8.0 或 8.4 | 使用 InnoDB、utf8mb4；本机检测到客户端 8.0.45，服务端需另行确认 |
| IDEA | 支持 JDK 17 的版本 | 可使用现有 IDEA 2024.1；普通 Maven 项目导入即可 |

## 2. 快速运行：先验证 Web

在含 `pom.xml` 的项目目录执行。以下假设已安装 JDK 17 与 Maven，并加入当前终端的 PATH：

```powershell
java -version
mvn -version
mvn -s config/maven-settings-central.xml clean test package
java -jar target/medical-system-0.0.1-SNAPSHOT.jar
```

首次构建需要联网下载依赖。本工程未附带 Maven Wrapper，`mvn` 需来自独立安装或 IDEA 随附的 Maven。

浏览器访问：

- 首页：<http://localhost:8080/>
- Hello World：<http://localhost:8080/api/hello>
- 项目信息：<http://localhost:8080/api/system/info>

Hello World 预期返回：

```json
{"code":0,"message":"success","data":"医疗系统 Hello World！"}
```

默认 `demo` 模式排除数据源自动配置，因此无需数据库即可启动；此模式不能证明 MySQL 配置成功。数据库检查和科室列表在该模式下返回 404。

### 在当前电脑临时使用 IDEA 自带工具

已检测到下列路径。此配置仅影响当前 PowerShell，不修改系统环境变量：

```powershell
$env:JAVA_HOME = 'D:\IDEA\IntelliJ IDEA 2024.1\jbr'
$env:Path = "$env:JAVA_HOME\bin;D:\IDEA\IntelliJ IDEA 2024.1\plugins\maven\lib\maven3\bin;$env:Path"
java -version
mvn -version
```

长期学习建议独立安装 JDK 17 和 Maven，避免 IDEA 更新、迁移后路径失效。

## 3. MySQL 模式：验收完整环境

1. 确认 MySQL 服务运行，使用管理员账号连接数据库。
2. 执行 `sql/01-schema.sql`，创建数据库、4 张基础表及 3 条科室演示数据。
3. 在 `sql/02-user.example.sql` 中自行替换示例口令，再由管理员执行。该文件是账号配置示例，不要提交真实口令。
4. 设置以下环境变量后启动。

```powershell
$env:DB_HOST = '127.0.0.1'
$env:DB_PORT = '3306'
$env:DB_NAME = 'medical_system'
$env:DB_USER = 'medical_app'
# 在提示框输入你为 medical_app 设置的口令；不写入口令历史。
$dbCredential = Get-Credential -UserName 'medical_app' -Message '输入数据库账号口令'
$env:DB_PASSWORD = $dbCredential.GetNetworkCredential().Password
java -jar target/medical-system-0.0.1-SNAPSHOT.jar --spring.profiles.active=mysql
```

口令通过进程环境变量提供。程序不会自动读取 `.env` 文件。IDEA 运行时在 Run Configuration 的 Environment variables 中设置同名变量，并在程序参数中加入 `--spring.profiles.active=mysql`。

如果使用命令行导入 SQL，先登录：

```powershell
mysql --default-character-set=utf8mb4 -h 127.0.0.1 -P 3306 -u root -p
```

再在 **mysql 提示符**中执行，路径请改为你实际解压的位置：

```sql
SOURCE C:/dev/medical-system/sql/01-schema.sql;
SOURCE C:/dev/medical-system/sql/02-user.example.sql;
SHOW DATABASES;
USE medical_system;
SHOW TABLES;
SELECT * FROM department;
```

也可用 Navicat / MySQL Workbench 打开 SQL 文件执行。账号脚本使用 `127.0.0.1` 作为 Host；修改连接主机后需同步检查 MySQL 账号的 Host 匹配规则。

启动后验证：

| 请求 | 成功依据 |
| --- | --- |
| `GET /api/system/database` | HTTP 200，`data.connected` 为 `true`，实际执行 `SELECT 1` |
| `GET /api/departments` | HTTP 200，返回内科、外科、儿科，证明分层调用、表结构与中文数据正常 |

数据库或表访问失败时返回 HTTP 503 和统一错误信息。测试代码中的数据库故障使用模拟对象，不能替代真实 MySQL 验收。

停止运行后可清除当前终端口令变量：

```powershell
Remove-Item Env:DB_PASSWORD
$dbCredential = $null
```

## 4. 项目目录

```text
medical-system/
├── pom.xml
├── README.md
├── .gitignore / .gitattributes / .editorconfig
├── config/                         Maven 配置示例
├── sql/                            数据库与账号初始化脚本
├── docs/                           框架设计、报告模板、验证记录
└── src/
    ├── main/
    │   ├── java/com/example/medical/
    │   │   ├── MedicalApplication.java
    │   │   ├── controller/         HTTP 请求入口
    │   │   ├── service/            业务规则与事务边界
    │   │   ├── repository/         SQL 与数据库访问
    │   │   ├── entity/             数据实体
    │   │   ├── dto/                预留：请求参数
    │   │   ├── vo/                 预留：组合响应
    │   │   ├── common/             统一响应、异常处理
    │   │   └── config/             预留：公共配置
    │   └── resources/
    │       ├── application.yml
    │       ├── application-demo.yml
    │       ├── application-mysql.yml
    │       └── static/index.html
    └── test/java/com/example/medical/
```

这里使用 Spring JDBC 的 `repository` 层。后续课程若要求 MyBatis，可将该层替换为 `mapper`，并增加 `resources/mapper/*.xml`；不需要同时引入两套持久层框架。

## 5. 提交范围

提交源码、本人真实 Git 仓库地址、已填写报告与实际操作截图。`target/`、IDE 私有配置、真实数据库口令不提交。当前源码包没有替你创建远程仓库，报告模板中的空项须根据实际操作填写。
