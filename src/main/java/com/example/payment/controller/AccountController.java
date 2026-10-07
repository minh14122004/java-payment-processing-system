package com.example.payment.controller;

import com.example.payment.dto.AuthDtos.*;
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
    @GetMapping("/{id}") public AccountView get(@PathVariable UUID id, @AuthenticationPrincipal AuthPrincipal principal) { return accounts.get(id,principal); }
    @GetMapping("/{id}/balance") public Balance balance(@PathVariable UUID id, @AuthenticationPrincipal AuthPrincipal principal) {
        var account = accounts.get(id,principal); return new Balance(account.id(),account.balance(),account.currency());
    }
}
