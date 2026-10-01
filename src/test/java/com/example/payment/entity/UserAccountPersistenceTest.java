package com.example.payment.entity;

import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:user-account-tests",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class UserAccountPersistenceTest {

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void persistsMultipleAccountsForOneUserAndKeepsOtherUsersSeparate() {
        User owner = entityManager.persist(user("owner@example.test"));
        User other = entityManager.persist(user("other@example.test"));
        Account first = entityManager.persist(new Account(owner, "First"));
        Account second = entityManager.persist(new Account(owner, "Second"));
        Account third = entityManager.persist(new Account(other, "Third"));
        entityManager.flush();
        entityManager.clear();

        User reloaded = entityManager.find(User.class, owner.getId());
        assertThat(reloaded.getAccounts()).extracting(Account::getId)
                .containsExactlyInAnyOrder(first.getId(), second.getId());
        assertThat(entityManager.find(Account.class, first.getId()).getUser()).isSameAs(reloaded);
        assertThat(entityManager.find(Account.class, second.getId()).getUser()).isSameAs(reloaded);
        assertThat(entityManager.find(User.class, other.getId()).getAccounts())
                .extracting(Account::getId).containsExactly(third.getId());
    }

    @Test
    void databaseRejectsDuplicateNormalizedEmail() {
        entityManager.persistAndFlush(user("owner@example.test"));

        assertThatThrownBy(() -> entityManager.persistAndFlush(user("  OWNER@EXAMPLE.TEST  ")))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void databaseRejectsAccountWithoutOwner() {
        assertThatThrownBy(() -> entityManager.getEntityManager().createNativeQuery("""
                INSERT INTO accounts (id, user_id, account_holder_name, balance, currency, created_at)
                VALUES (:id, NULL, 'Orphan', 0, 'VND', CURRENT_TIMESTAMP)
                """).setParameter("id", UUID.randomUUID()).executeUpdate())
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void databaseRejectsAccountReferencingUnknownUser() {
        assertThatThrownBy(() -> entityManager.getEntityManager().createNativeQuery("""
                INSERT INTO accounts (id, user_id, account_holder_name, balance, currency, created_at)
                VALUES (:id, :userId, 'Orphan', 0, 'VND', CURRENT_TIMESTAMP)
                """).setParameter("id", UUID.randomUUID())
                .setParameter("userId", UUID.randomUUID()).executeUpdate())
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void deletingUserDoesNotCascadeDeleteAccounts() {
        User owner = entityManager.persist(user("owner@example.test"));
        entityManager.persist(new Account(owner, "Keep financial history"));
        entityManager.flush();
        entityManager.clear();

        assertThatThrownBy(() -> {
            entityManager.remove(entityManager.find(User.class, owner.getId()));
            entityManager.flush();
        }).isInstanceOf(PersistenceException.class);
    }

    private static User user(String email) {
        return new User(email, "Demo User", "encoded-test-hash", Instant.parse("2026-10-01T00:00:00Z"));
    }
}
