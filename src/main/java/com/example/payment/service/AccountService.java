package com.example.payment.service;

import com.example.payment.dto.AuthDtos.AccountView;
import com.example.payment.exception.ApiException;
import com.example.payment.repository.AccountRepository;
import com.example.payment.security.AuthPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class AccountService {
    private final AccountRepository accounts;
    public AccountService(AccountRepository accounts) { this.accounts = accounts; }
    @Transactional(readOnly=true)
    public AccountView get(UUID id, AuthPrincipal principal) {
        if (principal == null) throw ApiException.authentication();
        var account = accounts.findById(id).orElseThrow(() -> new ApiException(404,"ACCOUNT_NOT_FOUND","Account not found"));
        if (!account.getUser().getId().equals(principal.userId())) throw new ApiException(403,"ACCESS_DENIED","Access denied");
        return AccountView.of(account);
    }
}
