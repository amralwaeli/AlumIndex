-- V8: Activation flow, domain lock
-- 1. Extend users.status to include pending_activation
DO $$
DECLARE
    con text;
BEGIN
    SELECT c.conname INTO con
    FROM pg_constraint c
    JOIN pg_class t ON t.oid = c.conrelid
    WHERE t.relname = 'users' AND c.contype = 'c' AND c.conname ILIKE '%status%';
    IF con IS NOT NULL THEN
        EXECUTE 'ALTER TABLE users DROP CONSTRAINT ' || quote_ident(con);
    END IF;
END $$;

ALTER TABLE users ADD CONSTRAINT users_status_check
    CHECK (status IN ('active', 'inactive', 'pending_activation'));

-- 2. Activation tokens (24-hour expiry; used once)
CREATE TABLE IF NOT EXISTS activation_tokens (
    token        UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at   TIMESTAMPTZ  NOT NULL,
    used         BOOLEAN      NOT NULL DEFAULT false,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS activation_tokens_user_id_idx ON activation_tokens(user_id);

-- 3. Per-tenant email domain restriction (Issue 2)
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS allowed_email_domain VARCHAR(255);
