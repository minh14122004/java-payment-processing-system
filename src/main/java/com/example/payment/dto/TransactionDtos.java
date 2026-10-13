package com.example.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class TransactionDtos {
    private TransactionDtos() {}

    public record Deposit(@NotNull UUID accountId,
                          @NotNull @Schema(example = "200000", description = "Positive whole VND, at most 9999999999999999999") BigDecimal amount) {}

    public record Transfer(@NotNull UUID sourceAccountId,
                           @NotNull @Pattern(regexp = "[0-9]{12}") @Schema(example = "100000000001") String destinationAccountNumber,
                           @NotNull @Schema(example = "100000") BigDecimal amount,
                           @Size(max = 255) @Schema(description = "Editable before submission. Missing/null/blank uses the sender's displayName + ' đã chuyển khoản'.") String description) {
        public Transfer {
            description = description == null || description.isBlank() ? null : description.strip();
        }
    }

    public record Preview(UUID sourceAccountId, String sourceAccountNumber, String destinationAccountNumber,
                          String recipientName, BigDecimal amount, String currency, String description) {}

    public record TransactionView(UUID id, String type, UUID sourceAccountId, UUID destinationAccountId,
                                  String sourceAccountNumber, String destinationAccountNumber, BigDecimal amount,
                                  String currency, String status, Instant createdAt, String description) {}

    public record TransactionPage(List<TransactionView> items, int page, int size, long totalElements, int totalPages) {}
    public record TransferResult(TransactionView transaction, boolean replay) {}
}
