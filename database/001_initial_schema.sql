-- PostgreSQL 17. Run once against the empty payment_db database.
-- Execute the entire file. Existing tables cause an error and roll back this
-- transaction; this script never drops or silently replaces existing objects.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';
SET LOCAL TIME ZONE 'UTC';

CREATE TABLE public.users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(254) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    email_verified_at TIMESTAMPTZ NOT NULL,
    credential_version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_users_email UNIQUE (email),
    -- Supports an OTP recipient FK that also verifies the user's email.
    CONSTRAINT uq_users_id_email UNIQUE (id, email),
    CONSTRAINT ck_users_email CHECK (
        email = lower(btrim(email)) AND email ~ '^[^[:space:]@]+@[^[:space:]@]+$'
    ),
    CONSTRAINT ck_users_display_name CHECK (display_name ~ '[^[:space:]]'),
    CONSTRAINT ck_users_password_hash CHECK (password_hash ~ '[^[:space:]]'),
    CONSTRAINT ck_users_credential_version CHECK (credential_version >= 0)
);

CREATE TABLE public.accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    account_holder_name VARCHAR(100) NOT NULL,
    balance NUMERIC(19,0) NOT NULL DEFAULT 0,
    currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_accounts_user FOREIGN KEY (user_id)
        REFERENCES public.users(id) ON DELETE RESTRICT,
    CONSTRAINT ck_accounts_holder_name CHECK (account_holder_name ~ '[^[:space:]]'),
    CONSTRAINT ck_accounts_balance CHECK (balance >= 0 AND balance <= 9999999999999999999),
    CONSTRAINT ck_accounts_currency CHECK (currency = 'VND')
);

-- Deliberately nonunique: one user can own multiple accounts.
CREATE INDEX idx_accounts_user ON public.accounts(user_id);

CREATE TABLE public.payment_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type VARCHAR(10) NOT NULL,
    source_account_id UUID,
    destination_account_id UUID,
    amount NUMERIC(19,0) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    status VARCHAR(7) NOT NULL DEFAULT 'SUCCESS',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_transactions_source FOREIGN KEY (source_account_id)
        REFERENCES public.accounts(id) ON DELETE RESTRICT,
    CONSTRAINT fk_transactions_destination FOREIGN KEY (destination_account_id)
        REFERENCES public.accounts(id) ON DELETE RESTRICT,
    CONSTRAINT ck_transactions_amount CHECK (amount > 0 AND amount <= 9999999999999999999),
    CONSTRAINT ck_transactions_currency CHECK (currency = 'VND'),
    CONSTRAINT ck_transactions_status CHECK (status = 'SUCCESS'),
    CONSTRAINT ck_transactions_type_accounts CHECK (
        (type = 'DEPOSIT' AND source_account_id IS NULL AND destination_account_id IS NOT NULL)
        OR (type = 'WITHDRAWAL' AND source_account_id IS NOT NULL AND destination_account_id IS NULL)
        OR (type = 'TRANSFER' AND source_account_id IS NOT NULL
            AND destination_account_id IS NOT NULL AND source_account_id <> destination_account_id)
    )
);

CREATE INDEX idx_transactions_source_history
    ON public.payment_transactions(source_account_id, created_at, id);
CREATE INDEX idx_transactions_destination_history
    ON public.payment_transactions(destination_account_id, created_at, id);

CREATE TABLE public.idempotency_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key VARCHAR(128) NOT NULL,
    request_fingerprint VARCHAR(96) NOT NULL,
    transaction_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT uq_idempotency_transaction UNIQUE (transaction_id),
    CONSTRAINT fk_idempotency_transaction FOREIGN KEY (transaction_id)
        REFERENCES public.payment_transactions(id) ON DELETE RESTRICT,
    CONSTRAINT ck_idempotency_key CHECK (idempotency_key COLLATE "C" ~ '^[!-~]{1,128}$'),
    CONSTRAINT ck_idempotency_fingerprint CHECK (request_fingerprint ~ '[^[:space:]]')
);

CREATE TABLE public.email_otp_challenges (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    challenge_id UUID,
    email VARCHAR(254) NOT NULL,
    purpose VARCHAR(20) NOT NULL,
    user_id UUID,
    credential_version INTEGER,
    pending_display_name VARCHAR(100),
    pending_password_hash VARCHAR(255),
    otp_digest VARCHAR(64),
    expires_at TIMESTAMPTZ,
    consumed_at TIMESTAMPTZ,
    failed_attempts INTEGER NOT NULL DEFAULT 0,
    last_sent_at TIMESTAMPTZ,
    window_started_at TIMESTAMPTZ,
    window_send_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_otp_challenge UNIQUE (challenge_id),
    CONSTRAINT uq_otp_email_purpose UNIQUE (email, purpose),
    CONSTRAINT fk_otp_user_email FOREIGN KEY (user_id, email)
        REFERENCES public.users(id, email) ON DELETE RESTRICT,
    CONSTRAINT ck_otp_email CHECK (
        email = lower(btrim(email)) AND email ~ '^[^[:space:]@]+@[^[:space:]@]+$'
    ),
    CONSTRAINT ck_otp_attempts CHECK (failed_attempts BETWEEN 0 AND 5),
    CONSTRAINT ck_otp_send_count CHECK (window_send_count BETWEEN 0 AND 5),
    CONSTRAINT ck_otp_digest CHECK (otp_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_otp_purpose_owner CHECK (
        (purpose = 'REGISTRATION' AND user_id IS NULL AND credential_version IS NULL)
        OR (purpose = 'PASSWORD_CHANGE' AND user_id IS NOT NULL
            AND credential_version IS NOT NULL AND credential_version >= 0)
    ),
    CONSTRAINT ck_otp_delivery_window CHECK (
        (window_send_count = 0 AND window_started_at IS NULL AND last_sent_at IS NULL)
        OR (window_send_count BETWEEN 1 AND 5 AND window_started_at IS NOT NULL
            AND last_sent_at IS NOT NULL AND last_sent_at >= window_started_at)
    ),
    CONSTRAINT ck_otp_state CHECK (
        -- Empty/reserved slot, including a failed first email delivery.
        (challenge_id IS NULL AND otp_digest IS NULL AND expires_at IS NULL
            AND consumed_at IS NULL AND failed_attempts = 0)
        OR (challenge_id IS NOT NULL AND expires_at IS NOT NULL AND window_send_count > 0
            AND ((otp_digest IS NOT NULL AND consumed_at IS NULL)
                OR (otp_digest IS NULL AND consumed_at IS NOT NULL)))
    ),
    CONSTRAINT ck_otp_registration_fields CHECK (
        (purpose = 'REGISTRATION' AND otp_digest IS NOT NULL
            AND pending_display_name IS NOT NULL AND pending_display_name ~ '[^[:space:]]'
            AND pending_password_hash IS NOT NULL AND pending_password_hash ~ '[^[:space:]]')
        OR ((purpose = 'PASSWORD_CHANGE' OR otp_digest IS NULL)
            AND pending_display_name IS NULL AND pending_password_hash IS NULL)
    )
);

CREATE INDEX idx_otp_expires_at ON public.email_otp_challenges(expires_at);
CREATE INDEX idx_otp_user ON public.email_otp_challenges(user_id);

COMMENT ON COLUMN public.accounts.user_id IS 'Required owner; nonunique to allow multiple accounts per user.';
COMMENT ON COLUMN public.users.password_hash IS 'Adaptive encoded password; never plaintext. Encoder is supplied by the application.';
COMMENT ON COLUMN public.email_otp_challenges.otp_digest IS 'Hex HMAC-SHA-256 of challenge ID and code; never the plaintext OTP.';
COMMENT ON COLUMN public.email_otp_challenges.last_sent_at IS 'Issuance reservation time; includes failed SMTP sends for throttling.';
COMMENT ON COLUMN public.accounts.balance IS 'Whole VND. Validate input before SQL: NUMERIC(19,0) may round fractional input.';
COMMENT ON TABLE public.payment_transactions IS 'Committed results. Atomic balance changes and authorization are service responsibilities.';

COMMIT;
