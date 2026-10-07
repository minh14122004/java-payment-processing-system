# PostgreSQL schema

On 2026-10-06, `001_initial_schema.sql` was applied to `payment_db.public` in the existing `payment-postgres` container (PostgreSQL 17). All five business tables are present and empty. `verify_schema.sql` passed against that database and rolled back all fixtures.

The runtime database is PostgreSQL. H2 remains a test-only dependency for the existing Java persistence tests. Credentials belong in the environment, not in these scripts.

## Files

- [001_initial_schema.sql](001_initial_schema.sql): versioned initial DDL, one transaction, no destructive resets and no silent skipping of existing tables. Already applied to the current database; do not rerun it there. Apply later schema changes with a new numbered script. Apply 002_auth_password_reset.sql after 001 to support PASSWORD_RESET; do not rerun applied scripts.
- [verify_schema.sql](verify_schema.sql): checks real PostgreSQL constraints using temporary helpers and test rows, then rolls everything back. A failed check must stop execution; the disconnected transaction is rolled back.
- [ERD](../docs/database-erd.md): table structure and relationships.

- [002_auth_password_reset.sql](002_auth_password_reset.sql): non-destructive extension of OTP owner/state constraints and password-hash documentation. Applied to the local payment_db on 2026-10-07; existing rows were preserved.

## Inspect in IntelliJ

Refresh the connected data source and expand `payment_db > public > tables`. The tables are `users`, `accounts`, `payment_transactions`, `idempotency_records` and `email_otp_challenges`.

On a new empty PostgreSQL database, execute all of `001_initial_schema.sql`, then all of `002_auth_password_reset.sql` against the intended data source. It includes BEGIN/COMMIT; do not execute only a selected fragment. An existing table causes an error and the transaction must be rolled back, not bypassed by deleting data.

## Run checks from PowerShell

For this container and database role:

```powershell
Get-Content -Raw database/verify_schema.sql | docker exec -i payment-postgres psql -X -U payment_user -d payment_db -v ON_ERROR_STOP=1 -q -f -
```

For a new empty database only, substitute `database/001_initial_schema.sql` to create the schema. The container's local PostgreSQL socket is used; no extra PostgreSQL installation or GUI is required.

## Enforced constraints and application responsibilities

SQL enforces primary/foreign keys, canonical unique emails, multiple accounts per user, nonnegative balances, positive transaction amounts, VND/SUCCESS values, transaction-type account rules, unique idempotency keys/results, OTP purpose/recipient/state rules and bounded counters. Deletes do not cascade through financial records. UUIDs default to `gen_random_uuid()`; timestamps are `TIMESTAMPTZ` and default to the transaction timestamp where appropriate. Java may supply its own UUIDs and timestamps.

OTP password-change/reset rows use `(user_id, email)` as a composite FK to `users(id, email)`, ensuring the recipient belongs to the referenced user. Registration rows have no user yet. The redundant unique pair on users supports this FK. OTP rate reservations can exist before the first successful delivery. Active codes require their digest/expiry and registration fields; consumption clears secrets while retaining rate metadata. Current credential-version comparison and expiry checks belong to the service, so changing a password does not invalidate historical rows through an FK failure.

Registration must still create the first account atomically with the user. Services must implement ownership authorization, password encoding, OTP delivery/verification, time-based limits, immutable account ownership/history, transaction locks and atomic balance updates. The transfer service must only create idempotency records for transfers and compare canonical fingerprints. These workflows are not implemented by table creation.

`NUMERIC(19,0)` matches the JPA mappings, but PostgreSQL can round fractional values on assignment. Reject fractional VND in Java before SQL, as required by the specs. The schema does not claim to implement that input validation.

The existing `spring.jpa.hibernate.ddl-auto=none` remains unchanged. Hibernate does not create, update or drop these tables automatically. The OTP entity/repository and authentication services are implemented. Testcontainers applies both SQL scripts to a separate PostgreSQL instance; tests never truncate the demo database.
