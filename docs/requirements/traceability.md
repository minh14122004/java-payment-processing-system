# Requirements and Test Traceability

Source: [SRS v1.0](SRS-v1.0.md). All tests below are **planned; no implementation or passing results exist yet**. Actual test file paths will be added during implementation. Specifications: [mini-payment-system/specs](../../openspec/changes/mini-payment-system/specs).

| SRS requirement | Capability | Milestone | Planned evidence |
| --- | --- | --- | --- |
| FR-01 | account-management | M1 | TC-01; blank/oversized names, zero balance, VND and UUID |
| FR-02 | account-management | M1 | Account/balance retrieval, 404 and malformed UUID |
| FR-03 | cash-operations | M2 | TC-02/03; missing account, fractional amount, overflow and rollback |
| FR-04 | cash-operations | M2 | TC-04/05; exact-balance withdrawal, fractional amount and rollback |
| FR-05 | fund-transfers | M3 | TC-06–09; balance conservation, missing source and overflow |
| FR-06 | transaction-history | M4 | TC-13; details/404, tied timestamps, pagination and both participants |
| FR-07 | transfer-idempotency | M5/M6 | TC-10/11/14; retry after failure, normalized identity and persisted replay |
| FR-08 | concurrent-processing | M2/M3/M6 | TC-12/14; concurrent deposits, mixed writes, opposite-direction transfers and timeout |
| REST §4 | api-contract | M0–M7 | Eight endpoints, DTOs/Location, validation, HTTP status/error JSON and OpenAPI |
| DB §5 | Account, cash, transfer and idempotency specifications; design §4 | M1/M2/M5 | PK/FK/UNIQUE/CHECK constraints, nullable account references and exact decimals |
| NFR-01/02 | cash-operations, fund-transfers, concurrent-processing | M2–M6 | Balance invariants and rollback after partial writes or unique-key conflicts |
| NFR-03 | local-backend-foundation | M0–M7 | Review of Controller/Service/Repository responsibilities and DTO boundaries |
| NFR-04 | local-backend-foundation | M1–M7 | Unit and H2 integration tests covering success and failure paths |
| NFR-05 | local-backend-foundation | M0/M7 | Local startup using only Java, Maven and embedded H2 |
| NFR-06 | api-contract, local-backend-foundation | M0–M7 | Input validation, loopback binding and exclusion of secrets and real financial data |
| Agent instructions §10 | Design, roadmap and tasks | M0–M7 | Specification-driven development, scoped changes and relevant tests before task completion |

## Required test scenarios

| ID | Scenario | Milestone / task |
| --- | --- | --- |
| TC-01 | Create a valid account with zero balance | M1 / 2.4 |
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

## SRS §9 acceptance criteria

| AC | Required evidence | Milestone |
| --- | --- | --- |
| 1 | Successful local startup using README instructions | M0/M7 |
| 2 | Contract/integration tests for all eight routes | M7 |
| 3 | TC-02/04/06 and exact monetary boundary tests | M2/M3 |
| 4 | TC-05/08/09 and transaction/idempotency record persistence failures | M2/M3/M5 |
| 5 | TC-10/11/14, with only one financial operation applied | M5/M6 |
| 6 | TC-12/14 and mixed/concurrent balance updates | M6 |
| 7 | TC-13 and history containing committed transactions only | M4/M6 |
| 8 | Successful Maven verification and recorded test evidence | M7 |
| 9 | Accessible Swagger UI matching the API contract | M0/M7 |
| 10 | Source code, README, setup instructions, API examples and test documentation ready in the repository for GitHub publication | M7 |

Pushing or publishing to GitHub is outside the current specification preparation step.
