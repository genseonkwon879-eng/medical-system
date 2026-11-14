import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Properties;
import java.util.UUID;

/** Local setup helper: credentials arrive only on stdin. */
class InitializeMedicalDatabase {
    private static String readField(BufferedReader input) throws Exception {
        String line = input.readLine();
        if (line == null) throw new IllegalArgumentException("Missing input");
        return new String(Base64.getDecoder().decode(line), StandardCharsets.UTF_8);
    }

    public static void main(String[] args) {
        String createdDatabase = "";
        String createdUser = "";
        try {
            var input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
            String host = readField(input);
            int port = Integer.parseInt(readField(input));
            String admin = readField(input);
            String adminPassword = readField(input);
            String appPassword = readField(input);
            String schemaFile = readField(input);
            String resumeDatabase = readField(input);
            if (!(host.equals("127.0.0.1") || host.equals("localhost")) || port < 1 || port > 65535
                    || !appPassword.matches("[A-Za-z0-9_-]{32,100}")
                    || !(resumeDatabase.isEmpty() || resumeDatabase.equals("medical_system"))) {
                throw new IllegalArgumentException("Invalid local setup input");
            }
            String sql = Files.readString(Path.of(schemaFile), StandardCharsets.UTF_8)
                    .replace("\uFEFF", "")
                    .replaceAll("(?m)^\\s*--[^\\r\\n]*", "")
                    .replaceFirst("(?is)CREATE DATABASE IF NOT EXISTS medical_system\\s+CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci\\s*;", "")
                    .replaceFirst("(?is)USE medical_system\\s*;", "");
            if (sql.matches("(?is).*\\b(CREATE\\s+DATABASE|USE|DROP|TRUNCATE|DELETE)\\b.*")) {
                throw new IllegalArgumentException("Schema safety validation failed");
            }
            var properties = new Properties();
            properties.setProperty("user", admin);
            properties.setProperty("password", adminPassword);
            properties.setProperty("characterEncoding", "UTF-8");
            properties.setProperty("connectTimeout", "5000");
            properties.setProperty("socketTimeout", "15000");
            String url = "jdbc:mysql://" + host + ":" + port + "/?serverTimezone=Asia/Shanghai";
            try (Connection connection = DriverManager.getConnection(url, properties)) {
                String database = "medical_system";
                if (!resumeDatabase.isEmpty()) {
                    database = resumeDatabase;
                    try (PreparedStatement check = connection.prepareStatement(
                            "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=?")) {
                        check.setString(1, database);
                        try (ResultSet found = check.executeQuery()) {
                            found.next();
                            if (found.getInt(1) != 0) throw new IllegalStateException("Recovery database is not empty");
                        }
                    }
                } else try (PreparedStatement check = connection.prepareStatement(
                        "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name=?")) {
                    check.setString(1, database);
                    try (ResultSet found = check.executeQuery()) {
                        found.next();
                        if (found.getInt(1) != 0) {
                            database = "medical_lab01_" + LocalDateTime.now().format(
                                    DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                                    + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 6);
                        }
                    }
                }
                String appUser = "medical_lab_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
                String identifierQuote = Character.toString(96);
                try (Statement statement = connection.createStatement()) {
                    // A collision fails rather than editing an existing database.
                    if (resumeDatabase.isEmpty()) {
                        statement.executeUpdate("CREATE DATABASE " + identifierQuote + database + identifierQuote
                                + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
                    }
                    createdDatabase = database;
                }
                connection.setCatalog(database);
                // Create a NEW statement after changing the connection catalog.
                // Connector/J statements retain the catalog active at their creation.
                try (Statement statement = connection.createStatement()) {
                    try (ResultSet selected = statement.executeQuery("SELECT DATABASE()")) {
                        selected.next();
                        if (!database.equals(selected.getString(1))) throw new IllegalStateException("Database selection failed");
                    }
                    for (String command : sql.split(";")) {
                        if (!command.isBlank()) statement.executeUpdate(command.trim());
                    }
                    statement.executeUpdate("CREATE USER '" + appUser
                            + "'@'" + host + "' IDENTIFIED BY '" + appPassword + "'");
                    createdUser = appUser;
                    for (String table : new String[]{"department", "doctor", "patient", "appointment"}) {
                        // Table grants avoid underscore wildcard semantics in database-level grants.
                        statement.executeUpdate("GRANT SELECT, INSERT, UPDATE, DELETE ON " + identifierQuote
                                + database + identifierQuote + "." + identifierQuote + table + identifierQuote
                                + " TO '" + appUser + "'@'" + host + "'");
                    }
                }
                var appProperties = new Properties();
                appProperties.putAll(properties);
                appProperties.setProperty("user", appUser);
                appProperties.setProperty("password", appPassword);
                try (Connection appConnection = DriverManager.getConnection(url, appProperties)) {
                    appConnection.setCatalog(database);
                    try (Statement statement = appConnection.createStatement();
                         ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM department")) {
                        rows.next();
                        if (rows.getInt(1) != 3) throw new IllegalStateException("Unexpected department count");
                    }
                }
                System.out.println("{\"success\":true,\"database\":\"" + database
                        + "\",\"user\":\"" + appUser + "\",\"departmentCount\":3}");
            }
        } catch (Exception exception) {
            String detail = exception instanceof SQLException sqlException
                    ? "sqlCode=" + sqlException.getErrorCode() + ", state=" + sqlException.getSQLState()
                    : exception.getClass().getSimpleName();
            System.err.println("Local database setup failed (" + detail + "). Created database="
                    + createdDatabase + ", created user=" + createdUser + ". Existing data was not edited.");
            System.exit(1);
        }
    }
}
