# Incremental Implementation Roadmap

Current status: **SRS 1.2 authentication revision specified; implementation and verification pending**. Maven configuration, User and financial entities, tests and an application entry point exist. User-to-Account mapping is implemented; authentication/OTP services and whole milestones remain pending. The existing PostgreSQL runtime configuration differs from the planned H2 baseline and must be reconciled in M0. The active change is [mini-payment-system](../openspec/changes/mini-payment-system/proposal.md); [tasks.md](../openspec/changes/mini-payment-system/tasks.md) is the authoritative implementation checklist.

| Milestone | Scope | Dependency | Completion criteria |
| --- | --- | --- | --- |
| M0 | Maven, Spring Boot, planned H2 alignment, Security/Mail dependencies, error contract, Swagger | — | Local startup verified and smoke tests pass |
| M1 | Email registration/OTP/login, password change, sessions, owned account creation/retrieval | M0 | TC-01, TC-15-20/22, TC-23/24, account portion of TC-21 and validation tests pass |
| M2 | Deposits, withdrawals and basic locking | M1 | TC-02–05, rollback and monetary boundary tests pass |
| M3 | Atomic transfers | M2 | TC-06–09, balance conservation and rollback tests pass |
| M4 | History, transaction details, pagination and MVP documentation | M3 | TC-13 passes and the MVP walkthrough is verified |
| M5 | Idempotency | M4 | TC-10–11 and retry/rollback/replay tests pass |
| M6 | Concurrent processing verification | M5 | TC-12/14, mixed-operation and race-condition tests pass |
| M7 | Full acceptance, documentation and examples | M6 | All 14 SRS acceptance criteria are met and the complete test suite passes |

M0–M4 constitute Phase 1; M5–M6 constitute Phase 2; M7 verifies both phases. Each milestone contains smaller tasks and can span multiple development sessions. Phase 1 does not satisfy the complete SRS because transfer deduplication is still pending. Basic account locking is introduced in M2 to protect balances; Phase 2 completes concurrency handling and verification.

## Requesting implementation

Example for the first session:

> Read the SRS and the mini-payment-system change. Implement only M0 (tasks 1.1–1.5), run the relevant checks, update the checklist and stop after M0.

Example for a smaller increment:

> Implement only tasks 2.1–2.2 of mini-payment-system. Read account-management and the relevant shared requirements. Leave endpoints and later milestones for subsequent sessions.

Use `/opsx:apply mini-payment-system` with an explicit milestone or task limit. For incremental development, always state the intended scope when applying the change.

## Revising specifications

1. Read the SRS and the relevant requirement; determine whether the revision clarifies a default or changes a source requirement.
2. Update the corresponding capability specification, retaining SHALL requirements and WHEN/THEN scenarios. Update the design when a technical decision changes.
3. Keep tasks, ERD and traceability aligned. The user-authorized SRS 1.2 revision is maintained in the existing SRS-v1.0.md path; record later revision history there and preserve unrelated user edits.
4. Run `openspec validate mini-payment-system --strict`. Once implementation exists, run the affected tests as well.
5. Mark a task complete only after its implementation and corresponding verification are complete. Specification validation does not demonstrate runtime correctness.

Keep delta specifications in the active change until implementation is verified and the change is archived. `openspec/specs/` will then contain the canonical specifications. The root `specs/` directory is currently empty and is not used for duplicate copies, avoiding divergence.

## Optional backlog after M7

Beyond reconciling the existing PostgreSQL configuration in M0, production PostgreSQL support, Docker, Redis for noncritical caching, load testing, and improved observability and structured logging remain optional. Create separate changes when needed; these enhancements do not add dependencies or mandatory tasks to the initial release.
