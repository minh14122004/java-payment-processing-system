package com.example.payment.repository;

import com.example.payment.dto.TransactionDtos.TransactionView;
import com.example.payment.enums.IdempotencyOperation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** JDBC shares the service transaction managed by JpaTransactionManager.
 * Explicit balance-only SQL avoids stale JPA state overwriting account labels. */
@Repository
public class FinancialRepository {
    public record AccountRow(UUID id, UUID owner, String number, String displayName, BigDecimal balance) {}
    public record SavedResult(String fingerprint, UUID sourceId, UUID transactionId) {}
    private final JdbcTemplate jdbc;
    public FinancialRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static final String ACCOUNT = """
        SELECT a.id, a.user_id, a.account_number, u.display_name, a.balance
        FROM accounts a JOIN users u ON u.id = a.user_id
        """;
    private static final RowMapper<AccountRow> ACCOUNT_ROW = (rs, n) -> new AccountRow(
        rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class),
        rs.getString("account_number"), rs.getString("display_name"), rs.getBigDecimal("balance"));

    public Optional<AccountRow> account(UUID id, boolean lock) {
        return jdbc.query(ACCOUNT + " WHERE a.id = ?" + (lock ? " FOR UPDATE OF a" : ""), ACCOUNT_ROW, id).stream().findFirst();
    }
    public Optional<AccountRow> recipient(String number) {
        return jdbc.query(ACCOUNT + " WHERE a.account_number = ?", ACCOUNT_ROW, number).stream().findFirst();
    }
    public void financialTimeout() { jdbc.execute("SET LOCAL lock_timeout = '5s'"); }
    public void balance(UUID id, BigDecimal value) {
        if (jdbc.update("UPDATE accounts SET balance = ? WHERE id = ?", value, id) != 1)
            throw new IllegalStateException("Locked account disappeared");
    }

    public UUID insert(String type, UUID source, UUID destination, BigDecimal amount, String description) {
        UUID id = UUID.randomUUID();
        var now = OffsetDateTime.ofInstant(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS), ZoneOffset.UTC);
        jdbc.update("""
            INSERT INTO payment_transactions(id,type,source_account_id,destination_account_id,amount,currency,status,created_at,description)
            VALUES (?, ?, ?, ?, ?, 'VND', 'SUCCESS', ?, ?)
            """, id, type, source, destination, amount, now, description);
        return id;
    }
    public Optional<SavedResult> saved(IdempotencyOperation operation, String key) {
        return jdbc.query("""
            SELECT r.request_fingerprint, t.source_account_id, r.transaction_id
            FROM idempotency_records r JOIN payment_transactions t ON t.id = r.transaction_id
            WHERE r.operation = ? AND r.idempotency_key = ?
            """, (rs, n) -> new SavedResult(rs.getString(1), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class)), operation.name(), key)
            .stream().findFirst();
    }
    public void saveKey(IdempotencyOperation operation, String key, String fingerprint, UUID transaction) {
        jdbc.update("INSERT INTO idempotency_records(id,operation,idempotency_key,request_fingerprint,transaction_id) VALUES (?,?,?,?,?)",
            UUID.randomUUID(), operation.name(), key, fingerprint, transaction);
    }

    private static final String TRANSACTION = """
        SELECT t.*, s.account_number AS source_number, d.account_number AS destination_number
        FROM payment_transactions t
        LEFT JOIN accounts s ON s.id = t.source_account_id
        LEFT JOIN accounts d ON d.id = t.destination_account_id
        """;
    private static final RowMapper<TransactionView> TRANSACTION_ROW = (rs, n) -> new TransactionView(
        rs.getObject("id", UUID.class), rs.getString("type"), rs.getObject("source_account_id", UUID.class),
        rs.getObject("destination_account_id", UUID.class), rs.getString("source_number"), rs.getString("destination_number"),
        rs.getBigDecimal("amount"), rs.getString("currency"), rs.getString("status"),
        rs.getTimestamp("created_at").toInstant(), rs.getString("description"));
    public Optional<TransactionView> transaction(UUID id) {
        return jdbc.query(TRANSACTION + " WHERE t.id = ?", TRANSACTION_ROW, id).stream().findFirst();
    }
    public long count(UUID account) {
        return jdbc.queryForObject("SELECT count(*) FROM payment_transactions WHERE source_account_id = ? OR destination_account_id = ?",
            Long.class, account, account);
    }
    public List<TransactionView> history(UUID account, int size, long offset) {
        return jdbc.query(TRANSACTION + " WHERE t.source_account_id = ? OR t.destination_account_id = ? ORDER BY t.created_at DESC, t.id DESC LIMIT ? OFFSET ?",
            TRANSACTION_ROW, account, account, size, offset);
    }
}
