package com.example.payment.auth;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class AccountMigrationTest {
    @Test void upgradeNamesLegacyAccountsPerOwnerAndPreservesEveryExistingColumnAndHistory() throws Exception {
        var db = PostgresFixture.DATABASE;
        String database = "migration_" + UUID.randomUUID().toString().replace("-", "");
        try (var admin = DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword());
             var statement = admin.createStatement()) {
            statement.execute("CREATE DATABASE " + database);
        }
        String url = db.getJdbcUrl().replace("/" + db.getDatabaseName(), "/" + database);
        try (var connection = DriverManager.getConnection(url, db.getUsername(), db.getPassword());
             var sql = connection.createStatement()) {
            sql.execute(Files.readString(Path.of("database/001_initial_schema.sql")));
            sql.execute(Files.readString(Path.of("database/002_auth_password_reset.sql")));
            sql.execute("""
                INSERT INTO users(id,email,display_name,password_hash,email_verified_at) VALUES
                ('10000000-0000-0000-0000-000000000001','one@example.test','One','hash',now()),
                ('10000000-0000-0000-0000-000000000002','two@example.test','Two','hash',now());
                INSERT INTO accounts(id,user_id,account_holder_name,balance,created_at) VALUES
                ('20000000-0000-0000-0000-000000000003','10000000-0000-0000-0000-000000000001','Third',300,'2026-02-01Z'),
                ('20000000-0000-0000-0000-000000000002','10000000-0000-0000-0000-000000000001','Second',200,'2026-01-01Z'),
                ('20000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000001','First',100,'2026-01-01Z'),
                ('20000000-0000-0000-0000-000000000004','10000000-0000-0000-0000-000000000002','Other',400,'2026-01-01Z');
                INSERT INTO payment_transactions(id,type,destination_account_id,amount)
                VALUES ('30000000-0000-0000-0000-000000000001','DEPOSIT','20000000-0000-0000-0000-000000000001',100);
                INSERT INTO idempotency_records(idempotency_key,request_fingerprint,transaction_id)
                VALUES ('migration-test','fixture','30000000-0000-0000-0000-000000000001');
                INSERT INTO email_otp_challenges(email,purpose) VALUES ('pending@example.test','REGISTRATION');
                """);
            String accountColumns = "select id,user_id,account_holder_name,balance,currency,created_at from accounts order by id";
            var before = rows(sql, accountColumns);
            var tables = List.of("users", "payment_transactions", "idempotency_records", "email_otp_challenges");
            var snapshots = new HashMap<String, List<List<String>>>();
            for (String table : tables) snapshots.put(table, rows(sql, "select * from " + table + " order by id"));
            sql.execute(Files.readString(Path.of("database/003_account_names.sql")));
            assertThat(rows(sql, accountColumns)).isEqualTo(before);
            for (String table : tables) assertThat(rows(sql, "select * from " + table + " order by id")).isEqualTo(snapshots.get(table));
            assertThat(rows(sql, "select account_name from accounts order by id")).containsExactly(
                    List.of("tài khoản thanh toán 1"), List.of("tài khoản thanh toán 2"),
                    List.of("tài khoản thanh toán 3"), List.of("tài khoản thanh toán 1"));
            reject(sql, "update accounts set account_name = null", "23502");
            reject(sql, "update accounts set account_name = '   '", "23514");
            reject(sql, "update accounts set account_name = repeat('a',101)", "22001");
            sql.execute("update accounts set account_name = 'Duplicated label'");
            assertThat(rows(sql, "select distinct account_name from accounts")).containsExactly(List.of("Duplicated label"));
        }
    }

    @Test void freshSchemaPassesFullSqlVerificationWithAllFixturesRolledBack() throws Exception {
        var db = PostgresFixture.DATABASE;
        try (var connection = DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword());
             var sql = connection.createStatement()) {
            var before = rows(sql, "select id,account_name,balance from accounts order by id");
            sql.execute(Files.readString(Path.of("database/verify_schema.sql")));
            assertThat(rows(sql, "select id,account_name,balance from accounts order by id")).isEqualTo(before);
        }
    }

    private static void reject(Statement sql, String query, String state) {
        assertThatThrownBy(() -> sql.execute(query)).isInstanceOf(SQLException.class)
                .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo(state));
    }
    private static List<List<String>> rows(Statement sql, String query) throws SQLException {
        var result = new ArrayList<List<String>>();
        try (var rows = sql.executeQuery(query)) {
            while (rows.next()) {
                var row = new ArrayList<String>();
                for (int i = 1; i <= rows.getMetaData().getColumnCount(); i++) row.add(rows.getString(i));
                result.add(row);
            }
        }
        return result;
    }
}
