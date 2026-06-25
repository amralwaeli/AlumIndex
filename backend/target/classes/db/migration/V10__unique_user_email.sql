-- Login looks users up by email globally, so emails must be globally unique.
-- The old UNIQUE(tenant_id, email) allowed the same email in two tenants, which
-- made findByEmail throw and broke login for that account.

-- 1. Detach audit-log references from duplicate users we are about to remove
WITH ranked AS (
    SELECT id, ROW_NUMBER() OVER (
        PARTITION BY email
        ORDER BY CASE status
                     WHEN 'active' THEN 0
                     WHEN 'pending_activation' THEN 1
                     ELSE 2
                 END,
                 created_at
    ) AS rn
    FROM users
)
UPDATE audit_logs SET user_id = NULL
WHERE user_id IN (SELECT id FROM ranked WHERE rn > 1);

-- 2. Remove their activation tokens
WITH ranked AS (
    SELECT id, ROW_NUMBER() OVER (
        PARTITION BY email
        ORDER BY CASE status
                     WHEN 'active' THEN 0
                     WHEN 'pending_activation' THEN 1
                     ELSE 2
                 END,
                 created_at
    ) AS rn
    FROM users
)
DELETE FROM activation_tokens
WHERE user_id IN (SELECT id FROM ranked WHERE rn > 1);

-- 3. Remove the duplicate users themselves (keep the most progressed account:
--    active > pending_activation > inactive, tie-broken by earliest created)
WITH ranked AS (
    SELECT id, ROW_NUMBER() OVER (
        PARTITION BY email
        ORDER BY CASE status
                     WHEN 'active' THEN 0
                     WHEN 'pending_activation' THEN 1
                     ELSE 2
                 END,
                 created_at
    ) AS rn
    FROM users
)
DELETE FROM users
WHERE id IN (SELECT id FROM ranked WHERE rn > 1);

-- 4. Enforce it forever
CREATE UNIQUE INDEX uq_users_email ON users (email);
