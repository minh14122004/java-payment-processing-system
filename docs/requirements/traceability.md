# Requirements and Test Traceability

Source: [SRS revision 1.6](SRS-v1.0.md). Authentication, accounts and financial operations are implemented; verification is recorded by increment below. Historical evidence is retained; the newest increment supersedes earlier pending-auth notes. Specifications: [mini-payment-system/specs](../../openspec/changes/mini-payment-system/specs).

| SRS requirement | Capability | Milestone | Planned evidence |
| --- | --- | --- | --- |
| FR-01 | account-management | M1 | TC-01/23-27; first/additional accounts, labels, owner FK, list, rename, migration, zero VND balance and rollback |
| FR-02 | account-management | M1 | Account/balance retrieval, 404 and malformed UUID |
| FR-03 | cash-operations | M2 | TC-02/03; missing account, fractional amount, overflow and rollback |
| FR-04 | cash-operations | M2 | TC-04/05; exact-balance withdrawal, fractional amount and rollback |
| FR-05 | fund-transfers | M3 | TC-06–09; balance conservation, missing source and overflow |
| FR-06 | transaction-history | M4 | TC-13; details/404, tied timestamps, pagination and both participants |
| FR-07 / revision 1.6 | transfer-idempotency, withdrawal-idempotency | M5/M6 | TC-10/11/14; retry after failure, normalized identity and persisted replay |
| FR-08 | concurrent-processing | M2/M3/M6 | TC-12/14; concurrent deposits, mixed writes, opposite-direction transfers and timeout |
| FR-09 | user-authentication | M1 | TC-15/16/22; unique email, email/password-only login, sessions and throttling |
| FR-10 | email-otp-verification | M1 | TC-17/18/20; lifecycle, concurrency, persisted limits and delivery failure |
| FR-11 | user-authentication, email-otp-verification | M1 | TC-19/20; current password, OTP, atomic update and session invalidation |
| FR-12 | user-authentication and financial/history/idempotency capabilities | M1-M5 | TC-21; ownership, authentication, CSRF and replay privacy |
| REST §4 | api-contract | M0–M7 | 20 endpoint operations, DTOs/Location, validation, HTTP status/error JSON and OpenAPI |
| DB §5 | Account, authentication, OTP and financial specs; design sections 4/8/9; ERD | M1/M2/M5 | Unique email and indexed nonunique owner FK, OTP state constraints, PK/FK/CHECK and exact decimals |
| NFR-01/02 | cash-operations, fund-transfers, concurrent-processing | M2–M6 | Balance invariants and rollback after partial writes or unique-key conflicts |
| NFR-03 | local-backend-foundation | M0–M7 | Review of Controller/Service/Repository responsibilities and DTO boundaries |
| NFR-04 | local-backend-foundation | M1–M7 | Unit and H2 integration tests covering success and failure paths |
| NFR-05 | local-backend-foundation | M0/M7 | Local startup with Java/Maven/PostgreSQL, configured SMTP for delivery and fake mail for tests |
| NFR-06 | api-contract, local-backend-foundation | M0–M7 | Input validation, loopback binding and exclusion of secrets and real financial data |
| Agent instructions §10 | Design, roadmap and tasks | M0–M7 | Specification-driven development, scoped changes and relevant tests before task completion |

## Required test scenarios

| ID | Scenario | Milestone / task |
| --- | --- | --- |
| TC-01 | Confirm registration and create one owned zero-balance account | M1 / 2.9 |
| TC-02 | Deposit a positive amount | M2 / 3.6 |
| TC-03 | Reject a zero or negative deposit | M2 / 3.2, 3.6 |
| TC-04 | Withdraw within the available balance | M2 / 3.4; increment 11 |
| TC-05 | Reject an excessive withdrawal without changing data | M2 / 3.4; increment 11 |
| TC-06 | Complete a valid transfer | M3 / 4.3 |
| TC-07 | Reject a transfer to the same account | M3 / 4.3 |
| TC-08 | Reject a transfer to a nonexistent destination | M3 / 4.3 |
| TC-09 | Roll back the entire transfer after an intermediate failure | M3 / 4.4 |
| TC-10 | Replay an identical key and payload | M5 / 6.5 |
| TC-11 | Reject a reused key with a different payload | M5 / 6.5 |
| TC-12 | Handle concurrent withdrawals exceeding the available balance | M6 / 7.2 |
| TC-13 | Retrieve transaction history with pagination | M4 / 5.4 |
| TC-14 | Handle concurrent requests with the same idempotency key | M6 / 7.5 |
| TC-15 | Duplicate normalized email and concurrent confirmation | M1 / 2.9 |
| TC-16 | Email/password login and rejection of other credentials | M1 / 2.6, 2.9 |
| TC-17 | Wrong/expired/consumed/replaced/wrong-purpose OTP | M1 / 2.9 |
| TC-18 | Issuance limits, concurrent requests and SMTP failures | M1 / 2.4, 2.9 |
| TC-19 | Password change invalidates old credentials/sessions/challenges | M1 / 2.7, 2.9 |
| TC-20 | Password rollback and concurrent OTP confirmation | M1 / 2.9 |
| TC-21 | Authentication, CSRF, ownership and replay privacy | M1 / 2.10; M2 / 3.6; M3 / 4.3; M4 / 5.1-5.4; M5 / 6.5 |
| TC-22 | Logout, session expiration and credential throttling | M1 / 2.6, 2.9 |
| TC-23 | Persist multiple accounts for one user; reject missing/unknown owners | M1 / 2.1 |
| TC-24 | Named account creation without another identity/OTP | M1 / 2.11 |
| TC-25 | Owner-filtered account pagination, stable order and empty pages | M1 / 2.11 |
| TC-26 | Owned rename, auth/CSRF, strict input and name-only updates | M1 / 2.11 |
| TC-27 | Account-name migration and preservation of legacy data | M1 / 2.11 |

## SRS §9 acceptance criteria

| AC | Required evidence | Milestone |
| --- | --- | --- |
| 1 | Successful local startup using README instructions | M0/M7 |
| 2 | Contract/integration tests for all specified routes | M7 |
| 3 | TC-02/04/06 and exact monetary boundary tests | M2/M3 |
| 4 | TC-05/08/09 and transaction/idempotency record persistence failures | M2/M3/M5 |
| 5 | TC-10/11/14, with only one financial operation applied | M5/M6 |
| 6 | TC-12/14 and mixed/concurrent balance updates | M6 |
| 7 | TC-13 and history containing committed transactions only | M4/M6 |
| 8 | Successful Maven verification and recorded test evidence | M7 |
| 9 | Accessible Swagger UI matching the API contract | M0/M7 |
| 10 | Source code, README, setup instructions, API examples and test documentation ready in the repository for GitHub publication | M7 |
| 11 | TC-01/15/16 and email/password registration/login contract | M1/M7 |
| 12 | TC-19/20/22 and session invalidation after password changes | M1/M7 |
| 13 | TC-17/18/21/22, OTP/credential limits and SMTP failure handling | M1-M7 |
| 14 | TC-23/24: one-to-many ownership and additional account opening | M1/M7 |

Pushing or publishing to GitHub is outside the current specification preparation step.

## Verified entity ownership increment (2026-10-01)

`mvn test` passed: 51 tests, 0 failures, 0 errors (Java 23 runtime compiling with Java 21 release target). This completes task 2.1 and TC-23 at the entity/persistence layer, not the registration or account-opening APIs.

- [UserAccountPersistenceTest](../../src/test/java/com/example/payment/entity/UserAccountPersistenceTest.java): reload two accounts for one user, isolate a second user's accounts, reject duplicate canonical email, null/unknown owners and deletion of a user with accounts. Uses an isolated H2 database with generated test schema; the PostgreSQL runtime schema has not been migrated or verified.
- [UserValidationTest](../../src/test/java/com/example/payment/entity/UserValidationTest.java): locale-independent email normalization, profile validation, bidirectional ownership, read-only account collection and secret-field serialization exclusion.
- [EntityValidationTest](../../src/test/java/com/example/payment/entity/EntityValidationTest.java): existing monetary/entity tests updated to create owned accounts; all continue to pass.

User accepts an already encoded password hash; no password encoder, authentication endpoint or OTP delivery is implemented in this increment. Enforcing at least one account for each registered user remains the responsibility of the planned atomic registration service.

## Verified PostgreSQL schema increment (2026-10-06)

[Initial DDL](../../database/001_initial_schema.sql) created all five ERD tables in payment_db.public on PostgreSQL 17.11 in the existing payment-postgres container. [SQL verification](../../database/verify_schema.sql) passed ownership, unique-email, money, transaction-shape, foreign-key, idempotency and OTP-state checks. All fixtures were rolled back; each business table has zero rows. This supersedes the earlier note that the runtime schema was not created. Authentication, SMTP and payment services are still pending.

## Verified authentication increment (2026-10-07)

`mvn verify` passed on Java 21.0.9: **74 tests, 0 failures, 0 errors, 0 skipped**.
Tests use a separate PostgreSQL 17 Testcontainer and a recording email adapter.
The real HTTP restart test starts two embedded servlet servers against the same
test database and keys. Live external SMTP inbox delivery has not been tested.

| Requirement / scenario | Passing implementation evidence |
| --- | --- |
| TC-01/15/16: registration, normalized identity, stored hash and login | AuthIntegrationTest.registrationHashRoundTripAndSessionActuallyAuthenticateNextRequest; simultaneousConfirmationsCreateExactlyOnePair |
| TC-17/18: OTP expiry, attempts, replay, purpose, resend and quota | attemptsExpiryReplayPurposeAndResendPersistCorrectly; smtpFailuresConsumeQuotaAndPreservePreviousCode; concurrentWrongCodesCannotExceedAttemptLimitAndIssuanceIsSerialized; cleanupClearsExpiredSecretsWithoutErasingActiveRateWindows |
| TC-19/20: password change/reset and transaction boundaries | changeAndResetInvalidateOldPasswordsAllSessionsAndOutstandingChallenges; databaseFailuresRollbackUserAccountPasswordAndOtpTogether; acceptedEmailButFailedCommitKeepsOldChallengeAndReservedQuota; resendCompetingWithVerifyAndChangeCompetingWithResetRemainAtomic |
| TC-21, account portion: session, CSRF, ownership and HTTP errors | errorsCsrfOwnershipAndStrictJson; anotherUserCannotConfirmOrResendPasswordChange; httpDatabaseAndMailFailuresReturnSafeErrorsAndPreserveState |
| TC-22: shared credential limit, restart, session expiry | credentialLimitSharedWithCurrentPasswordAndAppliesToUnknownEmails; AuthRestartTest.persistedHashesAndOtpQuotasSurviveRestartWhileSessionsDoNot (asserts 1800-second configured timeout and exercises expiry using a shortened test session) |
| Password policy/algorithm/key safety | PepperedPasswordEncoderTest; AuthConfigurationTest; rejectsScalarCoercionAcrossAllSecretFieldsWithoutSendingMail; 128-emoji HTTP registration/login after restart |
| SMTP adapter, error mapping and API documentation | SmtpOtpMailSenderTest; errorsCsrfOwnershipAndStrictJson checks /v3/api-docs and /swagger-ui/index.html |

Test sources: [AuthIntegrationTest](../../src/test/java/com/example/payment/auth/AuthIntegrationTest.java),
[AuthRestartTest](../../src/test/java/com/example/payment/auth/AuthRestartTest.java),
[PepperedPasswordEncoderTest](../../src/test/java/com/example/payment/security/PepperedPasswordEncoderTest.java).

Migration 002 was applied to the existing local payment_db on 2026-10-07 after
checking its previous constraints. Both OTP constraints now permit PASSWORD_RESET.
`database/verify_schema.sql` passed; fixtures rolled back and users/accounts/OTP
row counts remained 0/0/0. The test suite never writes fixtures into this database.

The current increment implements 12 routes: ten authentication routes and two
owned account reads. POST /api/accounts (TC-24), financial APIs and their ownership,
replay, monetary and concurrency acceptance remain M1 follow-up/M2-M7 work. No
claim of complete payment-system acceptance is made.

## Verified named account increment (2026-10-12)

`mvn verify` passed on Java 21.0.9: **89 tests, 0 failures, 0 errors, 0 skipped**.
The suite uses isolated PostgreSQL 17 Testcontainers, H2 entity tests and real
HTTP restart/session-expiry checks. Both `openspec validate mini-payment-system --strict`
and `openspec validate finalize-swagger-csrf-workflow --strict` passed.

| Requirement / scenario | Passing implementation evidence |
| --- | --- |
| TC-01/15: default first name, one account after confirmation/replay, atomic rollback and concurrent confirmation | AuthIntegrationTest.registrationHashRoundTripAndSessionActuallyAuthenticateNextRequest; simultaneousConfirmationsCreateExactlyOnePair; databaseFailuresRollbackUserAccountPasswordAndOtpTogether |
| TC-24: additional named accounts, zero VND, session owner, display-name holder, Location, duplicate labels and no new user/OTP | AccountIntegrationTest.opensNamedAccountsWithoutNewIdentityOrOtpAndListsOnlyTheirOwner |
| TC-25: owner-only items/counts, timestamp/UUID order, default and explicit pagination, empty/large-offset pages | opensNamedAccountsWithoutNewIdentityOrOtpAndListsOnlyTheirOwner; paginationUsesStableDescendingTimestampAndUuidAndSupportsEmptyPages |
| TC-26: strict names/types/unknown fields, pagination/UUID validation, owner-only rename and name-only SQL | rejectsInvalidNamesUnknownBodyFieldsAndInvalidPagination; renameRequiresOwnershipAndUpdatesOnlyNameWithoutTouchingMoney (a trigger rejects any balance-column update) |
| TC-21/22: direct account-route denial before CSRF, anonymous/logout/revoked sessions, stale/missing CSRF | directRequestsRequireLoginEvenWithAnonymousCsrfAndAfterLogoutOrRevocation; authenticatedMutationsRequireCurrentSessionCsrf; AuthRestartTest.persistedHashesAndOtpQuotasSurviveRestartWhileSessionsDoNot (GET/POST/PATCH after restart and real servlet expiry) |
| TC-26: creation/rename failure rolls back with safe errors | failedCreateAndRenameRollBackAndReturnGenericError |
| TC-27: upgrade with multiple owners/tied timestamps, all old columns and related records preserved; fresh schema and constraints | AccountMigrationTest; EntityValidationTest and UserAccountPersistenceTest cover named entity validation/ownership |
| OpenAPI and public Swagger with manual POST/PATCH CSRF | AccountIntegrationTest.openApiIncludesNewContractsAndManualCsrfOnPostAndPatch |

Test sources: [AccountIntegrationTest](../../src/test/java/com/example/payment/auth/AccountIntegrationTest.java),
[AccountMigrationTest](../../src/test/java/com/example/payment/auth/AccountMigrationTest.java),
[AuthIntegrationTest](../../src/test/java/com/example/payment/auth/AuthIntegrationTest.java),
[AuthRestartTest](../../src/test/java/com/example/payment/auth/AuthRestartTest.java).

Migration 003 was applied once to the existing local `payment_db` in
`payment-postgres` on 2026-10-12. Before/after fingerprints over every pre-existing
column in all five business tables matched. Counts remained: 1 user, 1 account,
0 transactions, 0 idempotency records and 0 OTP challenges; total balance stayed
0 VND. The existing account now has the exact name `tài khoản thanh toán 1`.
`account_name` is VARCHAR(100), NOT NULL, with the nonblank CHECK and no UNIQUE.
`database/verify_schema.sql` passed; all fixtures rolled back and the same data
fingerprints still matched afterward.

This completes task 2.11 and the requested named-account plan. Fifteen operations
are implemented (ten authentication and five account operations); the five
financial/history operations, M2-M7 acceptance and automatic Swagger CSRF handling
remain future work. Manual Swagger CSRF input is retained. Live SMTP inbox delivery
was not part of these tests.

## SRS 1.5 financial increment verification (2026-10-13)

`mvn verify` passed on Java 21: **108 tests, 0 failures, 0 errors, 0 skipped**. Strict validation passed for mini-payment-system and finalize-swagger-csrf-workflow. This is incremental acceptance; withdrawal and automatic Swagger CSRF are not implemented.

| Capability | Evidence |
| --- | --- |
| Stable generated numbers; preserved upgrade data | FinancialMigrationTest.upgradesPopulatedSchemaDeterministicallyAndPreservesEveryExistingColumn; FinancialIntegrationTest.concurrentAccountCreationGeneratesDistinctNumbersAndClientCannotChooseThem; existing registration/account tests |
| Deposit validation, limits and exact arithmetic | FinancialIntegrationTest.depositReturnsRetrievableImmutableRecordAndEachCallCreditsAgain; strictMoneyAndBodyValidationRejectsInvalidJsonWithoutWrites |
| Preview, user-name default, editable input and immutable message | previewUsesUserNamesAndNeverWritesThenCustomMessageIsFrozen; blankMessagesUseDefaultAndOversizedOrWrongTypedFieldsAreRejected |
| Transfer checks, conservation and same-owner accounts | executionRechecksPreviewAndAllowsDistinctAccountsOfSameOwner; oppositeDirectionsAndSameKeyConcurrentCallsConserveBalances |
| Authorization, CSRF, session revocation and participant history | allRoutesEnforceAuthenticationCsrfOwnershipAndCredentialRevocation; historyIsStablePaginatedParticipantOnlyAndOmitsFailedAttempts |
| Idempotency/replay/mismatch/normalization | replayNormalizesMoneyAndDefaultModeAndReturnsOriginalAfterNameChange; keysAreRequiredSingularCaseSensitiveAndNeverTrimmed |
| Atomic rollback after debit/record/key failure | failuresAfterDebitAndDuringRecordOrKeyWriteRollBackEverythingAndAllowRetry |
| Concurrent deposits/transfers and disjoint-pair unique race | concurrentDepositsAndCompetingTransfersDoNotLoseMoneyOrOverdraw; sameKeyOnDisjointPairsRollsBackTheLosingFinancialWrites |
| Real PostgreSQL lock timeout and rename preservation | heldLockTimesOutAndRollsBackWithRetryableConflict; balanceUpdatesPreserveConcurrentRenameAndNumbers |
| Real HTTP walkthrough and durable replay after restart/re-login | AuthRestartTest.financialHttpWalkthroughAndReplaySurviveApplicationRestart |
| Swagger assets, routes, schemas, CSRF and key fields | FinancialIntegrationTest.openApiAdvertisesAllRoutesKeyAndManualCsrf; existing AccountIntegrationTest OpenAPI checks |

All unqualified test methods above belong to FinancialIntegrationTest. Concurrent tests use separate request threads/connections with barriers and bounded futures; the disjoint key test forces both attempts to reach key persistence before either wins. Failure assertions read committed state through fresh database access. Tests use disposable PostgreSQL Testcontainers, never truncate the local database, and use fake mail.

Local migration 004 and verify_schema.sql completed successfully. Aggregated hashes of all original columns in users/accounts/payment_transactions/idempotency_records/email_otp_challenges were identical before/after migration and after rolled-back schema fixtures. Browser UI walkthrough remains unverified: no browser was connected to the computer-use runtime. The automated real HTTP walkthrough and Swagger contract checks passed.

## SRS 1.6 idempotent withdrawal verification (2026-11-06)

Java 21 `mvn verify` passed: **123 tests, 0 failures, 0 errors, 0 skipped**.
Both `openspec validate mini-payment-system --strict` and `openspec validate finalize-swagger-csrf-workflow --strict` passed. This increment implements withdrawals and operation-scoped idempotency; full M7 acceptance and automatic Swagger CSRF remain pending.

| Requirement / scenario | Passing evidence |
| --- | --- |
| TC-04/05/13: debit, exact-balance withdrawal, insufficient/zero funds, immutable detail/history and participant denial | FinancialIntegrationTest.withdrawalDebitsOnceAndFullBalanceReplayPreservesOriginalHistory |
| Strict JSON/money/header limits, missing account, rejected-key retry | withdrawalRejectsStrictInputsAndHeadersWithoutWrites; withdrawalSupportsMaximumExactAmountWithoutRounding |
| Independent TRANSFER/WITHDRAWAL namespaces, case-sensitive/boundary-length keys | withdrawalKeysAreCaseSensitiveAndIndependentFromTransfers |
| TC-21: authentication before body/CSRF, invalid CSRF, ownership before replay and credential revocation | withdrawalAuthorizationPrecedesReplayAndRevokedSessionsCannotWithdraw; allRoutesEnforceAuthenticationCsrfOwnershipAndCredentialRevocation |
| Actual rollback after debit, financial record and key writes; safe errors and later retry | withdrawalFailuresAfterDebitRecordAndKeyWritesRollBackAndAllowRetry |
| TC-12: two withdrawals of 800000 from 1000000 leave 200000 with exactly one committed result/key | competingWithdrawalsUseLockedFundsAndNeverOverdraw |
| Same-key concurrent full-balance replay and changed amount | sameWithdrawalKeyConcurrentlyExecutesOnceEvenWhenBalanceIsExhausted |
| Forced unique race on disjoint accounts with complete loser rollback | disjointWithdrawalKeyRaceRollsBackTheLoserBeforeMismatch |
| Withdrawal vs transfer/deposit/rename; balance, label and number preservation | withdrawalsSerializeWithTransfersDepositsAndRenames |
| Real 5-second PostgreSQL row-lock timeout, rollback and retry with the same key | withdrawalLockTimeoutRollsBackAndDoesNotBindKey |
| Migration 005 preserves every old column in all five tables; backfill, constraints and pre-upgrade transfer replay | FinancialMigrationTest.operationNamespaceUpgradePreservesAllColumnsAndReplaysOldTransfer |
| JPA composite uniqueness and operation persistence | UserAccountPersistenceTest.idempotencyEntityPersistsIndependentNamespacesAndRejectsDuplicateWithinOperation |
| Focused unit verification of authorization-before-lookup and normalized replay with zero funds/no lock or debit | WithdrawalServiceTest |
| Real HTTP sessions, withdrawal and transfer sharing a key, detail/history, restart/re-login replay, logout/real expiry denial | AuthRestartTest.financialHttpWalkthroughAndReplaySurviveApplicationRestart; persistedHashesAndOtpQuotasSurviveRestartWhileSessionsDoNot |
| OpenAPI withdrawal route, key and retained manual CSRF input | FinancialIntegrationTest.openApiAdvertisesAllRoutesKeyAndManualCsrf |

Unqualified method names above belong to FinancialIntegrationTest. PostgreSQL tests run against disposable Testcontainers, not local payment_db. Concurrent requests use independent connections/transactions, start barriers and bounded futures; the disjoint-account test forces both requests to key persistence before arbitration. Failure assertions observe committed state from fresh database access. Migration testing also executes verify_schema.sql and checks that all original columns remain identical afterward. Existing authentication, account, deposit, transfer, history and restart suites passed unchanged behavior.

Local migration 005 was applied once after preflight confirmed schema 004, outstanding operation column and no listener on application port 8080. Counts before/after: **1 user, 2 accounts, 0 transactions, 0 idempotency records, 0 OTP challenges; total balance 0 VND**. For all five tables, aggregate hashes of every original column matched before migration, after migration and after rolled-back verify_schema.sql fixtures. No business data was reset. The operation default/check/composite uniqueness and old transaction uniqueness/FKs were verified. Local evidence is in target/local-withdrawal-migration-evidence.json; Maven output is target/withdrawal-verify.log (build artifacts, not committed).

Browser interaction was not part of this increment; Swagger assets/OpenAPI and real HTTP walkthrough are automated. Automatic Swagger CSRF remains in its separate change; no overall archival or full-release acceptance is claimed.
