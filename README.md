# Mini Payment Processing System

A Java backend portfolio project that simulates account management and financial transactions in VND. The planned stack is Java 21, Spring Boot, Maven and H2.

The project is designed to demonstrate REST API development, layered architecture, exact monetary calculations, database transactions, idempotency, concurrency control and automated testing.

**Status: specifications prepared; implementation has not started.** `pom.xml` is currently empty. Application startup and automated tests have not yet been verified.

## Documentation

- [Original SRS v1.0](docs/requirements/SRS-v1.0.md): project scope and source requirements.
- [Implementation roadmap M0–M7](docs/implementation-roadmap.md): milestone dependencies and incremental development workflow.
- [Proposal](openspec/changes/mini-payment-system/proposal.md): objectives and eight capabilities.
- [Technical design](openspec/changes/mini-payment-system/design.md): architecture, data model, transaction boundaries, concurrency and proposed defaults.
- [Capability specifications](openspec/changes/mini-payment-system/specs): requirements and acceptance scenarios.
- [Implementation checklist](openspec/changes/mini-payment-system/tasks.md): small, verifiable tasks; all currently pending.
- [Requirements traceability](docs/requirements/traceability.md): mappings between functional and non-functional requirements, TC-01–TC-14 and acceptance criteria.

## Incremental development

Example request: “Implement only M0 of the mini-payment-system change, run the relevant checks, update the checklist and stop after M0.” Individual tasks within a milestone can also be selected.

Validate the documentation using the OpenSpec CLI:

```text
openspec status --change mini-payment-system
openspec validate mini-payment-system --strict
```

The specifications remain open to revision. Completed OpenSpec artifacts indicate that planning documents are ready for implementation. Maven commands, local configuration and API examples will be added and verified during implementation.

This application is a local educational demonstration using synthetic data. It does not process real money. A frontend, authentication, payment gateway integrations and infrastructure beyond H2 are outside the initial scope.
