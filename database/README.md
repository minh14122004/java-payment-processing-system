# PostgreSQL schema

On 2026-10-06, `001_initial_schema.sql` was applied to `payment_db.public` in the existing `payment-postgres` container (PostgreSQL 17); all five business tables were empty at that time. Migration 002 followed on 2026-10-07 and migration 003 on 2026-10-12. The latest verification preserved 1 user, 1 account with 0 VND, and no transaction, idempotency or OTP records. `verify_schema.sql` passed and rolled back all fixtures.

The runtime database is PostgreSQL. H2 remains a test-only dependency for the existing Java persistence tests. Credentials belong in the environment, not in these scripts.

## Files

- [001_initial_schema.sql](001_initial_schema.sql): versioned initial DDL, one transaction, no destructive resets and no silent skipping of existing tables. Already applied to the current database; do not rerun it there. Apply later schema changes with a new numbered script. Apply 002_auth_password_reset.sql after 001 to support PASSWORD_RESET; do not rerun applied scripts.
- [verify_schema.sql](verify_schema.sql): checks real PostgreSQL constraints using temporary helpers and test rows, then rolls everything back. A failed check must stop execution; the disconnected transaction is rolled back.
- [ERD](../docs/database-erd.md): table structure and relationships.

- [002_auth_password_reset.sql](002_auth_password_reset.sql): non-destructive extension of OTP owner/state constraints and password-hash documentation. Applied to the local payment_db on 2026-10-07; existing rows were preserved.

## Inspect in IntelliJ

Refresh the connected data source and expand `payment_db > public > tables`. The tables are `users`, `accounts`, `payment_transactions`, `idempotency_records` and `email_otp_challenges`.

On a new empty PostgreSQL database, execute all of `001_initial_schema.sql`, then `002_auth_password_reset.sql`, then `003_account_names.sql` then `004_account_numbers_and_transfer_descriptions.sql` and `005_idempotency_operation_namespaces.sql` against the intended data source. It includes BEGIN/COMMIT; do not execute only a selected fragment. An existing table causes an error and the transaction must be rolled back, not bypassed by deleting data.

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

The existing `spring.jpa.hibernate.ddl-auto=none` remains unchanged. Hibernate does not create, update or drop these tables automatically. The OTP entity/repository and authentication services are implemented. Testcontainers applies all four SQL scripts to a separate PostgreSQL instance; tests never truncate the demo database.

## Named account migration 003

[003_account_names.sql](003_account_names.sql) adds account_name VARCHAR(100), fills existing accounts with "tài khoản thanh toán N" (row_number per owner by created_at ASC/id ASC), and then enforces NOT NULL/nonblank without UNIQUE. IDs, balances, timestamps, owners, holders and history are preserved. Runtime uses ddl-auto=none; apply outstanding migrations before starting the updated application. Never rerun 001/002 on this existing database or rerun 003 once account_name exists.

For a schema already on 002, copy the UTF-8 SQL file into the container and execute it directly so PowerShell cannot corrupt Vietnamese literals through a text pipe:

```powershell
docker cp database/003_account_names.sql payment-postgres:/tmp/003_account_names.sql
docker exec payment-postgres psql -X -U payment_user -d payment_db -v ON_ERROR_STOP=1 -f /tmp/003_account_names.sql
```

Run verify_schema.sql afterward. It also verifies required/nonblank/length-limited names and duplicate labels, and rolls back all fixtures. AccountMigrationTest exercises the real upgrade with multiple owners, tied timestamps and related financial/OTP records, checking all pre-existing columns for equality.

Applied to the local database on 2026-10-12. Fingerprints of every pre-existing
column in all five tables matched before migration, after migration and after
schema verification. The existing account is now named `tài khoản thanh toán 1`;
its UUID, owner, holder, balance and creation time were preserved. Do not rerun 003
on this database. See [verification evidence](../docs/requirements/traceability.md).

## Account numbers and descriptions: migration 004

Apply [004_account_numbers_and_transfer_descriptions.sql](004_account_numbers_and_transfer_descriptions.sql) once after 003, before starting the new application. It locks accounts, backfills 12-digit numbers globally by created_at/id, establishes the next sequence value, adds NOT NULL/UNIQUE/format constraints, and adds nullable transaction description. No existing IDs, names, owners, balances, timestamps, history, credentials, OTP or idempotency data are changed. Sequence values are not secrets and can have gaps; the sequence does not cycle.

```powershell
docker cp database/004_account_numbers_and_transfer_descriptions.sql payment-postgres:/tmp/004_account_numbers_and_transfer_descriptions.sql
docker exec payment-postgres psql -X -U payment_user -d payment_db -v ON_ERROR_STOP=1 -f /tmp/004_account_numbers_and_transfer_descriptions.sql
docker cp database/verify_schema.sql payment-postgres:/tmp/verify_schema.sql
docker exec payment-postgres psql -X -U payment_user -d payment_db -v ON_ERROR_STOP=1 -f /tmp/verify_schema.sql
```

Verify all original columns before/after migration and after schema checks. Schema-check fixtures roll back; sequence increments do not, so verification may leave harmless account-number gaps. Do not rerun completed migrations or reset local data. Hibernate remains ddl-auto=none. FinancialMigrationTest covers populated upgrades and constraints; the shared PostgreSQL fixture covers a fresh installation.

Applied to local payment_db on 2026-10-13. Per-table fingerprints of all original columns in all five business tables were identical before migration, after migration and after rolled-back schema checks. Do not rerun 004. Verification details are in [traceability](../docs/requirements/traceability.md).

## Migration 005: operation-scoped idempotency

Stop the application and apply [005_idempotency_operation_namespaces.sql](005_idempotency_operation_namespaces.sql) once after 004, then start the upgraded application. The old application must not run after keys can overlap between operations: it performs key-only lookups. New databases run 001-005 in order. Do not rerun scripts or reset existing data.

005 backfills operation to TRANSFER (also the legacy insert default), restricts it to TRANSFER/WITHDRAWAL and replaces uq_idempotency_key with uq_idempotency_operation_key UNIQUE(operation,idempotency_key). It retains every old column value, unique transaction references and FKs. Application reads/writes explicitly specify operation. Verify populated upgrades and pre-existing transfer replay with FinancialMigrationTest; verify current constraints with verify_schema.sql, whose fixtures roll back.

```powershell
docker cp database/005_idempotency_operation_namespaces.sql payment-postgres:/tmp/005_idempotency_operation_namespaces.sql
docker exec payment-postgres psql -X -U payment_user -d payment_db -v ON_ERROR_STOP=1 -f /tmp/005_idempotency_operation_namespaces.sql
Get-Content -Raw database/verify_schema.sql | docker exec -i payment-postgres psql -X -U payment_user -d payment_db -v ON_ERROR_STOP=1 -q -f -
```
