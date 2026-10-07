package com.example.payment.auth;

import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import java.sql.DriverManager;

public final class PostgresFixture {
    public static final PostgreSQLContainer<?> DATABASE = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("auth_test").withUsername("auth_test").withPassword("test-only-password");
    static {
        DATABASE.start();
        try (var connection = DriverManager.getConnection(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword())) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource("database/001_initial_schema.sql"));
            ScriptUtils.executeSqlScript(connection, new FileSystemResource("database/002_auth_password_reset.sql"));
        } catch (Exception ex) { throw new ExceptionInInitializerError(ex); }
    }
    private PostgresFixture() {}
}
