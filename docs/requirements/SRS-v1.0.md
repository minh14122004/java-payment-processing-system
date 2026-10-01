# Mini Payment Processing System

## Software Requirements Specification (SRS)

**Version:** 1.2 (2026-10-01; filename retained for existing links)

**Revision:** Adds email/password registration and login, unique email, email OTP verification and account ownership. Revision 1.2 names the entity User and allows one or more bank accounts per user. Existing user edits are retained.

**Project Type:** Backend RESTful API
**Primary Language:** Java 21
**Architecture:** Layered Architecture (Controller – Service – Repository)

---

## 1. Project Overview

Mini Payment Processing System is a simplified financial transaction processing application developed using Java and Spring Boot.

The system simulates basic financial operations between internal accounts, including account creation, balance management, deposits, withdrawals, fund transfers, and transaction history.

The primary objective is to demonstrate backend software engineering skills, including RESTful API development, object-oriented programming, database design, transaction management, concurrency control, exception handling, and automated testing.


### 1.1 Project Objectives

* Develop a RESTful backend application using Java 21 and Spring Boot.
* Implement financial transaction processing with consistent and reliable balance management.
* Prevent duplicate transaction execution and unauthorized overdrafts.
* Ensure database consistency under concurrent transaction requests.
* Apply clean code principles, separation of concerns, and appropriate design patterns.
* Demonstrate practical backend development skills through automated testing and technical documentation.

### 1.2 Project Scope

The application must support:

1. Registration using email, password and display name; email OTP confirmation creates one bank account with zero balance.
2. Login using email and password, logout and ownership checks.
3. Password change using the current password and an OTP sent to the registered email.
4. Account details and balance retrieval.
5. Deposits and withdrawals.
6. Fund transfers between internal accounts.
7. Transaction history.
8. Transaction validation and error handling.
9. Atomic database transactions.
10. Idempotent fund transfer requests.
11. Concurrency control for account balance updates.
12. Unit tests and integration tests.

### 1.3 Out of Scope

The initial version must not include:

<!-- * Frontend or mobile applications. -->
* Real payment gateway or banking integrations.
* Cryptocurrency transactions.
* Currency exchange.
* Social login, username login, email changes and forgotten-password recovery.
* SMS and email notifications other than registration/password-change OTP.
* Microservices architecture.
* Kubernetes, cloud deployment, or distributed messaging.
* Real financial data; only a recipient email and a display name are required for this demo.

The API is intended for local demonstration with simulated funds. Email/password authentication and account ownership checks are required; real banking integration remains outside scope.

---

## 2. Technology Stack

| Component            | Technology                         |
| -------------------- | ---------------------------------- |
| Programming Language | Java 21                            |
| Backend Framework    | Spring Boot                        |
| API Architecture     | RESTful API                        |
| Database             | H2 Database                        |
| Database Access      | Spring Data JPA / Hibernate        |
| Build Tool           | Maven                              |
| Testing              | JUnit 5, Mockito, Spring Boot Test |
| API Documentation    | OpenAPI / Swagger UI               |
| Version Control      | Git and GitHub                     |
| Authentication       | Spring Security, server-side sessions and adaptive password hashing |
| Email                | SMTP adapter; fake delivery in automated tests |

H2 must be used as the default database to minimize local storage requirements and avoid installing additional database servers.

PostgreSQL may be introduced in a later version.

Docker, Redis and Kafka are not required. Email delivery requires an SMTP service configured through environment variables; tests use a fake adapter.

Repository alignment note: the checked-in Maven dependencies and application.properties currently target PostgreSQL with ddl-auto=none. The existing H2 default remains the planned baseline for this authentication-only specification revision. M0 must reconcile the runtime configuration with that baseline before claiming startup or database verification.

The application must be executable locally through Maven and must not require a frontend.

---

## 3. Functional Requirements

### FR-01: Account Creation

The system must accept email, password and displayName for registration. It must send an OTP to the supplied email and create the verified user and exactly one bank account only after successful OTP verification. This creates the first account. After login, the verified user may create additional accounts using POST /api/accounts with accountHolderName; the owner comes from the session, and no new email identity or registration OTP is required.

Each account must contain:

* A unique account ID.
* A required user reference (one user may own multiple accounts; user_id is not unique).
* An account holder name, initialized from the user display name.
* An account balance.
* A currency code.
* Account creation timestamp.

Business rules:

* Each account must have a unique identifier generated by the system.
* The initial balance must be zero.
* Account balances must never be negative.
* Currency must be VND in the initial implementation.
* Account balances must be represented using BigDecimal in Java and an appropriate fixed-precision decimal type in the database.
* Monetary values must never be represented using float or double.
* Monetary amounts must use whole VND values (scale 0).
* Account holder names must not be empty.
* User creation, account creation and registration OTP consumption must commit atomically; failures must leave none of these changes partially applied.

### FR-02: Account Balance Inquiry

The system must allow users to retrieve the current balance of an account.

Business rules:

* An account must exist before its balance can be retrieved.
* If an account does not exist, the system must return an appropriate error.
* The response must contain the account ID, current balance, and currency.

### FR-03: Deposit

The system must allow users to deposit money into an existing account.

Business rules:

* The deposit amount must be greater than zero.
* The target account must exist.
* The deposit amount must be a whole VND value.
* The account balance must increase by the deposit amount.
* The operation must be executed within a database transaction.
* A successful deposit must generate a transaction record.
* A failed deposit must not modify the account balance.

### FR-04: Withdrawal

The system must allow users to withdraw money from an existing account.

Business rules:

* The withdrawal amount must be greater than zero.
* The account must exist.
* The withdrawal amount must be a whole VND value.
* The account must have sufficient available balance.
* The account balance must decrease by the withdrawal amount.
* The balance must never become negative.
* A successful withdrawal must generate a transaction record.
* If the operation fails, the original balance must remain unchanged.

### FR-05: Fund Transfer

The system must allow users to transfer money between two internal accounts.

Business rules:

* The source and destination accounts must exist.
* The source and destination accounts must be different.
* The transfer amount must be greater than zero and must be a whole VND value.
* The source account must have sufficient funds.
* The source account must be debited by the transfer amount.
* The destination account must be credited by exactly the same amount.
* Both balance updates must occur within the same database transaction.
* If either balance update fails, the entire operation must be rolled back.
* A successful transfer must generate a transaction record containing the source account, destination account, amount, timestamp, and transaction status.
* The sum of the balances of the two accounts must remain unchanged after a successful transfer.

Example:

Initial state:

Account A: 1,000,000 VND
Account B: 500,000 VND

Transfer:

Account A transfers 200,000 VND to Account B.

Expected result:

Account A: 800,000 VND
Account B: 700,000 VND

Total balance before and after the transfer: 1,500,000 VND.

### FR-06: Transaction History

The system must allow users to retrieve the transaction history of a specific account.

Each transaction record must contain:

* Transaction ID.
* Transaction type: DEPOSIT, WITHDRAWAL, or TRANSFER.
* Source account ID, where applicable.
* Destination account ID, where applicable.
* Transaction amount.
* Currency.
* Transaction status.
* Transaction creation timestamp.

Business rules:

* Transactions must be ordered by creation time, newest first, with transaction ID used as a deterministic secondary ordering key.
* Transaction history must support pagination.
* A transfer must be visible in the transaction history of both participating accounts.
* Successfully committed transactions must have a SUCCESS status.
* Rejected requests that do not create or execute a financial transaction do not need to appear in the transaction history.
* Transaction history must be read-only through the public API.

### FR-07: Idempotent Fund Transfers

The system must prevent duplicate fund transfers caused by repeated requests.

The transfer API must accept an Idempotency-Key supplied by the client.

Business rules:

* The first request with a new idempotency key must execute the transfer normally.
* Repeating a completed request with the same key and identical request parameters must return the previously recorded result without transferring money again.
* Reusing the same key with different transfer parameters must be rejected.
* Concurrent requests using the same key must not execute duplicate transfers.
* Each idempotency key must be unique within the transfer operation namespace and must have a unique database constraint.
* The system must persist the request identity and completed transfer result for successful transfers.
* Idempotency records and the corresponding financial transaction must be committed atomically.
* A failed or rolled-back attempt must not permanently block a valid retry.

The initial implementation may retain successful idempotency records indefinitely. Automatic expiration is outside the project scope.

### FR-08: Concurrent Transaction Processing

The system must correctly handle concurrent operations affecting the same account.

Example:

An account contains 1,000,000 VND.

Two concurrent requests attempt to withdraw 800,000 VND each.

Expected result:

* Only one withdrawal may succeed.
* The other withdrawal must be rejected because of insufficient funds or an explicitly handled concurrency conflict.
* The final account balance must be 200,000 VND.
* The account balance must never become negative.
* The system must not lose balance updates.

The implementation must use an appropriate database concurrency control mechanism, such as pessimistic locking, optimistic locking with retry handling, or an atomic conditional update.

For transfers involving two accounts, the implementation must use a consistent locking order to reduce deadlock risk.

Database transaction boundaries alone must not be assumed to prevent lost updates.

### FR-09: User Registration and Email Login

* Registration must accept only email, password and displayName; login must accept only email and password. There is no username field.
* Normalize email by trimming and lowercasing with locale-independent rules. Enforce valid syntax, maximum 254 characters and database uniqueness of the normalized value. Do not rewrite provider-specific dots or plus tags.
* Names are trimmed, nonblank, at most 100 characters and need not be unique. They are for display only.
* Passwords contain 8-128 characters, are not trimmed or truncated, and are stored only as adaptive password hashes.
* A pending registration is not a user and cannot log in. Successful OTP confirmation creates one verified user and one account. Login is a separate action.
* Unknown email and incorrect password return the same 401 INVALID_CREDENTIALS. A verified duplicate email returns 409 EMAIL_ALREADY_REGISTERED, including case/space variants.
* Use server-side sessions, rotate session IDs on login, expire sessions after 30 idle minutes and invalidate the current session on logout. Use HttpOnly/SameSite=Lax cookies, Secure over HTTPS, and CSRF protection for authenticated mutations.
* Login and current-password checks permit at most 5 failed attempts per canonical email in a rolling 15-minute window, including unknown emails; excess attempts return 429 with Retry-After.

### FR-10: Email OTP Verification

* Registration and password change require separate, purpose-bound, cryptographically random six-digit email OTPs. Store a keyed digest, never the plaintext code; keep its key outside version control.
* Each code expires after 5 minutes, permits at most 5 incorrect attempts and can succeed only once. Failure counters must persist on rejected verification; concurrent requests must not bypass limits or consume a code twice.
* All issuance routes share a 60-second cooldown and 5-code limit per normalized email/purpose per fixed one-hour window. Persist issuance counters even on SMTP failure; resend does not reset the window.
* Resend rotates both challengeId and code. A new allowed registration for an unregistered email replaces pending details and invalidates the previous challenge; an existing user cannot be overwritten.
* SMTP acceptance precedes a successful 202 response. On failure return 503 EMAIL_DELIVERY_FAILED; the candidate is unusable and any previously active challenge remains usable. SMTP acceptance does not guarantee inbox delivery.
* APIs, logs and URLs must not expose OTPs, passwords or hashes. Tests obtain codes through a fake email adapter; an email provider is not required in automated tests.

### FR-11: Verified Password Change

* A signed-in user requests an OTP using currentPassword. The email recipient is taken from that user's stored email, never from request input.
* Confirmation accepts challengeId, otp and newPassword; it requires the same authenticated user, PASSWORD_CHANGE purpose, current credential version and a valid password.
* Password hash update, credentialVersion increment and OTP consumption are atomic. All old sessions and password-change challenges are invalid after success; a new login is required.
* Wrong current password, invalid OTP or persistence failure must not partially change credentials.
* Forgotten-password recovery, email changes and social login are outside this revision.

### FR-12: Account Ownership

* Account and transaction routes require an authenticated user.
* Users may read account details, balances and history, deposit into and withdraw from only accounts they own.
* A transfer source must belong to the caller; the destination may belong to another user. Transaction details are visible only to owners of a participating account.
* Check authorization before idempotency lookup or replay; a saved result must not disclose another user's transaction.
* Missing sessions return 401 AUTHENTICATION_REQUIRED; access to an existing unowned resource returns 403 ACCESS_DENIED. Unknown resources retain their existing 404 codes. Missing/invalid CSRF tokens on authenticated mutations return 403 CSRF_INVALID.

---

## 4. REST API Requirements

All API endpoints must use the `/api` prefix.

### Authentication API

| HTTP Method | Endpoint | Request / behavior |
| --- | --- | --- |
| GET | /api/auth/csrf | Obtain CSRF token for the current anonymous/authenticated session |
| POST | /api/auth/register | email, password, displayName; send OTP, return 202 |
| POST | /api/auth/register/verify | challengeId, otp; create User + Account, return 201 |
| POST | /api/auth/otp/resend | challengeId; replace OTP, return 202 |
| POST | /api/auth/login | email, password; return User and session cookie, 200 |
| POST | /api/auth/logout | Invalidate current session, 204 |
| POST | /api/auth/password-change/request | currentPassword; send OTP to session user's email, 202 |
| POST | /api/auth/password-change/confirm | challengeId, otp, newPassword; update password, invalidate sessions, 204 |

### Account API

| HTTP Method | Endpoint                        | Description                            |
| ----------- | ------------------------------- | -------------------------------------- |
| POST        | /api/accounts                   | Open another account for the authenticated user |
| GET         | /api/accounts/{id}              | Retrieve account information           |
| GET         | /api/accounts/{id}/balance      | Retrieve current account balance       |
| GET         | /api/accounts/{id}/transactions | Retrieve paginated transaction history |

### Transaction API

| HTTP Method | Endpoint                   | Description                     |
| ----------- | -------------------------- | ------------------------------- |
| POST        | /api/transactions/deposit  | Deposit money                   |
| POST        | /api/transactions/withdraw | Withdraw money                  |
| POST        | /api/transactions/transfer | Transfer money between accounts |
| GET         | /api/transactions/{id}     | Retrieve transaction details    |

The transfer endpoint must require the `Idempotency-Key` HTTP header. All POST routes require X-CSRF-TOKEN obtained from /api/auth/csrf with its session cookie, including registration and login. Refresh the CSRF token after login. Protected routes check authentication first, then CSRF and ownership as applicable.

All APIs must accept and return JSON, except endpoints where no response body is appropriate.

The system must use appropriate HTTP status codes, including:

* 200 OK: Successful retrieval or completed operation.
* 201 Created: OTP-confirmed user/account creation or newly created transaction.
* 202 Accepted: OTP accepted by the mail server.
* 204 No Content: Logout or password change completed.
* 401 Unauthorized: Missing session or invalid credentials.
* 403 Forbidden: Resource ownership or CSRF check failed.
* 400 Bad Request: Invalid input.
* 404 Not Found: Requested account or transaction does not exist.
* 409 Conflict: Duplicate registered email, idempotency key reused with different parameters or an explicitly reported concurrency conflict.
* 429 Too Many Requests: OTP or credential-attempt limit exceeded (Retry-After required).
* 503 Service Unavailable: OTP email delivery failed.
* 422 Unprocessable Content: Insufficient funds or a business rule violation.

Errors must return consistent JSON responses containing an error code, human-readable message, and timestamp.

The application must not expose internal stack traces in public API responses.

---

## 5. Database Design

The target model includes five entities: User, Account, PaymentTransaction, IdempotencyRecord and EmailOtpChallenge. See [the complete Mermaid ERD](../database-erd.md). User and its one-to-many Account association are implemented as entities; EmailOtpChallenge and authentication/OTP services remain planned.

### 5.1 Account

| Field             | Description                                   |
| ----------------- | --------------------------------------------- |
| id                | Unique account identifier                     |
| userId        | Required nonunique FK to User; many accounts per user |
| accountHolderName | Snapshot of displayName taken at registration |
| balance           | Current account balance                       |
| currency          | Currency code, initially VND                  |
| createdAt         | Account creation timestamp                    |
| version           | Optional version field for optimistic locking |

### 5.2 Transaction

| Field                | Description                                   |
| -------------------- | --------------------------------------------- |
| id                   | Unique transaction identifier                 |
| type                 | DEPOSIT, WITHDRAWAL, TRANSFER                 |
| sourceAccountId      | Source account, nullable for deposits         |
| destinationAccountId | Destination account, nullable for withdrawals |
| amount               | Transaction amount                            |
| currency             | Currency code                                 |
| status               | Transaction status                            |
| createdAt            | Transaction timestamp                         |

### 5.3 IdempotencyRecord

| Field              | Description                               |
| ------------------ | ----------------------------------------- |
| id                 | Unique record identifier                  |
| idempotencyKey     | Unique request key                        |
| requestFingerprint | Identity of the original transfer request |
| transactionId      | Reference to the completed transaction    |
| createdAt          | Record creation timestamp                 |

### 5.4 User

| Field | Description |
| --- | --- |
| id | UUID primary key |
| email | Canonical email, VARCHAR(254), NOT NULL UNIQUE |
| displayName | Nonblank name, VARCHAR(100); not unique and not a login identifier |
| passwordHash | Encoded adaptive password hash; never returned to clients |
| emailVerifiedAt | Required verification timestamp; only verified users exist |
| credentialVersion | Nonnegative integer, initially 0; invalidates sessions and pending password changes |
| createdAt | UTC creation timestamp |

### 5.5 EmailOtpChallenge

| Field | Description |
| --- | --- |
| id | Stable UUID primary key for the email/purpose slot |
| challengeId | UNIQUE nullable random UUID rotated on issuance; absent before first successful activation |
| email, purpose | Required canonical recipient and REGISTRATION/PASSWORD_CHANGE; composite UNIQUE |
| userId, credentialVersion | Required for PASSWORD_CHANGE; null for REGISTRATION |
| pendingDisplayName, pendingPasswordHash | Required for active REGISTRATION; null for PASSWORD_CHANGE; erased after successful registration |
| otpDigest | Keyed code/challenge digest; null if inactive or consumed |
| expiresAt, consumedAt | Code expiry and optional consumption timestamp |
| failedAttempts | 0-5, persisted even when verification returns an error |
| lastSentAt | Time of last issuance reservation; used for the cooldown, including failed sends |
| windowStartedAt, windowSendCount | Persistent one-hour issuance window and count (0-5), including failed sends |
| createdAt | UTC slot creation timestamp |

A challenge can exist before a user. There is no FK from registration email to User.email. Account.userId is NOT NULL and indexed, without a UNIQUE constraint. Each registration transaction creates a user and their first account; authenticated users may then create more accounts. Each account has exactly one immutable owner. Session storage uses the security framework, not an additional business entity. Details, indexes and state-dependent checks are defined in the design and ERD.

The application must maintain appropriate database constraints for monetary values, account references, idempotency keys, and data integrity.

The initial database schema should remain simple and should not introduce unnecessary entities or relationships.

---

## 6. Non-Functional Requirements

### NFR-01: Data Consistency

Financial transactions must maintain consistent account balances.

No successfully committed transfer may debit an account without crediting the destination account.

### NFR-02: Reliability

Invalid or failed operations must not result in partial balance updates.

Database exceptions must trigger appropriate transaction rollback.

### NFR-03: Maintainability

The project must follow a layered architecture:

Controller → Service → Repository → Database.

Business logic must be implemented in the Service layer rather than in Controllers.

Controllers must handle HTTP requests, validation, and response mapping.

DTOs must be used for API requests and responses rather than exposing JPA entities directly.

### NFR-04: Testability

Core financial business logic must be covered by automated tests.

Tests must include both successful and unsuccessful operations.

### NFR-05: Lightweight Development

The application must be runnable on a local development machine without Docker or a separately installed database server.

H2 must be used as the default database.

The project must avoid unnecessary dependencies and infrastructure components.

### NFR-06: Security and Input Validation

All request inputs must be validated.

Sensitive configuration values must not be committed to GitHub.

The application must not store real banking credentials or personal financial information.

Email/password authentication, email OTP verification and ownership authorization are required. Passwords use adaptive hashing; OTPs use keyed digests; SMTP credentials and digest keys are environment secrets. This remains a local demonstration with simulated funds.

---

## 7. Testing Requirements

The project must include automated tests for the following scenarios:

| Test ID | Test scenario                                                                    | Expected result                                  |
| ------- | -------------------------------------------------------------------------------- | ------------------------------------------------ |
| TC-01   | Register and confirm a valid email OTP | Exactly one user and zero-balance account created |
| TC-02   | Deposit a positive amount                                                        | Balance increases correctly                      |
| TC-03   | Deposit zero or a negative amount                                                | Request rejected                                 |
| TC-04   | Withdraw an amount within the available balance                                  | Balance decreases correctly                      |
| TC-05   | Withdraw more than the available balance                                         | Request rejected; balance unchanged              |
| TC-06   | Transfer between valid accounts                                                  | Both balances updated correctly                  |
| TC-07   | Transfer to the same account                                                     | Request rejected                                 |
| TC-08   | Transfer to a nonexistent account                                                | Request rejected; balances unchanged             |
| TC-09   | Simulate a failure during a transfer                                             | Entire transfer rolled back                      |
| TC-10   | Repeat a successful transfer with the same idempotency key and identical request | Original result returned; no additional transfer |
| TC-11   | Reuse an idempotency key with different transfer parameters                      | Request rejected; no additional transfer         |
| TC-12   | Execute concurrent withdrawals exceeding the available balance                   | No overdraft or lost update                      |
| TC-13   | Retrieve transaction history                                                     | Correct records and pagination                   |
| TC-14   | Execute concurrent requests with the same idempotency key                        | Only one financial transfer is committed         |
| TC-15 | Register an email again, including case/space variants and concurrent confirmation | At most one user/account; duplicate rejected |
| TC-16 | Log in with email/password; try display name, wrong password and pending registration | Only valid verified email credentials establish a session |
| TC-17 | Verify wrong, expired, consumed, replaced or wrong-purpose OTP | Rejected without account/password changes; attempts persist |
| TC-18 | Resend/issue concurrently or above limits; simulate SMTP failure | Limits persist; old challenge preserved on failure; no usable candidate |
| TC-19 | Change password with current password and OTP | New password works; old password, sessions and old-version challenges fail |
| TC-20 | Fail password persistence or confirm OTP concurrently | Atomic rollback; at most one successful consumption |
| TC-21 | Access unowned resources, omit session/CSRF, or replay another user's transfer | 401/403 and no financial change or saved-result disclosure |
| TC-22 | Log out, expire a session or exceed credential-attempt limits | Session unusable; credential throttling returns 429 with Retry-After |
| TC-23 | Persist multiple accounts for one user; attempt an absent/unknown owner | Multiple accounts succeed; invalid ownership fails |
| TC-24 | Open an additional account as a verified signed-in user | New zero-balance account shares the owner; no second user identity |

JUnit 5 and Spring Boot Test must be used for automated testing.

Mockito may be used for isolated unit tests.

Integration tests must verify transaction behavior against an actual test database rather than relying solely on mocked repositories.

---

## 8. Development Roadmap

### Phase 1 – Minimum Viable Product

Implement:

* Email/password registration, email OTP verification, login/logout, verified password change and ownership checks.
* Account creation during registration and balance retrieval.
* Deposit and withdrawal.
* Internal fund transfers.
* Transaction history.
* Input validation.
* Database transaction management.
* Unit tests and integration tests.
* OpenAPI documentation.

The MVP must be functional before advanced features are introduced.

### Phase 2 – Advanced Transaction Processing

Implement:

* Idempotency keys for fund transfers.
* Concurrency control.
* Concurrent transaction testing.
* Improvements to exception handling and transaction consistency.

### Phase 3 – Optional Enhancements

Only after the first two phases are complete:

* PostgreSQL database support.
* Docker configuration.
* Redis integration for selected noncritical caching use cases.
* Additional performance and load testing.
* Improved observability and structured logging.

These enhancements are optional and must not delay the completion of the core project.

---

## 9. Acceptance Criteria

The project is considered complete when:

1. The application starts successfully on a local development machine.
2. All required REST endpoints are functional.
3. Account balances remain correct after deposits, withdrawals, and transfers.
4. Failed transactions do not create partial balance changes.
5. Duplicate transfer requests do not cause duplicate financial transactions.
6. Concurrent operations do not cause negative balances or lost updates.
7. Transaction history correctly reflects committed operations.
8. Required automated tests pass.
9. API documentation is accessible through Swagger UI.
10. The GitHub repository contains the source code, README, setup instructions, API examples, and test documentation.
11. Email uniqueness, OTP-confirmed registration and email/password-only login are verified.
12. Password changes require current credentials and email OTP, invalidate old sessions and pass failure/race tests.
13. Ownership, CSRF, OTP limits, credential throttling and SMTP failure behavior pass TC-15 through TC-22.
14. A user can own multiple accounts with a required owner FK; additional account opening passes TC-23/TC-24.

---

## 10. AI Coding Agent Development Instructions

The AI Coding Agent must follow these rules:

1. Read this specification before generating or modifying source code.
2. Follow Spec-Driven Development. Implement only requirements described in the specification or explicitly approved by the developer.
3. Do not introduce frontend frameworks, microservices, Docker, cloud services, or external payment integrations without explicit approval.
4. Prioritize correctness of financial operations over adding new features.
5. Use BigDecimal for all monetary calculations. Never use float or double to represent money.
6. Keep business logic in the Service layer.
7. Use DTOs for API input and output.
8. Apply database transactions and suitable concurrency control to balance-changing operations.
9. Add appropriate unit tests and integration tests for each financial operation.
10. Do not silently swallow exceptions or convert failed financial transactions into successful responses.
11. Avoid unnecessary dependencies, excessive abstraction, and premature optimization.
12. Keep the project lightweight and suitable for local development.
13. Explain important architectural decisions and identify any assumptions when a requirement is ambiguous.
14. Do not claim a requirement is implemented until the relevant code has been completed and its behavior has been verified through tests.
15. Preserve existing working functionality when implementing new features.

The AI Coding Agent must implement the project incrementally, starting with the MVP and proceeding to advanced functionality only after the core features have been implemented and verified.
