package com.example.payment.auth;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class FinancialMigrationTest {
    @Test void operationNamespaceUpgradePreservesAllColumnsAndReplaysOldTransfer() throws Exception {
        var db=PostgresFixture.DATABASE;
        String name="operation_migration_"+UUID.randomUUID().toString().replace("-","");
        try(var admin=DriverManager.getConnection(db.getJdbcUrl(),db.getUsername(),db.getPassword());var sql=admin.createStatement()) {
            sql.execute("CREATE DATABASE "+name);
        }
        String url=db.getJdbcUrl().replace("/auth_test","/"+name);
        try(var connection=DriverManager.getConnection(url,db.getUsername(),db.getPassword());var sql=connection.createStatement()) {
            for(String script:List.of("001_initial_schema.sql","002_auth_password_reset.sql","003_account_names.sql","004_account_numbers_and_transfer_descriptions.sql"))
                sql.execute(Files.readString(Path.of("database",script)));
            UUID user=UUID.randomUUID(), source=UUID.randomUUID(), target=UUID.randomUUID(), transaction=UUID.randomUUID();
            var json=new com.fasterxml.jackson.databind.ObjectMapper();
            String fingerprint="v2:"+HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(json.writeValueAsBytes(Arrays.asList(source.toString(),target.toString(),"10",null))));
            try(var insert=connection.prepareStatement("INSERT INTO users(id,email,display_name,password_hash,email_verified_at) VALUES (?,'upgrade@example.test','Upgrade','fixture',now())")) {
                insert.setObject(1,user); insert.executeUpdate();
            }
            try(var insert=connection.prepareStatement("INSERT INTO accounts(id,user_id,account_holder_name,account_name,balance) VALUES (?,?,'Holder','Label',?)")) {
                insert.setObject(1,source); insert.setObject(2,user); insert.setInt(3,90); insert.executeUpdate();
                insert.setObject(1,target); insert.setInt(3,10); insert.executeUpdate();
            }
            try(var insert=connection.prepareStatement("INSERT INTO payment_transactions(id,type,source_account_id,destination_account_id,amount,description) VALUES (?,'TRANSFER',?,?,10,'Original message')")) {
                insert.setObject(1,transaction); insert.setObject(2,source); insert.setObject(3,target); insert.executeUpdate();
            }
            try(var insert=connection.prepareStatement("INSERT INTO idempotency_records(idempotency_key,request_fingerprint,transaction_id) VALUES ('preserved',?,?)")) {
                insert.setString(1,fingerprint); insert.setObject(2,transaction); insert.executeUpdate();
            }
            sql.execute("INSERT INTO email_otp_challenges(email,purpose) VALUES ('pending@example.test','REGISTRATION')");
            var columns=new HashMap<String,String>(); var before=new HashMap<String,List<List<String>>>();
            for(String table:List.of("users","accounts","payment_transactions","idempotency_records","email_otp_challenges")) {
                try(var rs=sql.executeQuery("SELECT * FROM "+table+" LIMIT 0")) {
                    var names=new ArrayList<String>();
                    for(int i=1;i<=rs.getMetaData().getColumnCount();i++) names.add(rs.getMetaData().getColumnName(i));
                    columns.put(table,String.join(",",names));
                }
                before.put(table,rows(sql,"SELECT "+columns.get(table)+" FROM "+table+" ORDER BY id"));
            }
            sql.execute(Files.readString(Path.of("database/005_idempotency_operation_namespaces.sql")));
            for(String table:columns.keySet())
                assertThat(rows(sql,"SELECT "+columns.get(table)+" FROM "+table+" ORDER BY id")).isEqualTo(before.get(table));
            assertThat(rows(sql,"SELECT operation FROM idempotency_records")).containsExactly(List.of("TRANSFER"));
            var dataSource=new org.springframework.jdbc.datasource.DriverManagerDataSource(url,db.getUsername(),db.getPassword());
            var jdbc=new org.springframework.jdbc.core.JdbcTemplate(dataSource);
            var service=new com.example.payment.service.FinancialService(new com.example.payment.repository.FinancialRepository(jdbc),json,
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(dataSource));
            String number=jdbc.queryForObject("SELECT account_number FROM accounts WHERE id=?",String.class,target);
            var replay=service.transfer(new com.example.payment.dto.TransactionDtos.Transfer(source,number,new java.math.BigDecimal("10.0"),null),
                "preserved",new com.example.payment.security.AuthPrincipal(user,0));
            assertThat(replay.replay()).isTrue(); assertThat(replay.transaction().id()).isEqualTo(transaction);
            assertThat(replay.transaction().description()).isEqualTo("Original message");
            for(String table:columns.keySet())
                assertThat(rows(sql,"SELECT "+columns.get(table)+" FROM "+table+" ORDER BY id")).isEqualTo(before.get(table));
            reject(sql,"UPDATE idempotency_records SET operation=NULL","23502");
            reject(sql,"UPDATE idempotency_records SET operation='DEPOSIT'","23514");
            reject(sql,"INSERT INTO idempotency_records(operation,idempotency_key,request_fingerprint,transaction_id) SELECT operation,idempotency_key,request_fingerprint,transaction_id FROM idempotency_records","23505");
            reject(sql,"INSERT INTO idempotency_records(operation,idempotency_key,request_fingerprint,transaction_id) SELECT 'WITHDRAWAL',idempotency_key,request_fingerprint,transaction_id FROM idempotency_records","23505");
            reject(sql,"INSERT INTO idempotency_records(operation,idempotency_key,request_fingerprint,transaction_id) VALUES ('WITHDRAWAL','unknown','test',gen_random_uuid())","23503");
            sql.execute(Files.readString(Path.of("database/verify_schema.sql")));
            for(String table:columns.keySet())
                assertThat(rows(sql,"SELECT "+columns.get(table)+" FROM "+table+" ORDER BY id")).isEqualTo(before.get(table));
        }
    }
    @Test void upgradesPopulatedSchemaDeterministicallyAndPreservesEveryExistingColumn() throws Exception {
        var db=PostgresFixture.DATABASE;
        String name="number_migration_"+UUID.randomUUID().toString().replace("-","");
        try(var admin=DriverManager.getConnection(db.getJdbcUrl(),db.getUsername(),db.getPassword());var sql=admin.createStatement()) {
            sql.execute("CREATE DATABASE "+name);
        }
        try(var connection=DriverManager.getConnection(db.getJdbcUrl().replace("/auth_test","/"+name),db.getUsername(),db.getPassword());
                var sql=connection.createStatement()) {
            for(String script:List.of("001_initial_schema.sql","002_auth_password_reset.sql","003_account_names.sql"))
                sql.execute(Files.readString(Path.of("database",script)));
            sql.execute("""
                INSERT INTO users(id,email,display_name,password_hash,email_verified_at) VALUES
                ('10000000-0000-0000-0000-000000000001','one@example.test','Name','fixture',now());
                INSERT INTO accounts(id,user_id,account_holder_name,account_name,balance,created_at) VALUES
                ('20000000-0000-0000-0000-000000000002','10000000-0000-0000-0000-000000000001','Holder','Two',200,'2026-01-01Z'),
                ('20000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000001','Holder','One',100,'2026-01-01Z'),
                ('20000000-0000-0000-0000-000000000003','10000000-0000-0000-0000-000000000001','Holder','Three',300,'2026-02-01Z');
                INSERT INTO payment_transactions(id,type,source_account_id,destination_account_id,amount) VALUES
                ('30000000-0000-0000-0000-000000000001','TRANSFER','20000000-0000-0000-0000-000000000001',
                  '20000000-0000-0000-0000-000000000002',10);
                """);
            sql.execute("""
                INSERT INTO idempotency_records(idempotency_key,request_fingerprint,transaction_id)
                VALUES ('preserved','v1|legacy','30000000-0000-0000-0000-000000000001');
                INSERT INTO email_otp_challenges(email,purpose) VALUES ('pending@example.test','REGISTRATION');
                """);
            var tables=List.of("users","accounts","payment_transactions","idempotency_records","email_otp_challenges");
            var columns=new HashMap<String,String>(); var before=new HashMap<String,List<List<String>>>();
            for(String table:tables) {
                try(var rs=sql.executeQuery("SELECT * FROM "+table+" LIMIT 0")) {
                    var names=new ArrayList<String>();
                    for(int i=1;i<=rs.getMetaData().getColumnCount();i++) names.add(rs.getMetaData().getColumnName(i));
                    columns.put(table,String.join(",",names));
                }
                before.put(table,rows(sql,"SELECT "+columns.get(table)+" FROM "+table+" ORDER BY id"));
            }
            sql.execute(Files.readString(Path.of("database/004_account_numbers_and_transfer_descriptions.sql")));
            for(String table:tables) assertThat(rows(sql,"SELECT "+columns.get(table)+" FROM "+table+" ORDER BY id")).isEqualTo(before.get(table));
            assertThat(rows(sql,"SELECT account_number FROM accounts ORDER BY id")).containsExactly(
                List.of("100000000000"),List.of("100000000001"),List.of("100000000002"));
            assertThat(rows(sql,"SELECT nextval('account_number_seq')::text")).containsExactly(List.of("100000000003"));
            assertThat(rows(sql,"SELECT description FROM payment_transactions").getFirst().getFirst()).isNull();
            reject(sql,"UPDATE accounts SET account_number=NULL","23502");
            reject(sql,"UPDATE accounts SET account_number='123'","23514");
            reject(sql,"UPDATE accounts SET account_number='１２３４５６７８９０１２'","23514");
            reject(sql,"UPDATE accounts SET account_number='100000000000'","23505");
            reject(sql,"UPDATE payment_transactions SET description=repeat('x',256)","22001");
            assertThat(rows(sql,"SELECT cycle_option FROM information_schema.sequences WHERE sequence_name='account_number_seq'"))
                .containsExactly(List.of("NO"));
        }
    }
    private static List<List<String>> rows(Statement sql,String query) throws Exception {
        var result=new ArrayList<List<String>>();
        try(var rs=sql.executeQuery(query)) {
            while(rs.next()) {
                var row=new ArrayList<String>();
                for(int i=1;i<=rs.getMetaData().getColumnCount();i++) row.add(rs.getString(i));
                result.add(row);
            }
        }
        return result;
    }
    private static void reject(Statement sql,String query,String state) {
        assertThatThrownBy(() -> sql.execute(query)).isInstanceOf(SQLException.class)
            .satisfies(error -> assertThat(((SQLException)error).getSQLState()).isEqualTo(state));
    }
}
