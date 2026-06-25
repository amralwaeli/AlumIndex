-- Settings tab: add seat management, contact info, and API key to tenants
ALTER TABLE tenants
    ADD COLUMN primary_contact  TEXT,
    ADD COLUMN contact_email    TEXT,
    ADD COLUMN seat_limit       INT NOT NULL DEFAULT 5,
    ADD COLUMN api_key          TEXT UNIQUE;
