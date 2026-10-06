# Requirements and Test Traceability

Source: [SRS revision 1.2](SRS-v1.0.md). The acceptance scenarios below are planned unless evidence is recorded below. User and Account ownership now have focused entity/database tests; authentication and OTP acceptance remain pending. Specifications: [mini-payment-system/specs](../../openspec/changes/mini-payment-system/specs).

| SRS requirement | Capability | Milestone | Planned evidence |
| --- | --- | --- | --- |
| FR-01 | account-management | M1 | TC-01/23/24; first and additional accounts, owner FK, names, zero balance, VND and rollback |
| FR-02 | account-management | M1 | Account/balance retrieval, 404 and malformed UUID |
| FR-03 | cash-operations | M2 | TC-02/03; missing account, fractional amount, overflow and rollback |
| FR-04 | cash-operations | M2 | TC-04/05; exact-balance withdrawal, fractional amount and rollback |
| FR-05 | fund-transfers | M3 | TC-06–09; balance conservation, missing source and overflow |
| FR-06 | transaction-history | M4 | TC-13; details/404, tied timestamps, pagination and both participants |
| FR-07 | transfer-idempotency | M5/M6 | TC-10/11/14; retry after failure, normalized identity and persisted replay |
| FR-08 | concurrent-processing | M2/M3/M6 | TC-12/14; concurrent deposits, mixed writes, opposite-direction transfers and timeout |
| FR-09 | user-authentication | M1 | TC-15/16/22; unique email, email/password-only login, sessions and throttling |
| FR-10 | email-otp-verification | M1 | TC-17/18/20; lifecycle, concurrency, persisted limits and delivery failure |
| FR-11 | user-authentication, email-otp-verification | M1 | TC-19/20; current password, OTP, atomic update and session invalidation |
| FR-12 | user-authentication and financial/history/idempotency capabilities | M1-M5 | TC-21; ownership, authentication, CSRF and replay privacy |
| REST §4 | api-contract | M0–M7 | 16 endpoints, DTOs/Location, validation, HTTP status/error JSON and OpenAPI |
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
| TC-04 | Withdraw within the available balance | M2 / 3.6 |
| TC-05 | Reject an excessive withdrawal without changing data | M2 / 3.6 |
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
| TC-24 | Authenticated additional account opening without a second user | M1 / 2.8 |

## SRS §9 acceptance criteria

| AC | Required evidence | Milestone |
| --- | --- | --- |
| 1 | Successful local startup using README instructions | M0/M7 |
| 2 | Contract/integration tests for all 16 routes | M7 |
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
