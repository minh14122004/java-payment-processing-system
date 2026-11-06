-- Apply once after 004, with the application stopped. Never resets business data.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';
ALTER TABLE public.idempotency_records
    ADD COLUMN operation VARCHAR(10) NOT NULL DEFAULT 'TRANSFER',
    ADD CONSTRAINT ck_idempotency_operation CHECK (operation IN ('TRANSFER', 'WITHDRAWAL'));
ALTER TABLE public.idempotency_records
    DROP CONSTRAINT uq_idempotency_key,
    ADD CONSTRAINT uq_idempotency_operation_key UNIQUE (operation, idempotency_key);
COMMIT;
