-- Upgrade existing databases after 001_initial_schema.sql. No data reset.
BEGIN;
SET LOCAL lock_timeout = '20s';
ALTER TABLE public.email_otp_challenges DROP CONSTRAINT ck_otp_purpose_owner;
ALTER TABLE public.email_otp_challenges ADD CONSTRAINT ck_otp_purpose_owner CHECK (
    (purpose = 'REGISTRATION' AND user_id IS NULL AND credential_version IS NULL)
    OR (purpose IN ('PASSWORD_CHANGE', 'PASSWORD_RESET') AND user_id IS NOT NULL
        AND credential_version IS NOT NULL AND credential_version >= 0)
);
ALTER TABLE public.email_otp_challenges DROP CONSTRAINT ck_otp_registration_fields;
ALTER TABLE public.email_otp_challenges ADD CONSTRAINT ck_otp_registration_fields CHECK (
    (purpose = 'REGISTRATION' AND otp_digest IS NOT NULL
        AND pending_display_name IS NOT NULL AND pending_display_name ~ '[^[:space:]]'
        AND pending_password_hash IS NOT NULL AND pending_password_hash ~ '[^[:space:]]')
    OR ((purpose IN ('PASSWORD_CHANGE', 'PASSWORD_RESET') OR otp_digest IS NULL)
        AND pending_display_name IS NULL AND pending_password_hash IS NULL)
);
COMMENT ON COLUMN public.users.password_hash IS
    '{bcrypt-hmac-sha384-v1} followed by BCrypt 2b; stable external pepper required.';
COMMIT;
