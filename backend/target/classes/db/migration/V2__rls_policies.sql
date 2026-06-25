-- AlumIndex RLS Policies V2
-- Enables Row-Level Security on all tenant tables.
-- The backend sets the current tenant via a session variable:
--   SET LOCAL app.current_tenant_id = '<uuid>';
-- Superadmin bypasses RLS by connecting as a privileged role or via SECURITY DEFINER functions.

-- Enable RLS on every tenant table
ALTER TABLE users              ENABLE ROW LEVEL SECURITY;
ALTER TABLE alumni             ENABLE ROW LEVEL SECURITY;
ALTER TABLE alumni_profiles    ENABLE ROW LEVEL SECURITY;
ALTER TABLE profile_snapshots  ENABLE ROW LEVEL SECURITY;
ALTER TABLE career_events      ENABLE ROW LEVEL SECURITY;
ALTER TABLE import_batches     ENABLE ROW LEVEL SECURITY;
ALTER TABLE data_permissions   ENABLE ROW LEVEL SECURITY;
ALTER TABLE dashboard_metrics  ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_logs         ENABLE ROW LEVEL SECURITY;

-- Helper: extract current tenant from session variable
CREATE OR REPLACE FUNCTION current_tenant_id() RETURNS UUID
    LANGUAGE sql STABLE
AS $$
    SELECT NULLIF(current_setting('app.current_tenant_id', TRUE), '')::UUID;
$$;

-- ── users ────────────────────────────────────────────────────────────────────
CREATE POLICY tenant_isolation ON users
    USING (tenant_id = current_tenant_id() OR tenant_id IS NULL);

-- ── alumni ───────────────────────────────────────────────────────────────────
CREATE POLICY tenant_isolation ON alumni
    USING (tenant_id = current_tenant_id());

-- ── alumni_profiles (via alumni.tenant_id) ───────────────────────────────────
CREATE POLICY tenant_isolation ON alumni_profiles
    USING (
        alumni_id IN (
            SELECT id FROM alumni WHERE tenant_id = current_tenant_id()
        )
    );

-- ── profile_snapshots ────────────────────────────────────────────────────────
CREATE POLICY tenant_isolation ON profile_snapshots
    USING (
        alumni_id IN (
            SELECT id FROM alumni WHERE tenant_id = current_tenant_id()
        )
    );

-- ── career_events ────────────────────────────────────────────────────────────
CREATE POLICY tenant_isolation ON career_events
    USING (
        alumni_id IN (
            SELECT id FROM alumni WHERE tenant_id = current_tenant_id()
        )
    );

-- ── import_batches ───────────────────────────────────────────────────────────
CREATE POLICY tenant_isolation ON import_batches
    USING (tenant_id = current_tenant_id());

-- ── data_permissions ─────────────────────────────────────────────────────────
CREATE POLICY tenant_isolation ON data_permissions
    USING (tenant_id = current_tenant_id());

-- ── dashboard_metrics ────────────────────────────────────────────────────────
CREATE POLICY tenant_isolation ON dashboard_metrics
    USING (tenant_id = current_tenant_id());

-- ── audit_logs ───────────────────────────────────────────────────────────────
CREATE POLICY tenant_isolation ON audit_logs
    USING (tenant_id = current_tenant_id());
