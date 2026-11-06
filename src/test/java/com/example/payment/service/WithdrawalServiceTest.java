package com.example.payment.service;

import com.example.payment.dto.TransactionDtos.Withdrawal;
import com.example.payment.dto.TransactionDtos.TransactionView;
import com.example.payment.exception.ApiException;
import com.example.payment.repository.FinancialRepository;
import com.example.payment.repository.FinancialRepository.AccountRow;
import com.example.payment.repository.FinancialRepository.SavedResult;
import com.example.payment.security.AuthPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import static com.example.payment.enums.IdempotencyOperation.WITHDRAWAL;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WithdrawalServiceTest {
    private final FinancialRepository repository=mock(FinancialRepository.class);
    private final ObjectMapper json=new ObjectMapper();
    private final UUID owner=UUID.randomUUID(), account=UUID.randomUUID(), transaction=UUID.randomUUID();
    private FinancialService service() {
        var manager=mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        return new FinancialService(repository,json,manager);
    }
    @Test void unauthorizedCallerCannotLookUpOrReplayKnownKey() {
        when(repository.account(account,false)).thenReturn(Optional.of(new AccountRow(account,owner,"100000000000","Owner",BigDecimal.ZERO)));
        assertThatThrownBy(() -> service().withdraw(new Withdrawal(account,BigDecimal.ONE),"known",new AuthPrincipal(UUID.randomUUID(),0)))
            .isInstanceOf(ApiException.class).satisfies(e -> assertThat(((ApiException)e).status()).isEqualTo(403));
        verify(repository,never()).saved(any(),anyString());
        verify(repository,never()).balance(any(),any());
    }
    @Test void completedIdentityReplaysAtZeroBalanceWithoutLockOrDebit() throws Exception {
        when(repository.account(account,false)).thenReturn(Optional.of(new AccountRow(account,owner,"100000000000","Owner",BigDecimal.ZERO)));
        String fingerprint="w1:"+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(json.writeValueAsBytes(List.of(account.toString(),"1000"))));
        when(repository.saved(WITHDRAWAL,"known")).thenReturn(Optional.of(new SavedResult(fingerprint,account,transaction)));
        var original=new TransactionView(transaction,"WITHDRAWAL",account,null,"100000000000",null,
            new BigDecimal("1000"),"VND","SUCCESS",Instant.parse("2026-01-01T00:00:00Z"),null);
        when(repository.transaction(transaction)).thenReturn(Optional.of(original));
        var result=service().withdraw(new Withdrawal(account,new BigDecimal("1000.0")),"known",new AuthPrincipal(owner,0));
        assertThat(result.replay()).isTrue(); assertThat(result.transaction()).isEqualTo(original);
        verify(repository,never()).account(account,true);
        verify(repository,never()).balance(any(),any());
        verify(repository,never()).insert(anyString(),any(),any(),any(),any());
        verify(repository,never()).saveKey(any(),anyString(),anyString(),any());
    }
}
