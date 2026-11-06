# Named account management

Each verified user can own multiple zero-balance VND accounts. OTP confirmation
already creates the first account atomically with the user; its accountName is
exactly `tài khoản thanh toán 1`. accountHolderName is the user's displayName at
account creation. These are separate fields: editing the account label does not
change the holder, user identity or balance.

## Setup and Swagger walkthrough

Use Java 21 and apply outstanding migrations through
[005_idempotency_operation_namespaces.sql](../database/005_idempotency_operation_namespaces.sql) before starting the
updated application. Follow [database upgrade instructions](../database/README.md)
and [authentication setup](security-authentication.md).

1. Open `/swagger-ui/index.html` on the same host used for all requests.
2. Obtain the anonymous token with `GET /api/auth/csrf`; retain the session cookie.
3. Register with email/password/displayName and confirm the emailed OTP using
   that token. The 201 response includes the first named account and its Location.
4. Log in separately with email/password, then fetch a fresh CSRF token because
   login rotates the session/token.
5. Execute `POST /api/accounts` with the current token and the following body:

```json
{"accountName":"Tiết kiệm"}
```

The response is 201 with Location `/api/accounts/{id}` and an Account DTO:

```json
{
  "id":"20000000-0000-0000-0000-000000000001",
  "userId":"10000000-0000-0000-0000-000000000001",
  "accountHolderName":"Demo User",
  "accountName":"Tiết kiệm",
  "accountNumber":"100000000001",
  "balance":0,
  "currency":"VND",
  "createdAt":"2026-10-12T10:00:00Z"
}
```

IDs and timestamps above are illustrative; use the returned UUID in real calls.
Creation takes ownership from the current session and holder from displayName;
no additional email, password or OTP is accepted or generated.

6. Execute `GET /api/accounts?page=0&size=20`. It returns `items`, `page`, `size`,
   `totalElements`, `totalPages`, all restricted to the session user. Items are
   ordered by createdAt descending, then UUID descending. `page` must be a
   nonnegative integer; `size` is 1-100. Defaults are 0 and 20. Pages beyond the
   end have empty items; reads never auto-create accounts.
7. Execute `PATCH /api/accounts/{id}` with the current CSRF token and:

```json
{"accountName":"Chi tiêu"}
```

The response is 200 with the updated Account. Only the label changes. GET detail
and list show the new label; balance, holder, ownership and creation time remain
unchanged. Concurrent renames use the last successful database write.

The manual X-CSRF-TOKEN input remains available during functional development,
including for PATCH. Automatic Swagger token handling stays deferred until M7.

## Validation and access

- Names must be strings, stripped, nonblank and at most 100 characters. Vietnamese
  names and duplicates are accepted. UUID distinguishes accounts; no account-count
  limit applies in this increment.
- POST/PATCH accept only accountName. Missing/invalid input or additional fields
  such as userId, accountHolderName, balance, currency or credentials return
  `400 INVALID_REQUEST`. Invalid UUIDs and pagination also return 400.
- All account routes require login, including direct URL/API requests. Missing,
  expired, logged-out or revoked sessions return `401 AUTHENTICATION_REQUIRED`
  before CSRF or body validation. An anonymous CSRF token is not authentication.
- Authenticated POST/PATCH with missing, invalid or stale CSRF return
  `403 CSRF_INVALID`; no account is created or changed.
- Accessing or renaming another user's account returns `403 ACCESS_DENIED`.
  Unknown valid UUIDs return `404 ACCOUNT_NOT_FOUND`.
- Swagger and user registration remain public. Seeing API documentation does not
  grant permission to execute protected operations.

## Migration and compatibility

Migration 003 adds account_name and names existing accounts
`tài khoản thanh toán N`, starting at 1 for each user in created_at ASC/id ASC
order. It preserves existing data and enforces required, nonblank labels without
a uniqueness constraint. Run it once after 001/002; keep Hibernate ddl-auto=none.
Account DTOs now include accountName. The previously planned, unimplemented POST
contract accepting accountHolderName is replaced by accountName only.

Run `mvn verify` on Java 21 with Docker available. AccountIntegrationTest covers
the new endpoints and denials; AccountMigrationTest verifies the real SQL upgrade
and schema constraints; AuthIntegrationTest and AuthRestartTest cover registration,
rollback, concurrent OTP verification, session revocation, restart and real expiry.

Migration 004 additionally assigns each account an immutable unique 12-digit accountNumber. Account UUIDs remain resource identifiers; transfers address recipients by accountNumber. Clients cannot choose or change it. See [financial operations](financial-operations.md).
