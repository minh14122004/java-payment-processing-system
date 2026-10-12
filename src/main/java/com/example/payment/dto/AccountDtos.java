package com.example.payment.dto;

import com.example.payment.dto.AuthDtos.AccountView;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public final class AccountDtos {
    private AccountDtos() {}

    @Schema(name = "AccountNameRequest", description = "Only accountName is client-settable. Names are stripped and duplicates are allowed.")
    public record Name(@NotBlank @Size(max = 100) @Schema(example = "Tiết kiệm") String accountName) {
        public Name { accountName = accountName == null ? null : accountName.strip(); }
    }

    public record AccountPage(List<AccountView> items, int page, int size, long totalElements, int totalPages) {}
}
