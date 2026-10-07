# Mini Payment Processing System

A Java backend portfolio project that simulates account management and financial transactions in VND. The stack is Java 21, Spring Boot, Maven and PostgreSQL 17, with H2 for isolated tests.

The project is designed to demonstrate REST API development, layered architecture, exact monetary calculations, database transactions, idempotency, concurrency control and automated testing.

**Status: authentication implemented; final verification is recorded in the traceability document.** Registration, email OTP, login/logout, password change, password recovery and owned account/balance reads are implemented. Financial APIs and additional account creation remain later work. See [authentication setup, BCrypt algorithm and API examples](docs/security-authentication.md) and [database migrations](database/README.md).

Registration accepts email, password and displayName. OTP confirmation atomically creates a verified user and their first zero-balance VND account. Login verifies the stored BCrypt/HMAC hash with the same PasswordEncoder. Password changes require a session, current password and email OTP; recovery uses a separate email OTP without the old password. Both revoke old sessions. Automated tests use fake delivery; live SMTP inbox delivery has not been verified.

## Documentation

- [SRS revision 1.3 (retained filename)](docs/requirements/SRS-v1.0.md): project scope and source requirements.
- [Implementation roadmap M0–M7](docs/implementation-roadmap.md): milestone dependencies and incremental development workflow.
- [Proposal](openspec/changes/mini-payment-system/proposal.md): objectives and ten capabilities.
- [Technical design](openspec/changes/mini-payment-system/design.md): architecture, data model, transaction boundaries, concurrency and proposed defaults.
- [Capability specifications](openspec/changes/mini-payment-system/specs): requirements and acceptance scenarios.
- [Implementation checklist](openspec/changes/mini-payment-system/tasks.md): small, verifiable tasks with completion evidence.
- [Requirements traceability](docs/requirements/traceability.md): mappings between functional and non-functional requirements, TC-01 through TC-24 and acceptance criteria.

- [Database ERD](docs/database-erd.md): user ownership, email OTP and financial tables.

## Incremental development

Example request: “Implement only M0 of the mini-payment-system change, run the relevant checks, update the checklist and stop after M0.” Individual tasks within a milestone can also be selected.

Validate the documentation using the OpenSpec CLI:

```text
openspec status --change mini-payment-system
openspec validate mini-payment-system --strict
```

The specifications remain open to revision. Completed OpenSpec artifacts indicate that planning documents are ready for implementation. Run `mvn verify` using Java 21 with Docker running. The suite includes H2 entity checks, isolated PostgreSQL 17 Testcontainers integration/concurrency tests and real HTTP restart/session checks. Export the environment variables documented in `.env.example`; `.env` is not loaded automatically. Keep PASSWORD_PEPPER and OTP_HMAC_KEY fixed across restarts. Full-project M2-M7 acceptance remains pending.

This application is a local educational demonstration using synthetic data. It does not process real money. Frontend implementation and payment gateway integrations are outside the initial scope. Email/password authentication and SMTP OTP delivery are included; tests use fake email delivery. Forgotten-password recovery is included; username login is excluded.
