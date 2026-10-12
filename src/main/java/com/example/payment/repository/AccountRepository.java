package com.example.payment.repository;

import com.example.payment.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    Page<Account> findByUserId(UUID userId, Pageable pageable);
    long countByUserId(UUID userId);

    // Update only the label: a stale entity must never overwrite a concurrent balance change.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Account a set a.accountName = :name where a.id = :id and a.user.id = :userId")
    int rename(@Param("id") UUID id, @Param("userId") UUID userId, @Param("name") String name);
}
