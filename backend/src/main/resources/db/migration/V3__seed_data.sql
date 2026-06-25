-- AlumIndex Seed V3 — deterministic demo data
-- Superadmin pw: AlumIndex@2024 (BCrypt hash below)
-- UTM admin pw:  Admin@UTM2024
-- UM  admin pw:  Admin@UM2024
-- USM admin pw:  Admin@USM2024

-- ── 1. Superadmin user ───────────────────────────────────────────────────────
INSERT INTO users (id, tenant_id, full_name, email, password_hash, role, status)
VALUES (
    'a0000000-0000-0000-0000-000000000001',
    NULL,
    'Amr Al-Waeli',
    'amralwaeli9@gmail.com',
    '$2a$12$hOuJt3bqBBQp7PvT.UbkSuHfEBnrKnKwP9DRq0uVd0rCFJzLcpU0i', -- AlumIndex@2024
    'superadmin',
    'active'
);

-- ── 2. Tenants ───────────────────────────────────────────────────────────────
INSERT INTO tenants (id, institution_name, admin_name, admin_email, subscription_status)
VALUES
    ('b0000000-0000-0000-0000-000000000001', 'Universiti Teknologi Malaysia', 'Dr. Siti Rahimah',   'admin@utm.edu.my',  'active'),
    ('b0000000-0000-0000-0000-000000000002', 'Universiti Malaya',            'Prof. Azman Hashim', 'admin@um.edu.my',   'active'),
    ('b0000000-0000-0000-0000-000000000003', 'Universiti Sains Malaysia',    'Dr. Farah Nadia',    'admin@usm.edu.my',  'active');

-- ── 3. Tenant admin users ────────────────────────────────────────────────────
INSERT INTO users (id, tenant_id, full_name, email, password_hash, role, status)
VALUES
    ('c0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000001', 'Dr. Siti Rahimah',   'admin@utm.edu.my', '$2a$12$YXKM8bEd4RjFe3pCZ6d7qeE9m5FLJ8K7bGXaHn3bkMbhv0B7hFnXu', 'admin',    'active'),
    ('c0000000-0000-0000-0000-000000000002', 'b0000000-0000-0000-0000-000000000002', 'Prof. Azman Hashim', 'admin@um.edu.my',  '$2a$12$9v7FpNw5Xe1rQjKdB4aPFeGcVmTsHoLiZqXn2kJwDy6bC3R8sGvMu', 'admin',    'active'),
    ('c0000000-0000-0000-0000-000000000003', 'b0000000-0000-0000-0000-000000000003', 'Dr. Farah Nadia',    'admin@usm.edu.my', '$2a$12$rP4vKb8Xt9nWjLfD2eQogeF5mRaYHdCuGpXs7nBw3KvMi1T0tHqNy', 'admin',    'active'),
    ('c0000000-0000-0000-0000-000000000004', 'b0000000-0000-0000-0000-000000000001', 'Ahmad Faiz',         'faiz@utm.edu.my',  '$2a$12$YXKM8bEd4RjFe3pCZ6d7qeE9m5FLJ8K7bGXaHn3bkMbhv0B7hFnXu', 'readonly', 'active');

-- ── 4. Default data permissions (seeded for all 3 tenants) ──────────────────
DO $$
DECLARE
    tid UUID;
    pkeys TEXT[] := ARRAY[
        'current_employment','location_linkedin','employer_type','historical_employment','nonprofit_boards','corp_matching',
        'salary','donation_pred','property','sec_stock',
        'biz_email','personal_email',
        'seniority','news',
        'monthly','midyear','multiyear',
        'ultra_conf','company_id',
        'exports_users','support'
    ];
    defaults_on TEXT[] := ARRAY['current_employment','seniority','monthly','exports_users','support'];
    pkey TEXT;
BEGIN
    FOREACH tid IN ARRAY ARRAY[
        'b0000000-0000-0000-0000-000000000001'::UUID,
        'b0000000-0000-0000-0000-000000000002'::UUID,
        'b0000000-0000-0000-0000-000000000003'::UUID
    ] LOOP
        FOREACH pkey IN ARRAY pkeys LOOP
            INSERT INTO data_permissions (tenant_id, permission_key, enabled)
            VALUES (tid, pkey, pkey = ANY(defaults_on));
        END LOOP;
    END LOOP;
END $$;

-- ── 5. Customer requests (pending) ──────────────────────────────────────────
INSERT INTO customer_requests (id, name, email, institution, job_title, status)
VALUES
    ('d0000000-0000-0000-0000-000000000001', 'Dr. Lim Wei Jian', 'lim@utp.edu.my',  'Universiti Teknologi PETRONAS', 'Director of Alumni Relations', 'pending'),
    ('d0000000-0000-0000-0000-000000000002', 'Nor Azlin Bt Yusof','azlin@iium.edu.my','International Islamic University Malaysia','Alumni Affairs Manager','pending');

-- ── 6. UTM alumni (1,000+ rows generated deterministically) ─────────────────
-- Insert 1,020 alumni for UTM with profiles + snapshots + some career events.
-- Names, titles, and industries are drawn from fixed arrays to keep the seed deterministic.
DO $$
DECLARE
    i INT;
    alumni_id UUID;
    profile_id UUID;
    snap_id UUID;
    tenant UUID := 'b0000000-0000-0000-0000-000000000001';

    first_names TEXT[] := ARRAY['Ahmad','Muhammad','Nur','Siti','Khairul','Faizal','Hafizuddin','Razif','Amirul','Zainab',
        'Nabilah','Rashid','Hamidah','Izzati','Firdaus','Ezwan','Haslinda','Syafiq','Nurul','Azizi',
        'Rosnah','Irfan','Dzulkarnain','Wafiq','Balkis','Nazmie','Fadzlillah','Halimah','Tasnim','Usamah'];
    last_names  TEXT[] := ARRAY['Abdullah','Hassan','Ibrahim','Ismail','Othman','Yusof','Ahmad','Hamid','Razak','Salleh',
        'Karim','Zainudin','Bakar','Daud','Ghani','Talib','Wahab','Aziz','Osman','Mansor',
        'Rahim','Nordin','Ramli','Jaafar','Ali','Kassim','Samad','Harun','Ariffin','Musa'];
    employers   TEXT[] := ARRAY['Petronas','Tenaga Nasional','Maybank','CIMB Group','Telekom Malaysia',
        'AirAsia','Maxis','Public Bank','RHB Bank','Hong Leong Bank',
        'Sapura Energy','Gamuda','IJM Corporation','Sime Darby','YTL Corporation',
        'Khazanah Nasional','PNB','EPF','KWSP','Jabatan Perkhidmatan Awam'];
    job_titles  TEXT[] := ARRAY['Software Engineer','Senior Engineer','Product Manager','Data Analyst','Systems Architect',
        'Project Manager','Business Analyst','DevOps Engineer','Tech Lead','CTO',
        'Financial Analyst','Investment Manager','Risk Manager','Compliance Officer','VP Finance',
        'HR Manager','Operations Director','Marketing Manager','Sales Executive','CEO'];
    seniorities TEXT[] := ARRAY['Junior','Mid-Level','Senior','Lead','Manager','Director','VP','C-Suite'];
    industries  TEXT[] := ARRAY['Technology','Finance','Energy','Telecommunications','Aviation','Banking',
        'Construction','Manufacturing','Healthcare','Government'];
    locations   TEXT[] := ARRAY['Kuala Lumpur','Selangor','Johor Bahru','Penang','Putrajaya',
        'Cyberjaya','Petaling Jaya','Shah Alam','Subang Jaya','Kota Kinabalu'];
    grad_years  INT[]  := ARRAY[2010,2011,2012,2013,2014,2015,2016,2017,2018,2019,2020,2021,2022];

    fname TEXT; lname TEXT; employer TEXT; jtitle TEXT; sen TEXT; ind TEXT; loc TEXT; yr INT;
    confidence NUMERIC;
    has_event BOOLEAN;
    old_title TEXT;
BEGIN
    FOR i IN 1..1020 LOOP
        fname     := first_names[1 + (i % array_length(first_names, 1))];
        lname     := last_names[1  + ((i * 7) % array_length(last_names, 1))];
        employer  := employers[1   + ((i * 3) % array_length(employers, 1))];
        jtitle    := job_titles[1  + ((i * 11) % array_length(job_titles, 1))];
        sen       := seniorities[1 + ((i * 5)  % array_length(seniorities, 1))];
        ind       := industries[1  + ((i * 13) % array_length(industries, 1))];
        loc       := locations[1   + ((i * 17) % array_length(locations, 1))];
        yr        := grad_years[1  + ((i * 2)  % array_length(grad_years, 1))];
        confidence := 0.7 + (i % 30) * 0.01;

        alumni_id := gen_random_uuid();

        INSERT INTO alumni (id, tenant_id, full_name, linkedin_url, education_end_year, university_name)
        VALUES (
            alumni_id, tenant,
            fname || ' ' || lname,
            'https://linkedin.com/in/' || lower(fname) || '-' || lower(lname) || '-' || i,
            yr,
            'Universiti Teknologi Malaysia'
        );

        INSERT INTO alumni_profiles (id, alumni_id, employer, job_title, seniority, industry, location, confidence_score)
        VALUES (gen_random_uuid(), alumni_id, employer, jtitle, sen, ind, loc, LEAST(confidence, 1.0));

        INSERT INTO profile_snapshots (id, alumni_id, captured_at, extracted_fields)
        VALUES (
            gen_random_uuid(), alumni_id,
            now() - ((1020 - i) || ' days')::INTERVAL,
            jsonb_build_object(
                'employer', employer, 'job_title', jtitle, 'seniority', sen,
                'industry', ind, 'location', loc
            )
        );

        -- ~20% of alumni have a career event
        has_event := (i % 5 = 0);
        IF has_event THEN
            old_title := job_titles[1 + ((i * 19) % array_length(job_titles, 1))];
            INSERT INTO career_events (alumni_id, event_type, old_value, new_value, significance_level, detected_at)
            VALUES (
                alumni_id,
                CASE WHEN i % 2 = 0 THEN 'promotion' ELSE 'employer_change' END,
                old_title,
                jtitle,
                'high',
                now() - ((500 - (i % 500)) || ' days')::INTERVAL
            );
        END IF;
    END LOOP;
END $$;

-- ── 7. Sample audit logs ─────────────────────────────────────────────────────
INSERT INTO audit_logs (user_id, tenant_id, action_type, action_details, action_time)
VALUES
    ('c0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000001', 'PERMISSION_UPDATED', 'salary enabled=false', now() - INTERVAL '2 days'),
    ('c0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000001', 'ALUMNI_ANONYMISED',  'alumni_id masked',     now() - INTERVAL '5 days'),
    ('a0000000-0000-0000-0000-000000000001', NULL,                                   'TENANT_APPROVED',   'UTM approved',          now() - INTERVAL '30 days');
