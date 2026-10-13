-- PostgreSQL 17. Apply once after 003; existing identifiers and money are preserved.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';
LOCK TABLE public.accounts IN ACCESS EXCLUSIVE MODE;

CREATE SEQUENCE public.account_number_seq AS BIGINT
    MINVALUE 100000000000 MAXVALUE 999999999999 START WITH 100000000000 NO CYCLE;
ALTER TABLE public.accounts ADD COLUMN account_number VARCHAR(12);
WITH numbered AS (
    SELECT id, (100000000000 + row_number() OVER (ORDER BY created_at, id) - 1)::text AS number
    FROM public.accounts
)
UPDATE public.accounts a SET account_number = n.number FROM numbered n WHERE a.id = n.id;
SELECT setval('public.account_number_seq',
    COALESCE((SELECT max(account_number::bigint) FROM public.accounts), 100000000000),
    EXISTS (SELECT 1 FROM public.accounts));
ALTER TABLE public.accounts ALTER COLUMN account_number SET DEFAULT nextval('public.account_number_seq')::text;
ALTER TABLE public.accounts ALTER COLUMN account_number SET NOT NULL;
ALTER TABLE public.accounts ADD CONSTRAINT uq_accounts_number UNIQUE (account_number);
ALTER TABLE public.accounts ADD CONSTRAINT ck_accounts_number CHECK (account_number COLLATE "C" ~ '^[0-9]{12}$');
ALTER SEQUENCE public.account_number_seq OWNED BY public.accounts.account_number;

ALTER TABLE public.payment_transactions ADD COLUMN description VARCHAR(255);
COMMENT ON COLUMN public.payment_transactions.description IS 'Immutable transfer message. NULL is allowed for cash operations and legacy history.';
COMMIT;
