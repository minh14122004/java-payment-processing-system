package com.example.payment.service;

import com.example.payment.dto.AuthDtos.AccountView;
import com.example.payment.dto.AccountDtos.AccountPage;
import com.example.payment.dto.AccountDtos.Name;
import com.example.payment.entity.Account;
import com.example.payment.exception.ApiException;
import com.example.payment.repository.AccountRepository;
import com.example.payment.security.AuthPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import java.util.List;
import java.util.UUID;

@Service
public class AccountService {
    private final AccountRepository accounts;
    private final AuthService auth;
    public AccountService(AccountRepository accounts, AuthService auth) { this.accounts = accounts; this.auth = auth; }

    @Transactional
    public AccountView create(Name input, AuthPrincipal principal) {
        var user = auth.current(principal);
        return AccountView.of(accounts.saveAndFlush(new Account(user, user.getDisplayName(), input.accountName())));
    }

    @Transactional(readOnly = true)
    public AccountPage list(int page, int size, AuthPrincipal principal) {
        var user = auth.current(principal);
        if (page < 0 || size < 1 || size > 100)
            throw new ApiException(400, "INVALID_REQUEST", "Invalid account pagination");
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        // JPA offsets are ints. Large, valid pages beyond the dataset still return an empty page.
        if (pageable.getOffset() > Integer.MAX_VALUE) {
            long total = accounts.countByUserId(user.getId());
            return new AccountPage(List.of(), page, size, total, (int) Math.ceil((double) total / size));
        }
        var result = accounts.findByUserId(user.getId(), pageable);
        return new AccountPage(result.getContent().stream().map(AccountView::of).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public AccountView rename(UUID id, Name input, AuthPrincipal principal) {
        get(id, principal); // Preserve the shared 401/403/404 ownership contract before writing.
        if (accounts.rename(id, principal.userId(), input.accountName()) != 1)
            throw new ApiException(404, "ACCOUNT_NOT_FOUND", "Account not found");
        return get(id, principal);
    }
    @Transactional(readOnly=true)
    public AccountView get(UUID id, AuthPrincipal principal) {
        if (principal == null) throw ApiException.authentication();
        var account = accounts.findById(id).orElseThrow(() -> new ApiException(404,"ACCOUNT_NOT_FOUND","Account not found"));
        if (!account.getUser().getId().equals(principal.userId())) throw new ApiException(403,"ACCESS_DENIED","Access denied");
        return AccountView.of(account);
    }
}
