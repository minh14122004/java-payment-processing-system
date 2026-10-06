# Mini Payment Processing System

A Java backend portfolio project that simulates account management and financial transactions in VND. The stack is Java 21, Spring Boot, Maven and PostgreSQL 17, with H2 for isolated tests.

The project is designed to demonstrate REST API development, layered architecture, exact monetary calculations, database transactions, idempotency, concurrency control and automated testing.

**Status: User and one-to-many Account entities implemented; authentication/OTP services pending.** User stores unique canonical email, display name and an already encoded password hash. Account requires an owner; multiple accounts can share the same user. Focused tests cover validation and persistence using an isolated H2 database; production startup remains unverified. The five-table PostgreSQL schema was created and constraint-tested on 2026-10-06 in the existing Docker database. See [database setup and verification](database/README.md).

Registration accepts email, password and displayName. Planned email OTP confirmation creates one user and their first zero-balance bank account; authenticated users may open additional accounts. Login uses email/password only; email is unique after normalization. Password changes require a session, current password and email OTP. Account/transaction access requires ownership. SMTP delivery and authentication are specified, not yet implemented.

## Documentation

- [SRS revision 1.2 (retained filename)](docs/requirements/SRS-v1.0.md): project scope and source requirements.
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

The specifications remain open to revision. Completed OpenSpec artifacts indicate that planning documents are ready for implementation. Run `mvn test` for entity validation and isolated H2 persistence checks. Runtime configuration and API examples remain to be verified as services are implemented.

This application is a local educational demonstration using synthetic data. It does not process real money. Frontend implementation and payment gateway integrations are outside the initial scope. Email/password authentication and SMTP OTP delivery are included; tests will use fake email delivery. Forgotten-password recovery and username login are excluded.
