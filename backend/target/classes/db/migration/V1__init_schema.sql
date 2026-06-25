-- AlumIndex DB Migration V1 — Full schema (12 entities)
-- Targeting Supabase (PostgreSQL 15+)
-- Run order: this file only; RLS policies in V2

-- ── Extensions ──────────────────────────────────────────────────────────────
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ── 1. tenants ───────────────────────────────────────────────────────────────
CREATE TABLE tenants (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_name    TEXT NOT NULL,
    admin_name          TEXT NOT NULL,
    admin_email         TEXT NOT NULL UNIQUE,
    subscription_status TEXT NOT NULL DEFAULT 'active' CHECK (subscription_status IN ('active', 'suspended')),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ── 2. customer_requests ─────────────────────────────────────────────────────
CREATE TABLE customer_requests (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name         TEXT NOT NULL,
    email        TEXT NOT NULL,
    institution  TEXT NOT NULL,
    job_title    TEXT NOT NULL,
    status       TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'approved', 'denied')),
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ── 3. invite_tokens ─────────────────────────────────────────────────────────
CREATE TABLE invite_tokens (
    token        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email        TEXT NOT NULL,
    organization TEXT NOT NULL,
    expires_at   TIMESTAMPTZ NOT NULL DEFAULT (now() + INTERVAL '20 minutes'),
    used         BOOLEAN NOT NULL DEFAULT FALSE
);

-- ── 4. users ─────────────────────────────────────────────────────────────────
CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID REFERENCES tenants(id) ON DELETE CASCADE,   -- NULL for superadmin
    full_name     TEXT NOT NULL,
    email         TEXT NOT NULL,
    password_hash TEXT NOT NULL,
    role          TEXT NOT NULL CHECK (role IN ('superadmin', 'admin', 'readonly')),
    status        TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'inactive')),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, email)
);
CREATE INDEX idx_users_tenant_id ON users(tenant_id);
CREATE UNIQUE INDEX idx_users_email_global ON users(email) WHERE tenant_id IS NULL;

-- ── 5. alumni ────────────────────────────────────────────────────────────────
CREATE TABLE alumni (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    full_name          TEXT NOT NULL,
    linkedin_url       TEXT,
    education_end_year INT,
    university_name    TEXT,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_alumni_tenant_id           ON alumni(tenant_id);
CREATE INDEX idx_alumni_linkedin_url        ON alumni(linkedin_url);
CREATE INDEX idx_alumni_name_year           ON alumni(full_name, education_end_year);

-- ── 6. alumni_profiles ───────────────────────────────────────────────────────
CREATE TABLE alumni_profiles (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    alumni_id        UUID NOT NULL REFERENCES alumni(id) ON DELETE CASCADE,
    employer         TEXT,
    job_title        TEXT,
    seniority        TEXT,
    industry         TEXT,
    location         TEXT,
    confidence_score NUMERIC(4,3) CHECK (confidence_score BETWEEN 0 AND 1),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_alumni_profiles_alumni_id ON alumni_profiles(alumni_id);

-- ── 7. profile_snapshots ─────────────────────────────────────────────────────
CREATE TABLE profile_snapshots (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    alumni_id        UUID NOT NULL REFERENCES alumni(id) ON DELETE CASCADE,
    captured_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    raw_source_data  JSONB,
    extracted_fields JSONB
);
CREATE INDEX idx_profile_snapshots_alumni_id ON profile_snapshots(alumni_id);

-- ── 8. career_events ─────────────────────────────────────────────────────────
CREATE TABLE career_events (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    alumni_id         UUID NOT NULL REFERENCES alumni(id) ON DELETE CASCADE,
    event_type        TEXT NOT NULL CHECK (event_type IN ('job_change', 'promotion', 'employer_change')),
    old_value         TEXT,
    new_value         TEXT,
    significance_level TEXT NOT NULL CHECK (significance_level IN ('high', 'medium', 'low')),
    detected_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_career_events_alumni_id ON career_events(alumni_id);

-- ── 9. import_batches ────────────────────────────────────────────────────────
CREATE TABLE import_batches (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    filename        TEXT NOT NULL,
    uploaded_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    record_count    INT NOT NULL DEFAULT 0,
    inserted_count  INT NOT NULL DEFAULT 0,
    updated_count   INT NOT NULL DEFAULT 0,
    unchanged_count INT NOT NULL DEFAULT 0,
    failed_count    INT NOT NULL DEFAULT 0,
    status          TEXT NOT NULL DEFAULT 'processing' CHECK (status IN ('processing', 'validated', 'completed', 'failed')),
    error_log       JSONB
);
CREATE INDEX idx_import_batches_tenant_id ON import_batches(tenant_id);

-- ── 10. data_permissions ─────────────────────────────────────────────────────
CREATE TABLE data_permissions (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    permission_key TEXT NOT NULL,
    enabled        BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (tenant_id, permission_key)
);
CREATE INDEX idx_data_permissions_tenant_id ON data_permissions(tenant_id);

-- ── 11. dashboard_metrics ────────────────────────────────────────────────────
CREATE TABLE dashboard_metrics (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    metric_name  TEXT NOT NULL,
    metric_value NUMERIC NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_dashboard_metrics_tenant_id ON dashboard_metrics(tenant_id);

-- ── 12. audit_logs ───────────────────────────────────────────────────────────
CREATE TABLE audit_logs (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID REFERENCES users(id) ON DELETE SET NULL,
    tenant_id      UUID REFERENCES tenants(id) ON DELETE CASCADE,
    action_type    TEXT NOT NULL,
    action_details TEXT,
    action_time    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_logs_tenant_id ON audit_logs(tenant_id);
CREATE INDEX idx_audit_logs_action_time ON audit_logs(action_time DESC);
