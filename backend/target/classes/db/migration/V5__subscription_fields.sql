-- A2: Add subscription term fields to tenants
ALTER TABLE tenants
    ADD COLUMN subscription_start      TIMESTAMPTZ,
    ADD COLUMN subscription_end        TIMESTAMPTZ,
    ADD COLUMN auto_suspend_on_expiry  BOOLEAN NOT NULL DEFAULT TRUE;
