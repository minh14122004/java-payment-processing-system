package com.example.payment.controller;

import com.example.payment.dto.AuthDtos.*;
import com.example.payment.dto.AccountDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import io.swagger.v3.oas.annotations.Operation;
import java.net.URI;
import com.example.payment.security.AuthPrincipal;
import com.example.payment.service.AccountService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {
    private final AccountService accounts;
    public AccountController(AccountService accounts) { this.accounts=accounts; }
    @Operation(summary = "Open a named zero-balance VND account for the signed-in user")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping public ResponseEntity<AccountView> create(@Valid @RequestBody Name input, @AuthenticationPrincipal AuthPrincipal principal) {
        var account = accounts.create(input, principal);
        return ResponseEntity.created(URI.create("/api/accounts/" + account.id())).body(account);
    }
    @Operation(summary = "List only the signed-in user's accounts", description = "Ordered by createdAt DESC, id DESC. Page starts at 0; size is 1-100.")
    @GetMapping public AccountPage list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
                                       @AuthenticationPrincipal AuthPrincipal principal) {
        return accounts.list(page, size, principal);
    }
    @Operation(summary = "Rename an owned account", description = "Updates only accountName; requires authentication, ownership and a current CSRF token.")
    @PatchMapping("/{id}") public AccountView rename(@PathVariable UUID id, @Valid @RequestBody Name input,
                                                    @AuthenticationPrincipal AuthPrincipal principal) {
        return accounts.rename(id, input, principal);
    }
    @GetMapping("/{id}") public AccountView get(@PathVariable UUID id, @AuthenticationPrincipal AuthPrincipal principal) { return accounts.get(id,principal); }
    @GetMapping("/{id}/balance") public Balance balance(@PathVariable UUID id, @AuthenticationPrincipal AuthPrincipal principal) {
        var account = accounts.get(id,principal); return new Balance(account.id(),account.balance(),account.currency());
    }
}
