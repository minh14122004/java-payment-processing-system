# Incremental Implementation Roadmap

Current status: **specifications only; implementation has not started**. `pom.xml` is currently empty, so application startup and test commands will become available after M0. The active change is [mini-payment-system](../openspec/changes/mini-payment-system/proposal.md); [tasks.md](../openspec/changes/mini-payment-system/tasks.md) is the authoritative implementation checklist.

| Milestone | Scope | Dependency | Completion criteria |
| --- | --- | --- | --- |
| M0 | Maven, Spring Boot, H2, error contract, Swagger | — | Local startup verified and smoke tests pass |
| M1 | Account creation, retrieval and balance | M0 | TC-01 and validation/404 tests pass |
| M2 | Deposits, withdrawals and basic locking | M1 | TC-02–05, rollback and monetary boundary tests pass |
| M3 | Atomic transfers | M2 | TC-06–09, balance conservation and rollback tests pass |
| M4 | History, transaction details, pagination and MVP documentation | M3 | TC-13 passes and the MVP walkthrough is verified |
| M5 | Idempotency | M4 | TC-10–11 and retry/rollback/replay tests pass |
| M6 | Concurrent processing verification | M5 | TC-12/14, mixed-operation and race-condition tests pass |
| M7 | Full acceptance, documentation and examples | M6 | All 10 SRS acceptance criteria are met and the complete test suite passes |

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
3. Keep tasks and traceability aligned. Preserve the original SRS v1.0; document changes to source requirements separately rather than overwriting the source.
4. Run `openspec validate mini-payment-system --strict`. Once implementation exists, run the affected tests as well.
5. Mark a task complete only after its implementation and corresponding verification are complete. Specification validation does not demonstrate runtime correctness.

Keep delta specifications in the active change until implementation is verified and the change is archived. `openspec/specs/` will then contain the canonical specifications. The root `specs/` directory is currently empty and is not used for duplicate copies, avoiding divergence.

## Optional backlog after M7

PostgreSQL, Docker, Redis for noncritical caching, load testing, and improved observability and structured logging remain optional. Create separate changes when needed; these enhancements do not add dependencies or mandatory tasks to the initial release.
