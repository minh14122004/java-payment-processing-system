-- Apply once after 001 and 002, before starting the account-management application.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';

ALTER TABLE public.accounts ADD COLUMN account_name VARCHAR(100);

WITH numbered AS (
    SELECT id, row_number() OVER (PARTITION BY user_id ORDER BY created_at ASC, id ASC) AS ordinal
    FROM public.accounts
)
UPDATE public.accounts a
SET account_name = 'tài khoản thanh toán ' || numbered.ordinal
FROM numbered WHERE a.id = numbered.id;

ALTER TABLE public.accounts
    ALTER COLUMN account_name SET NOT NULL,
    ADD CONSTRAINT ck_accounts_name CHECK (account_name ~ '[^[:space:]]');

COMMIT;
