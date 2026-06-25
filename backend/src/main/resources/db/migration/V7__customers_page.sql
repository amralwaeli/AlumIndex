-- V7: Customers page — extend subscription_status to include 'expired',
--     change audit_logs tenant FK to SET NULL so offboard audit trail persists

-- Drop the auto-named inline CHECK, then recreate with 'expired' added
ALTER TABLE tenants
    DROP CONSTRAINT IF EXISTS tenants_subscription_status_check;
ALTER TABLE tenants
    ADD CONSTRAINT tenants_subscription_status_check
    CHECK (subscription_status IN ('active', 'suspended', 'expired'));

-- Change audit_logs tenant FK: CASCADE → SET NULL
-- (audit entries survive when their tenant is hard-deleted during offboarding)
ALTER TABLE audit_logs
    DROP CONSTRAINT IF EXISTS audit_logs_tenant_id_fkey;
ALTER TABLE audit_logs
    ADD CONSTRAINT audit_logs_tenant_id_fkey
    FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE SET NULL;
