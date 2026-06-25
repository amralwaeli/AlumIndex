# AlumIndex — Master Build Prompt for Claude Code

> Paste this entire file as your first message in Claude Code (or save it as `CLAUDE.md` at the repo root). It is the complete, authoritative specification. Build to it exactly. Do not invent features, do not omit modules, do not substitute the stack. Where a choice is unspecified, pick the simplest option that satisfies everything below and state the choice in a comment.

---

## 0. Role & operating rules

You are the lead engineer building **AlumIndex** end to end. Work in this order: scaffold → database → backend modules → frontend → pipeline → tests → Docker. After each milestone, run the build/tests and fix all errors before moving on. Keep a running `PROGRESS.md`. Never leave a module half-wired. Every record-returning query MUST be tenant-scoped. Treat the acceptance criteria in §12 as the definition of done.

---

## 1. What AlumIndex is (one paragraph, memorise it)

AlumIndex is a **multi-tenant SaaS platform** for alumni career tracking and analytics. A single platform operator (Super Admin / developer) onboards universities as paying customers. Each university's staff see **only their own institution's** alumni data (hard tenant isolation). The operator uploads a CSV exported by a compliant third-party scraper; an automated pipeline **normalises** each row with an LLM, **matches** it against existing alumni with a rule-based L1/L2/L3 strategy, and **inserts/updates** records while recording career-change events. University staff then search, view career histories, see analytics dashboards, donor insights, and alerts. It is a lighter, student-built, zero-licensing-cost alternative to LiveAlumni (which UTM was quoted USD 18,984–28,610 / ≈ RM 89,784–134,817 per year for).

**AlumIndex does NOT scrape.** Web scraping is performed externally by a compliant third-party data provider. AlumIndex ingests the resulting structured CSV/Excel export only.

---

## 2. Locked technology stack — do not substitute

| Layer | Technology | Notes |
|---|---|---|
| Frontend | React 18 + TypeScript + Vite | SPA |
| UI | Tailwind CSS + shadcn/ui | design tokens in §10 |
| Charts | Recharts | dashboard + trajectory |
| Backend | Spring Boot 3 (Java 17+) + Spring Security | REST API |
| Auth | JWT (HS256), stateless | RBAC, tenant claim in token |
| Database | Supabase (PostgreSQL) | Row-Level Security ON for all tenant tables |
| LLM | OpenAI API (`gpt-4o-mini`, `temperature=0`) | normalisation only |
| Deployment | Docker + Docker Compose | frontend, backend, env config |
| External data | 3rd-party scraper → CSV/XLSX handoff | AlumIndex never scrapes |

Repo layout (monorepo):
```
alumindex/
  backend/        Spring Boot (Maven or Gradle — pick Maven)
  frontend/       Vite + React + TS + Tailwind + shadcn/ui
  db/             SQL migrations + RLS policies + seed
  docker-compose.yml
  .env.example
  PROGRESS.md
```

---

## 3. Tenancy & security model (the spine — get this right first)

- Every tenant-owned row has a `tenant_id` (UUID) foreign key to `tenants`.
- The authenticated user's `tenant_id` is embedded as a **claim in the JWT** at login. The backend derives `tenant_id` from the token on every request and **ignores any tenant_id supplied in the request body/params**. A user can never read or write another tenant's data, even by manipulating parameters → return `403`.
- Enable **PostgreSQL Row-Level Security** on every tenant table as defence in depth; policies filter by the current tenant context set per request.
- The Super Admin (`role=superadmin`) is **not** bound to a tenant; operator endpoints are separated under `/api/superadmin/**` and require the superadmin role.
- Passwords hashed with BCrypt. JWT expiry 24h; refresh optional (note it but don't over-build).

### Roles (exactly 3)
| Role | Portal | Access |
|---|---|---|
| `superadmin` | Super Admin | full platform control; not tenant-bound |
| `admin` | University | full access for own tenant incl. user management, deletion/anonymisation |
| `readonly` | University | view dashboards, search, profiles, reports; **no** management/import/permissions/delete |

Enforce role at the API (`@PreAuthorize`) **and** hide forbidden UI in the frontend.

---

## 4. Database schema (12 entities) — create as SQL migrations

Use UUID primary keys (`gen_random_uuid()`), `timestamptz` for times, and add `tenant_id` + indexes on every tenant table. RLS ON for all tables marked (T).

1. **tenants** (T-root): `id`, `institution_name`, `admin_name`, `admin_email`, `subscription_status` (`active|suspended`), `created_at`.
2. **customer_requests**: `id`, `name`, `email`, `institution`, `job_title`, `status` (`pending|approved|denied`), `submitted_at`.
3. **invite_tokens**: `token` (UUID, PK), `email`, `organization`, `expires_at` (now()+20min), `used` (bool).
4. **users** (T): `id`, `tenant_id`(nullable for superadmin), `full_name`, `email` (unique per tenant), `password_hash`, `role` (`superadmin|admin|readonly`), `status` (`active|inactive`), `created_at`.
5. **alumni** (T): `id`, `tenant_id`, `full_name`, `linkedin_url` (nullable), `education_end_year` (int, used as graduation year), `university_name`, `created_at`.
6. **alumni_profiles** (T): `id`, `alumni_id`, `employer`, `job_title`, `seniority`, `industry`, `location`, `confidence_score` (numeric 0–1), `updated_at`. (Current snapshot.)
7. **profile_snapshots** (T): `id`, `alumni_id`, `captured_at`, `raw_source_data` (jsonb), `extracted_fields` (jsonb). (Historical versions.)
8. **career_events** (T): `id`, `alumni_id`, `event_type` (`job_change|promotion|employer_change`), `old_value`, `new_value`, `significance_level` (`high|medium|low`), `detected_at`.
9. **import_batches** (T): `id`, `tenant_id`, `filename`, `uploaded_at`, `record_count`, `inserted_count`, `updated_count`, `unchanged_count`, `failed_count`, `status` (`processing|validated|completed|failed`), `error_log` (jsonb).
10. **data_permissions** (T): `id`, `tenant_id`, `permission_key`, `enabled` (bool). (21 keys per tenant — see §8.)
11. **dashboard_metrics** (T): `id`, `tenant_id`, `metric_name`, `metric_value` (numeric), `generated_at`.
12. **audit_logs** (T): `id`, `user_id`, `tenant_id`, `action_type`, `action_details`, `action_time`.

Add FKs, `ON DELETE` rules, and indexes on `(tenant_id)`, `alumni_profiles(alumni_id)`, `career_events(alumni_id)`, `alumni(linkedin_url)`, `alumni(full_name, education_end_year)`.

---

## 5. The data pipeline (core technical contribution) — exact behaviour

Triggered automatically after a Super Admin uploads a CSV to a selected tenant. Runs **asynchronously**, per row, in this exact order:

**Step 1 — Upload & validate.** Accept CSV/XLSX/XLS, drag-drop, **max 50 MB** (50.00 MB valid, 50.1 MB rejected with `400`). Validate required columns present (see §6). Wrong file type → reject, no DB write. Create an `import_batches` row (`status=processing`).

**Step 2 — LLM normalisation (runs FIRST, before matching).** For each raw row, call OpenAI (`gpt-4o-mini`, `temperature=0`) to clean & extract structured fields: normalise `full_name` (strip titles), extract `employer`, `job_title`, `seniority`, `industry`, `location`, and assign a `confidence_score` (0–1). Output a clean structured object. **No alumni data is sent externally during matching — only this normalisation step uses the API.** On API failure: retry with exponential backoff (1s, 2s, 4s). If all retries fail → mark row failed, log `"OpenAI API unavailable"` to `error_log`, **continue to next row**.

**Step 3 — Matching (runs AFTER normalisation, on clean data, all internal).** Match each clean row against existing alumni **within the same tenant** using prioritised signals:
- **L1** — exact `linkedin_url` match → confidence ≥ 95%. (If found, skip L2/L3.)
- **L2** — `full_name` + `education_end_year` match → ~85%.
- **L3** — `full_name` only → 50–75%. If the name matches ≥2 records → flag as ambiguous (`AMBIGUOUS_NAME`), mark failed.

**Step 4 — Insert / update / unchanged:**
- **Not found** → create `alumni` + first `alumni_profiles` + first `profile_snapshots`. `inserted_count++`.
- **Found, data changed** (any of the 5 comparison fields differ: employer, job_title, seniority, industry, location) → update `alumni_profiles`, insert new `profile_snapshots`, insert `career_events` (set `event_type` and `significance_level`: a seniority increase = `promotion`/high; employer change = `employer_change`/high). `updated_count++`.
- **Found, identical** → no write. `unchanged_count++`.

**Step 5 — Finalise.** Update `import_batches` counts and `status=completed`. High-significance career events feed the Alerts module (§7).

Implement matching as a clearly testable decision tree (white-box branch coverage is required — see §11).

---

## 6. Scraper CSV schema (input contract)

Required: `full_name`, `first_name`, `last_name`, `captured_date`.
Optional (LLM normalises raw values): `linkedin_url` (strongest match signal), `employment_title`, `employment_company`, `company_standardized_name`, `employment_start_month`, `employment_start_year`, `company_size`, `company_type`, `company_industry`, `location_city`, `location_state`, `location_country`, `education_degree`, `education_major`, `education_end_year` (graduation year for matching), `university_name`.

---

## 7. The 8 modules (build all; map to use cases in §9)

1. **Auth Module** — JWT login/logout, BCrypt, RBAC, portal routing (superadmin → operator app; admin/readonly → university app).
2. **Tenant Module** — invite university (20-min token email), registration request queue, approve/deny (approve seeds tenant + admin user + default permissions + activation email), view active customers, tenant isolation.
3. **Import Module** — CSV/XLSX upload per tenant, validation, 50 MB limit, batch tracking, triggers pipeline.
4. **LLM Normalisation Module** — OpenAI extraction, confidence scoring, retry/backoff, clean structured output.
5. **Matching Module** — internal L1/L2/L3 match, ambiguity flagging, auto insert/update/unchanged, career-event detection.
6. **Profile/Search Module** — search (name/employer), filter (industry, seniority), view current profile, view career-history timeline (snapshots + events).
7. **Analytics Module** — dashboard KPIs (total alumni, employment rate, career-change alerts, high-value prospects), charts (employment-rate trend, seniority distribution, industry spread), donor insights, alerts, audit log.
8. **Permissions Module** — per-tenant toggle control over 21 data fields / 7 categories (§8).

### Donor Insights (university portal)
Per alumnus: giving likelihood score (0–100%), estimated capacity range (e.g. RM 75K–300K), wealth indicator (high/medium/low), employer-matching availability flag, suggested engagement approach. Sortable by likelihood, capacity, recent activity. **Gated** by data permissions: if the tenant's `donation_pred`/`salary` fields are disabled, show a locked state, not the data.

### Alerts (university portal, auto-surfaced from pipeline)
Types: `job_change` (high), `donor_prospect` (high), `verification` (med), `data_quality` (low), `system` (low). Filterable. Generated when the pipeline detects the corresponding condition.

---

## 8. Data permissions — 21 fields, 7 categories (per tenant)

Super Admin toggles these per university. Seed defaults on tenant creation.

| Category | Fields (`permission_key`) |
|---|---|
| Employment Data | `current_employment`✓, `location_linkedin`, `employer_type`, `historical_employment`, `nonprofit_boards`, `corp_matching` |
| Net Worth Data | `salary`, `donation_pred`, `property`, `sec_stock` |
| Contact Data | `biz_email`, `personal_email` |
| Professional Insights | `seniority`✓, `news` |
| Data Refresh | `monthly`✓, `midyear`, `multiyear` |
| Verification & Matching | `ultra_conf`, `company_id` |
| Access & Support | `exports_users`✓, `support`✓ |

Defaults ON (✓): `current_employment`, `seniority`, `monthly`, `exports_users`, `support`. Everything else OFF. A "Reset to default" action restores exactly this set. Every change writes an `audit_logs` entry (`PERMISSION_UPDATED`).

---

## 9. The 18 use cases (acceptance-level behaviour)

| UC | Name | Actor | Key behaviour |
|---|---|---|---|
| UC001 | Login | Both | valid → JWT + role route; bad pw → 401; inactive → 403 |
| UC002 | Logout | Both | clear token → /login |
| UC003 | Manage Users | University admin | create/list users; duplicate email → 409 |
| UC004 | Validate Import Structure | Both | required cols present; missing → block, no pipeline |
| UC005 | Validate Import Result | Both | view batch summary (total/inserted/updated/unchanged/failed) |
| UC006 | Run Pipeline | System | normalise → match → write (see §5) |
| UC007 | Search Alumni | University | by name/employer, tenant-scoped, empty-state msg |
| UC008 | Filter Alumni | University | by industry + seniority |
| UC009 | View Profile | University | current profile fields |
| UC010 | View Career History | University | snapshots + events timeline; single-snapshot empty state |
| UC011 | Dashboard Analytics | University | KPIs + charts, tenant-scoped |
| UC012 | Delete/Anonymise Alumni | University admin | anonymise (name→ANONYMISED, linkedin→null), audit log; cancel = no change |
| UC013 | View Audit Log | University admin | reverse-chronological, tenant-scoped |
| UC014 | Invite University | Super Admin | 20-min token email; duplicate active token → 409 |
| UC015 | Approve/Deny Registration | Super Admin | approve seeds tenant+admin+perms+email; deny = no tenant |
| UC016 | Upload Alumni Data | Super Admin | per tenant, ≤50 MB, triggers pipeline |
| UC017 | Configure Data Permissions | Super Admin | toggle 21 fields; reset to default |
| UC018 | View Active Customers | Super Admin | active tenants + user_count + last_import |

---

## 10. Frontend design system (match the prototype — DO NOT produce generic AI UI)

A reference React prototype (`AlumIndex_Prototype.jsx`) accompanies this prompt. **Treat it as the visual source of truth.** Reproduce its design language with shadcn/ui + Tailwind.

- **Two distinct shells.** Super Admin = **dark ink navy** operator console (dense, data-grade). University = **warm bone** analyst workspace (calm, light). Role context is felt instantly.
- **Palette (define as CSS variables / Tailwind theme extension — not the default blue):**
  - ink `#0E1726`, ink panel `#172441`, ink line `#26314F`, ink text `#E7ECF6`, ink muted `#8A97B4`
  - bone `#F5F4EF`, surface `#FFFFFF`, line `#E5E2D9`, text `#172230`, muted `#657182`
  - sapphire (primary) `#2D4BC4` / dark `#21399B` / soft `#E7ECFB`
  - **gold (value/donor accent)** `#A9791F` / soft `#F3E9CF`
  - emerald `#1C8A5A`, amber `#C9791C`, red `#BB3B2E`, violet `#6D4AA6`
- **Type:** display/numerals = **Fraunces** (used with restraint for KPI numbers + wordmark); UI = **Inter**; data/IDs = **IBM Plex Mono**.
- **Signature 1 — Career Trajectory:** a horizontal connected-node timeline in the alumni profile drawer; nodes rise with seniority, final node gold, event tags (Graduated / Promotion / Employer change) under each.
- **Signature 2 — Live Pipeline Visualiser:** the Data Import screen shows the 4 stages (Upload → LLM normalise → L1/L2/L3 match → Insert/update) animating through running→done, then a result summary (total/new/updated/unchanged/failed).
- Restraint: spend boldness on the two signatures; keep everything else quiet. Respect reduced-motion, keyboard focus, responsive to mobile.

### Screens to build
**Super Admin (dark):** Manage Customers (Active + Pending tabs, Invite modal, Approve/Deny), Data Import (tenant select + upload + pipeline), Data Permissions (21 toggles, 7 categories, reset).
**University (light):** Dashboard (4 KPIs + 4 charts + recent events), Alumni Database (search/filter/table + profile drawer w/ trajectory + donor block), Donor Insights (ranked, sortable, gated), Alerts (filterable), Reports (exportable list), Audit Log, Settings.
**Shared:** Login (role-aware), sidebar nav, role badge, sign out.

---

## 11. REST API contract (implement all; all tenant-scoped via JWT)

```
POST   /api/auth/login                 → {token, role, tenantId, user}
POST   /api/auth/logout
GET    /api/auth/me

# Super Admin (role=superadmin)
POST   /api/superadmin/invite          {email, organization}            → 200 | 409
GET    /api/superadmin/requests        ?status=pending
POST   /api/superadmin/requests/{id}/approve
POST   /api/superadmin/requests/{id}/deny
GET    /api/superadmin/customers       → active tenants + aggregates
POST   /api/superadmin/import          (multipart, tenantId, file)      → 202 batch
GET    /api/superadmin/permissions/{tenantId}
PUT    /api/superadmin/permissions/{tenantId}   {permissionKey, enabled}
POST   /api/superadmin/permissions/{tenantId}/reset

# Public registration (token-gated)
GET    /api/register/{token}           → 200 | 410 (expired/used)
POST   /api/register/{token}           {name, jobTitle, ...}            → creates customer_request

# University (role=admin|readonly; writes = admin only)
GET    /api/alumni                      ?query=&industry=&seniority=&page=
GET    /api/alumni/{id}/profile
GET    /api/alumni/{id}/history
PUT    /api/alumni/{id}/anonymise       (admin)
POST   /api/users                       (admin)  → 201 | 409
GET    /api/dashboard/metrics
GET    /api/donors                      ?sort=likelihood|capacity|recent
GET    /api/alerts                      ?type=
GET    /api/audit-logs
GET    /api/reports/{type}/export       → file
```
Errors must be informative and never leak stack traces (e.g. auth service down → "Service temporarily unavailable").

---

## 12. Non-functional requirements (acceptance)

- **Performance:** search/filter ≤ 5 s; profile load ≤ 1 s; dashboard ≤ 8 s.
- **Security:** JWT + RBAC enforced server-side; tenant isolation verified (cross-tenant attempt → 403, no data); BCrypt passwords.
- **Reliability:** pipeline tolerates single-row LLM failure (retry/backoff, isolate, continue).
- **Compliance:** PDPA 2010 — anonymise capability + audit trail; no direct scraping (CSV ingest only). State this in the README.
- **Compatibility:** latest Chrome, Firefox, Edge, Safari.

---

## 13. Testing (mirror the STD — required)

Provide tests covering: functional (normal-flow) for each use case, negative (alt/exception, boundary), and **white-box branch coverage** for the L1/L2/L3 matching tree and the LLM retry logic. Boundary musts: 50.00 MB accepted / 50.1 MB rejected; expired (410) and used (410) tokens; duplicate email (409); cross-tenant access (403). Backend: JUnit + Spring Boot Test. Frontend: Vitest + React Testing Library for the pipeline and search flows. Aim: all functional + non-functional + negative cases covered.

---

## 14. Docker

`docker-compose.yml` runs `frontend` (Vite build served via nginx) and `backend` (Spring Boot). `.env.example` lists: `OPENAI_API_KEY`, `SUPABASE_URL`, `SUPABASE_KEY`, `JWT_SECRET`, `DB_URL`. `docker compose up` must bring the system up cleanly. Document run steps in `README.md`.

---

## 15. Seed data (for demo & tests)

Seed: 1 superadmin (`amralwaeli9@gmail.com`); 3 active tenants (UTM, UM, USM) with the default permission set; ~1,000+ alumni for UTM with profiles, snapshots, and some career events; 2 pending customer requests; sample alerts and audit logs. Use a deterministic seed so demos are stable.

---

## 16. Build order (milestones — finish & verify each before next)

1. **M1** Scaffold monorepo + Docker skeleton + `.env.example`.
2. **M2** DB migrations (12 entities) + RLS + seed.
3. **M3** Auth + JWT + RBAC + tenant claim + portal routing.
4. **M4** Tenant module (invite → request → approve/deny → seed).
5. **M5** Import + LLM normalisation + L1/L2/L3 matching + insert/update + career events.
6. **M6** Profile/Search + Analytics (dashboard, donor, alerts, audit).
7. **M7** Permissions (21 toggles + reset + gating donor insights).
8. **M8** Frontend design pass to match the prototype (both shells, 2 signatures).
9. **M9** Tests (functional + negative + white-box).
10. **M10** Docker up, README, final verification against §9 and §12.

---

## 17. Hard constraints (do not violate)

- Multi-tenant from day one — never single-tenant. `tenant_id` on every tenant row; JWT-derived; cross-tenant = 403.
- LLM normalisation runs **before** matching. Matching is **internal only** (no external calls).
- AlumIndex **never scrapes**; it ingests a third-party CSV/XLSX export.
- Exactly 3 roles, 2 portals. 18 use cases. 8 modules. 21 permission fields / 7 categories.
- Match the prototype's design system; do not ship generic default-blue AI UI.
- Keep `PROGRESS.md` current; verify each milestone builds and tests pass before continuing.

**Start with M1 now. Confirm the plan in one short paragraph, then build.**
