package com.example.payment.service;

import com.example.payment.dto.TransactionDtos.*;
import com.example.payment.exception.ApiException;
import com.example.payment.repository.FinancialRepository;
import com.example.payment.repository.FinancialRepository.AccountRow;
import com.example.payment.repository.FinancialRepository.SavedResult;
import com.example.payment.security.AuthPrincipal;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.*;
import static com.example.payment.enums.IdempotencyOperation.*;

@Service
public class FinancialService {
    private final FinancialRepository repository;
    private final ObjectMapper json;
    private final TransactionTemplate write;
    private final TransactionTemplate read;

    public FinancialService(FinancialRepository repository, ObjectMapper json, PlatformTransactionManager manager) {
        this.repository = repository;
        this.json = json;
        write = new TransactionTemplate(manager);
        write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        read = new TransactionTemplate(manager);
        read.setReadOnly(true);
    }

    public TransactionView deposit(Deposit input, AuthPrincipal principal) {
        return write.execute(status -> {
            repository.financialTimeout();
            owned(account(input.accountId(), false), principal);
            BigDecimal amount = Money.amount(input.amount());
            var target = account(input.accountId(), true);
            repository.balance(target.id(), Money.credit(target.balance(), amount));
            return transaction(repository.insert("DEPOSIT", null, target.id(), amount, null));
        });
    }

    public Preview preview(Transfer input, AuthPrincipal principal) {
        return read.execute(status -> {
            var source = owned(account(input.sourceAccountId(), false), principal);
            var destination = destination(input.destinationAccountNumber());
            var amount = Money.amount(input.amount());
            validateTransfer(source, destination, amount);
            return new Preview(source.id(), source.number(), destination.number(), destination.displayName(), amount,
                "VND", description(input, source));
        });
    }

    public WithdrawalResult withdraw(Withdrawal input, String key, AuthPrincipal principal) {
        try {
            return write.execute(status -> executeWithdrawal(input, key, principal));
        } catch (DataIntegrityViolationException failure) {
            if (!isKeyCollision(failure)) throw failure;
            // The losing attempt (including debit/result) has fully rolled back.
            return read.execute(status -> {
                owned(account(input.accountId(), false), principal);
                String identity = withdrawalFingerprint(input.accountId(), Money.amount(input.amount()));
                var saved = repository.saved(WITHDRAWAL, key)
                    .orElseThrow(() -> new ApiException(409, "CONCURRENCY_CONFLICT", "Concurrent withdrawal; retry later"));
                return new WithdrawalResult(replay(saved, input.accountId(), identity).transaction(), true);
            });
        }
    }

    private WithdrawalResult executeWithdrawal(Withdrawal input, String key, AuthPrincipal principal) {
        repository.financialTimeout();
        owned(account(input.accountId(), false), principal);
        validateKey(key);
        BigDecimal amount = Money.amount(input.amount());
        String identity = withdrawalFingerprint(input.accountId(), amount);
        var completed = repository.saved(WITHDRAWAL, key);
        if (completed.isPresent())
            return new WithdrawalResult(replay(completed.get(), input.accountId(), identity).transaction(), true);

        var source = owned(account(input.accountId(), true), principal);
        completed = repository.saved(WITHDRAWAL, key);
        if (completed.isPresent())
            return new WithdrawalResult(replay(completed.get(), input.accountId(), identity).transaction(), true);
        repository.balance(source.id(), Money.debit(source.balance(), amount));
        UUID id = repository.insert("WITHDRAWAL", source.id(), null, amount, null);
        repository.saveKey(WITHDRAWAL, key, identity, id);
        return new WithdrawalResult(transaction(id), false);
    }

    public TransferResult transfer(Transfer input, String key, AuthPrincipal principal) {
        // The coordinator catches commit/unique failures only AFTER the entire attempt rolls back.
        try {
            return write.execute(status -> executeTransfer(input, key, principal));
        } catch (DataIntegrityViolationException failure) {
            if (!isKeyCollision(failure)) throw failure;
            return read.execute(status -> {
                owned(account(input.sourceAccountId(), false), principal);
                String identity = fingerprint(input, destination(input.destinationAccountNumber()).id(), Money.amount(input.amount()));
                return repository.saved(TRANSFER, key).map(saved -> replay(saved, input.sourceAccountId(), identity))
                    .orElseThrow(() -> new ApiException(409, "CONCURRENCY_CONFLICT", "Concurrent transfer; retry later"));
            });
        }
    }

    private TransferResult executeTransfer(Transfer input, String key, AuthPrincipal principal) {
        repository.financialTimeout();
        owned(account(input.sourceAccountId(), false), principal);
        validateKey(key);
        var destination = destination(input.destinationAccountNumber());
        BigDecimal amount = Money.amount(input.amount());
        String identity = fingerprint(input, destination.id(), amount);
        var completed = repository.saved(TRANSFER, key);
        if (completed.isPresent()) return replay(completed.get(), input.sourceAccountId(), identity);

        var ids = new ArrayList<>(Set.of(input.sourceAccountId()));
        if (!destination.id().equals(input.sourceAccountId())) ids.add(destination.id());
        ids.sort(Comparator.comparing(UUID::toString));
        var locked = new HashMap<UUID, AccountRow>();
        for (UUID id : ids) locked.put(id, account(id, true));
        completed = repository.saved(TRANSFER, key); // READ_COMMITTED sees a winner that held these account locks.
        if (completed.isPresent()) return replay(completed.get(), input.sourceAccountId(), identity);
        var source = owned(locked.get(input.sourceAccountId()), principal);
        destination = locked.get(destination.id());
        validateTransfer(source, destination, amount);
        repository.balance(source.id(), Money.debit(source.balance(), amount));
        repository.balance(destination.id(), Money.credit(destination.balance(), amount));
        UUID id = repository.insert("TRANSFER", source.id(), destination.id(), amount, description(input, source));
        repository.saveKey(TRANSFER, key, identity, id);
        return new TransferResult(transaction(id), false);
    }

    private TransferResult replay(SavedResult saved, UUID source, String identity) {
        // Global keys must never disclose a transaction from another source, even on a hash collision.
        if (!source.equals(saved.sourceId()) || !identity.equals(saved.fingerprint()))
            throw new ApiException(409, "IDEMPOTENCY_KEY_REUSED", "Key already used for a different request");
        return new TransferResult(transaction(saved.transactionId()), true);
    }

    public TransactionView detail(UUID id, AuthPrincipal principal) {
        return read.execute(status -> {
            if (principal == null) throw ApiException.authentication();
            var result = transaction(id);
            boolean allowed = result.sourceAccountId() != null && account(result.sourceAccountId(), false).owner().equals(principal.userId());
            allowed |= result.destinationAccountId() != null && account(result.destinationAccountId(), false).owner().equals(principal.userId());
            if (!allowed) throw denied();
            return result;
        });
    }

    public TransactionPage history(UUID id, int page, int size, AuthPrincipal principal) {
        return read.execute(status -> {
            owned(account(id, false), principal);
            if (page < 0 || size < 1 || size > 100) throw new ApiException(400, "INVALID_REQUEST", "Invalid history pagination");
            long count = repository.count(id);
            return new TransactionPage(repository.history(id, size, (long) page * size), page, size, count,
                (int) ((count + size - 1) / size));
        });
    }

    private AccountRow account(UUID id, boolean lock) { return repository.account(id, lock).orElseThrow(FinancialService::missingAccount); }
    private AccountRow destination(String number) {
        if (number == null || !number.matches("[0-9]{12}")) throw new ApiException(400, "INVALID_REQUEST", "Invalid account number");
        return repository.recipient(number).orElseThrow(FinancialService::missingAccount);
    }
    private TransactionView transaction(UUID id) {
        return repository.transaction(id).orElseThrow(() -> new ApiException(404, "TRANSACTION_NOT_FOUND", "Transaction not found"));
    }
    private static AccountRow owned(AccountRow account, AuthPrincipal principal) {
        if (principal == null) throw ApiException.authentication();
        if (!account.owner().equals(principal.userId())) throw denied();
        return account;
    }
    private static void validateTransfer(AccountRow source, AccountRow destination, BigDecimal amount) {
        if (source.id().equals(destination.id())) throw new ApiException(422, "SAME_ACCOUNT_TRANSFER", "Source and destination must differ");
        Money.debit(source.balance(), amount);
        Money.credit(destination.balance(), amount);
    }
    private static String description(Transfer input, AccountRow source) {
        return input.description() == null ? source.displayName() + " đã chuyển khoản" : input.description();
    }
    public static void validateKey(String key) {
        if (key == null || !key.matches("[!-~]{1,128}")) throw new ApiException(400, "INVALID_REQUEST", "Invalid Idempotency-Key");
    }
    private String fingerprint(Transfer input, UUID destination, BigDecimal amount) {
        try {
            byte[] canonical = json.writeValueAsBytes(Arrays.asList(input.sourceAccountId().toString(), destination.toString(),
                amount.toPlainString(), input.description()));
            return "v2:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (JsonProcessingException | NoSuchAlgorithmException ex) { throw new IllegalStateException("Cannot encode transfer identity", ex); }
    }
    private static boolean isKeyCollision(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && "23505".equals(sql.getSQLState())
                && sql.getMessage().contains("\"uq_idempotency_operation_key\"")) return true;
        }
        return false;
    }
    private String withdrawalFingerprint(UUID account, BigDecimal amount) {
        try {
            byte[] canonical = json.writeValueAsBytes(List.of(account.toString(), amount.toPlainString()));
            return "w1:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (JsonProcessingException | NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Cannot encode withdrawal identity", ex);
        }
    }
    private static ApiException missingAccount() { return new ApiException(404, "ACCOUNT_NOT_FOUND", "Account not found"); }
    private static ApiException denied() { return new ApiException(403, "ACCESS_DENIED", "Access denied"); }
}
