-- Run with psql -v ON_ERROR_STOP=1. All fixtures and helpers are rolled back.
BEGIN;
SET LOCAL statement_timeout = '30s';
SET LOCAL lock_timeout = '5s';
SET LOCAL TIME ZONE 'UTC';

CREATE FUNCTION pg_temp.require(ok BOOLEAN, label TEXT) RETURNS VOID LANGUAGE plpgsql AS $$
BEGIN
    IF ok IS DISTINCT FROM TRUE THEN
        RAISE EXCEPTION 'Assertion failed: %', label;
    END IF;
END;
$$;

CREATE FUNCTION pg_temp.reject(statement TEXT, expected_state TEXT, label TEXT)
RETURNS VOID LANGUAGE plpgsql AS $$
BEGIN
    BEGIN
        EXECUTE statement;
    EXCEPTION WHEN OTHERS THEN
        IF SQLSTATE = expected_state THEN RETURN; END IF;
        RAISE EXCEPTION 'Wrong failure for %: % %', label, SQLSTATE, SQLERRM;
    END;
    RAISE EXCEPTION 'Statement unexpectedly succeeded: %', label;
END;
$$;

SELECT pg_temp.require((SELECT count(*) = 5 FROM information_schema.tables
    WHERE table_schema = 'public' AND table_name IN
    ('users','accounts','payment_transactions','idempotency_records','email_otp_challenges')),
    'all five ERD tables exist');

INSERT INTO public.users(id, email, display_name, password_hash, email_verified_at)
VALUES ('10000000-0000-0000-0000-000000000001', 'schema-check@example.test', 'Schema Check', 'test-only-encoded-hash', now());
INSERT INTO public.accounts(id, user_id, account_holder_name) VALUES
    ('20000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', 'First'),
    ('20000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000001', 'Second');
SELECT pg_temp.require((SELECT count(*) = 2 AND bool_and(balance = 0 AND currency = 'VND')
    FROM public.accounts WHERE user_id = '10000000-0000-0000-0000-000000000001'),
    'multiple zero-balance accounts for one user');

SELECT pg_temp.reject($q$INSERT INTO public.users(email,display_name,password_hash,email_verified_at)
    VALUES ('schema-check@example.test','Duplicate','hash',now())$q$, '23505', 'duplicate email');
SELECT pg_temp.reject($q$UPDATE public.users SET email='SCHEMA-CHECK@EXAMPLE.TEST'
    WHERE id='10000000-0000-0000-0000-000000000001'$q$, '23514', 'noncanonical email');
SELECT pg_temp.reject($q$INSERT INTO public.accounts(account_holder_name) VALUES ('No owner')$q$,
    '23502', 'owner is required');
SELECT pg_temp.reject($q$INSERT INTO public.accounts(user_id,account_holder_name)
    VALUES ('10000000-0000-0000-0000-000000000099','Unknown owner')$q$, '23503', 'owner FK');
SELECT pg_temp.reject($q$UPDATE public.accounts SET balance=-1
    WHERE id='20000000-0000-0000-0000-000000000001'$q$, '23514', 'negative balance');
SELECT pg_temp.reject($q$UPDATE public.accounts SET balance='NaN'
    WHERE id='20000000-0000-0000-0000-000000000001'$q$, '23514', 'nonfinite money');
SELECT pg_temp.reject($q$UPDATE public.accounts SET balance=10000000000000000000
    WHERE id='20000000-0000-0000-0000-000000000001'$q$, '22003', 'money overflow');
SELECT pg_temp.reject($q$UPDATE public.accounts SET currency='USD'
    WHERE id='20000000-0000-0000-0000-000000000001'$q$, '23514', 'VND only');
SELECT pg_temp.reject($q$DELETE FROM public.users WHERE id='10000000-0000-0000-0000-000000000001'$q$,
    '23503', 'no cascading user deletion');

INSERT INTO public.payment_transactions(id,type,source_account_id,destination_account_id,amount) VALUES
    ('30000000-0000-0000-0000-000000000001','DEPOSIT',NULL,'20000000-0000-0000-0000-000000000001',1000),
    ('30000000-0000-0000-0000-000000000002','WITHDRAWAL','20000000-0000-0000-0000-000000000001',NULL,100),
    ('30000000-0000-0000-0000-000000000003','TRANSFER','20000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000002',200);
SELECT pg_temp.reject($q$UPDATE public.payment_transactions SET amount=0
    WHERE id='30000000-0000-0000-0000-000000000001'$q$, '23514', 'positive transaction amount');
SELECT pg_temp.reject($q$UPDATE public.payment_transactions SET source_account_id=destination_account_id
    WHERE id='30000000-0000-0000-0000-000000000001'$q$, '23514', 'deposit has no source');
SELECT pg_temp.reject($q$UPDATE public.payment_transactions SET destination_account_id=source_account_id
    WHERE id='30000000-0000-0000-0000-000000000002'$q$, '23514', 'withdrawal has no destination');
SELECT pg_temp.reject($q$UPDATE public.payment_transactions SET destination_account_id=source_account_id
    WHERE id='30000000-0000-0000-0000-000000000003'$q$, '23514', 'transfer to self');
SELECT pg_temp.reject($q$UPDATE public.payment_transactions SET destination_account_id=NULL
    WHERE id='30000000-0000-0000-0000-000000000003'$q$, '23514', 'transfer needs both accounts');
SELECT pg_temp.reject($q$UPDATE public.payment_transactions SET type='UNKNOWN'
    WHERE id='30000000-0000-0000-0000-000000000003'$q$, '23514', 'transaction type');
SELECT pg_temp.reject($q$UPDATE public.payment_transactions SET status='FAILED'
    WHERE id='30000000-0000-0000-0000-000000000003'$q$, '23514', 'committed SUCCESS only');
SELECT pg_temp.reject($q$UPDATE public.payment_transactions SET destination_account_id='20000000-0000-0000-0000-000000000099'
    WHERE id='30000000-0000-0000-0000-000000000003'$q$, '23503', 'transaction account FK');
SELECT pg_temp.reject($q$DELETE FROM public.accounts WHERE id='20000000-0000-0000-0000-000000000001'$q$,
    '23503', 'no cascading financial history deletion');

INSERT INTO public.idempotency_records(idempotency_key,request_fingerprint,transaction_id)
VALUES ('schema-check-key','v1|20000000-0000-0000-0000-000000000001|20000000-0000-0000-0000-000000000002|200',
    '30000000-0000-0000-0000-000000000003');
SELECT pg_temp.reject($q$INSERT INTO public.idempotency_records(idempotency_key,request_fingerprint,transaction_id)
    VALUES ('schema-check-key','test','30000000-0000-0000-0000-000000000001')$q$, '23505', 'unique idempotency key');
SELECT pg_temp.reject($q$INSERT INTO public.idempotency_records(idempotency_key,request_fingerprint,transaction_id)
    VALUES ('other-key','test','30000000-0000-0000-0000-000000000003')$q$, '23505', 'unique transaction result');
SELECT pg_temp.reject($q$UPDATE public.idempotency_records SET idempotency_key='bad key'
    WHERE idempotency_key='schema-check-key'$q$, '23514', 'visible ASCII key without spaces');
SELECT pg_temp.reject($q$DELETE FROM public.payment_transactions WHERE id='30000000-0000-0000-0000-000000000003'$q$,
    '23503', 'retained idempotency result');

-- Slot creation/reservation can precede successful SMTP delivery.
INSERT INTO public.email_otp_challenges(id,email,purpose)
VALUES ('40000000-0000-0000-0000-000000000001','pending-schema-check@example.test','REGISTRATION');
UPDATE public.email_otp_challenges SET window_started_at=now(),last_sent_at=now(),window_send_count=1
WHERE id='40000000-0000-0000-0000-000000000001';
UPDATE public.email_otp_challenges SET challenge_id=gen_random_uuid(),otp_digest=repeat('a',64),
    expires_at=now()+interval '5 minutes',pending_display_name='Pending',pending_password_hash='test-hash'
WHERE id='40000000-0000-0000-0000-000000000001';
SELECT pg_temp.reject($q$UPDATE public.email_otp_challenges SET pending_password_hash=NULL
    WHERE id='40000000-0000-0000-0000-000000000001'$q$, '23514', 'active registration requires hash');
SELECT pg_temp.reject($q$UPDATE public.email_otp_challenges SET otp_digest='123456'
    WHERE id='40000000-0000-0000-0000-000000000001'$q$, '23514', 'OTP digest instead of raw code');
SELECT pg_temp.reject($q$UPDATE public.email_otp_challenges SET consumed_at=now()
    WHERE id='40000000-0000-0000-0000-000000000001'$q$, '23514', 'consumption must erase digest');
SELECT pg_temp.reject($q$UPDATE public.email_otp_challenges SET failed_attempts=6
    WHERE id='40000000-0000-0000-0000-000000000001'$q$, '23514', 'five-attempt bound');
SELECT pg_temp.reject($q$UPDATE public.email_otp_challenges SET window_send_count=6
    WHERE id='40000000-0000-0000-0000-000000000001'$q$, '23514', 'five-send bound');
SELECT pg_temp.reject($q$INSERT INTO public.email_otp_challenges(email,purpose)
    VALUES ('pending-schema-check@example.test','REGISTRATION')$q$, '23505', 'one slot per email/purpose');
SELECT pg_temp.reject($q$INSERT INTO public.email_otp_challenges(email,purpose)
    VALUES ('pending-schema-check@example.test','PASSWORD_CHANGE')$q$, '23514', 'password change requires owner');
SELECT pg_temp.reject($q$INSERT INTO public.email_otp_challenges(email,purpose,user_id,credential_version)
    VALUES ('other@example.test','PASSWORD_CHANGE','10000000-0000-0000-0000-000000000001',0)$q$,
    '23503', 'password-change email must match owner');
INSERT INTO public.email_otp_challenges(email,purpose,user_id,credential_version)
VALUES ('schema-check@example.test','PASSWORD_CHANGE','10000000-0000-0000-0000-000000000001',0);

UPDATE public.email_otp_challenges SET failed_attempts=5
WHERE id='40000000-0000-0000-0000-000000000001';
UPDATE public.email_otp_challenges SET challenge_id=gen_random_uuid(),otp_digest=repeat('b',64),
    failed_attempts=0,window_send_count=2,expires_at=now()+interval '5 minutes'
WHERE id='40000000-0000-0000-0000-000000000001';
UPDATE public.email_otp_challenges SET consumed_at=now(),otp_digest=NULL,
    pending_display_name=NULL,pending_password_hash=NULL
WHERE id='40000000-0000-0000-0000-000000000001';
SELECT pg_temp.require((SELECT consumed_at IS NOT NULL AND otp_digest IS NULL
    AND pending_password_hash IS NULL AND window_send_count=2
    FROM public.email_otp_challenges WHERE id='40000000-0000-0000-0000-000000000001'),
    'consumed registration clears secrets and retains throttling');

ROLLBACK;
SELECT 'Schema checks passed; all test rows rolled back.' AS result;
