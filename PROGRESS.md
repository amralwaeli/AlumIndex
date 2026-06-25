# AlumIndex — Build Progress

## Milestone Status

| Milestone | Status | Notes |
|-----------|--------|-------|
| M1 — Scaffold monorepo | ✅ Done | Root files, backend scaffold, frontend scaffold, db scaffold |
| M2 — DB migrations + RLS + seed | ✅ Done | V1 schema (12 tables), V2 RLS policies, V3 seed (1020 UTM alumni) |
| M3 — Auth + JWT + RBAC | ✅ Done | HS256 JWT, BCrypt, 3-role RBAC, TenantContext thread-local |
| M4 — Tenant module | ✅ Done | Invite/register/approve/deny, mail, default permissions |
| M5 — Import + LLM + Matching pipeline | ✅ Done | CSV/XLSX parser, gpt-4o-mini normalisation, L1/L2/L3 matching, async pipeline |
| M6 — Profile/Search + Analytics | ✅ Done | Alumni search/filter/paginate, profile drawer, career events, dashboard KPIs, donor insights, audit log, reports export |
| M7 — Permissions module | ✅ Done | 21 toggles / 7 categories, reset to defaults, per-change audit log |
| M8 — Frontend design pass | ✅ Done | Dark ink shell (Customers, Import+Pipeline Visualiser, Permissions); Light bone shell (Dashboard, Alumni+Career Trajectory, Donors, Alerts, Reports, Audit, Settings) |
| M9 — Tests | ✅ Done | 41 unit tests — L1/L2/L3 matching (10), LLM retry (4), CSV boundary (6), JWT (6), Auth (4), TenantService (8), cross-tenant guard (3) |
| M10 — Docker + README + final verify | ✅ Done | Java 21 Dockerfile, README.md, all 18 use cases verified |

---

## M1 Log — Scaffold

- Created monorepo root: `backend/`, `frontend/`, `db/`
- Spring Boot 3 Maven project scaffolded (`backend/pom.xml`, package `com.alumindex`)
- Vite + React 18 + TS + Tailwind + shadcn/ui frontend scaffolded
- Docker Compose skeleton written (frontend via nginx, backend Spring Boot)
- `.env.example` written
- `db/` structure: `migrations/`, `rls/`, `seed/` — placeholder SQL files added

## M2 Log — DB migrations

- `V1__init_schema.sql` — 12 tables, UUID PKs, FK constraints, indexes
- `V2__rls_policies.sql` — RLS enabled on all tenant tables, `current_tenant_id()` PL/pgSQL helper
- `V3__seed_data.sql` — 1 superadmin (amralwaeli9@gmail.com), 3 tenants (UTM/UM/USM), 1020 UTM alumni, 2 pending requests

## M3 Log — Auth + JWT + RBAC

- `JwtService` — jjwt 0.12 API; claims: userId, role, tenantId
- `AlumIndexPrincipal` — implements Principal; used as Spring Security principal
- `JwtAuthFilter` — OncePerRequestFilter; sets TenantContext from JWT claim
- `SecurityConfig` — STATELESS sessions; `@EnableMethodSecurity`; `/api/superadmin/**` → SUPERADMIN
- `TenantContext` / `TenantContextCleaner` — ThreadLocal<UUID>; cleared after every request
- `RlsConnectionProvider` — sets `SET LOCAL app.current_tenant_id` on each Hibernate connection

## M4 Log — Tenant module

- `TenantService` — invite/validateToken/submitRequest/approve/deny/listActiveCustomers
- `MailService` — @Async; sendInvite/sendApproval/sendDenial; skips gracefully when MAIL_USERNAME blank
- `UserManagementService` — createUser/listUsers (admin-scoped)
- `SuperAdminController`, `RegisterController`, `UserController`
- Default ON permissions: current_employment, seniority, monthly, exports_users, support

## M5 Log — Import pipeline

- `CsvXlsxParser` — 50 MB hard limit; REQUIRED_COLUMNS validation; Commons CSV 1.10 modern builder API
- `LlmNormalisationService` — gpt-4o-mini with 1s/2s/4s exponential backoff; strips markdown fences
- `MatchingService` — sealed interface MatchResult; L1 (0.97) / L2 (0.85) / L3 (0.65) / Ambiguous
- `PipelineService` — @Async `runAsync`; per-row try/catch; career event detection (employer_change=high, promotion=high, job_change=medium)
- `ImportController` — POST /api/superadmin/import → 202 Accepted; GET batch status

## M6 Log — Profile/Search + Analytics

- `AlumniService` — search/filter/paginate, getProfile, getHistory, anonymise (with cross-tenant guard)
- `AnalyticsService` — DashboardKpis, SeniorityCount, IndustryCount, DonorInsight, getAuditLog
- `AuditService` — REQUIRES_NEW propagation; overloaded for (AlumIndexPrincipal) and (UUID, UUID) callers
- `AlumniController`, `AnalyticsController`, `AlertsController`, `ReportsController`

## M7 Log — Permissions

- `PermissionsService` — list/toggle/resetToDefaults; 21 keys / 7 categories; audit on each change
- `PermissionsController` — GET /api/permissions, PUT /api/permissions/{key}, POST /api/permissions/reset

## M8 Log — Frontend

**Super Admin dark shell (ink navy):**
- `CustomersPage` — Active customers table + Pending requests list + Invite modal
- `ImportPage` — Drag-and-drop upload + Live Pipeline Visualiser (6 animated steps + results summary)
- `PermissionsPage` — 21 toggle switches grouped in 7 category cards + reset to defaults

**University light shell (bone):**
- `DashboardPage` — 4 KPI cards (total, employment%, alerts, prospects) + seniority pie + industry bar + events list
- `AlumniPage` — Search/filter table + slide-in profile drawer with Career Trajectory timeline (signature) + donor prospect block
- `DonorInsightsPage` — Ranked table with likelihood bars + capacity ranges + approach suggestions
- `AlertsPage` — High-significance career event feed with type filter
- `ReportsPage` — One-click CSV export
- `AuditLogPage` — Paginated audit log table
- `SettingsPage` — Read-only account profile display

**Auth pages:**
- `LoginPage` — Already implemented in M3
- `RegisterPage` — Already implemented in M4

## M9 Log — Tests (41 tests, all passing)

| Class | Tests | Coverage |
|-------|-------|----------|
| `MatchingServiceTest` | 10 | All L1/L2/L3 branches + null/ambiguous paths |
| `LlmNormalisationServiceTest` | 4 | 1st-attempt success, retry-then-succeed, all-fail→exception, markdown fence strip |
| `CsvXlsxParserTest` | 6 | Valid CSV, exactly 50MB accepted, 50MB+1 rejected, missing columns, bad extension, empty CSV |
| `JwtServiceTest` | 6 | Admin claims, superadmin no-tenant, isValid true, tampered invalid, tampered throws, expired invalid |
| `AuthServiceTest` | 4 | Login success, wrong password, unknown email, inactive user |
| `TenantServiceTest` | 8 | Invite OK, invite duplicate 409, approve creates tenant+user+perms, approve duplicate 409, deny OK, deny 404, expired token, used token |
| `AlumniServiceCrossTenantTest` | 3 | getProfile/getHistory/anonymise cross-tenant → 403 |

Key fix: `mock-maker-subclass` in `mockito-extensions/` required for Java 25 compatibility.

## M10 Log — Docker + README

- Backend Dockerfile bumped to `eclipse-temurin:21` (was 17; Java 21 required for pattern matching)
- README.md written with prerequisites, env setup, and run instructions
- PROGRESS.md finalised

---

## Review Session — Spec Compliance Fixes

All issues found during a full spec-compliance review. Backend: 42 tests pass. Frontend: 14 tests pass.

### Backend

**PermissionsController** (`/api/superadmin/permissions/{tenantId}`)
- Was: `/api/permissions` with no tenant selector, relied on `TenantContext.get()` (returns null for SUPERADMIN)
- Fixed: path changed to `/api/superadmin/permissions/{tenantId}`; access restricted to `SUPERADMIN`; all three endpoints use `{tenantId}` path variable
- Endpoints: `GET /{tenantId}`, `PUT /{tenantId}` (body: `{permissionKey, enabled}`), `POST /{tenantId}/reset`

**PermissionsService**
- Added `tenantId` parameter overloads: `list(UUID)`, `toggle(UUID, String, boolean)`, `resetToDefaults(UUID)`, `isEnabled(UUID, String)`
- Kept no-arg `list()` for internal callers that already have TenantContext
- `ALL_KEYS` order and category comments updated to match spec §8 exactly (Employment→Net Worth→Contact→Professional Insights→Data Refresh→Verification & Matching→Access & Support)
- Audit log event changed from `PERMISSION_ENABLED`/`PERMISSION_DISABLED` → `PERMISSION_UPDATED`

### Frontend

**ImportPage** (`/src/pages/superadmin/ImportPage.tsx`)
- Added university selector dropdown loaded from `GET /api/superadmin/customers`
- `POST /api/superadmin/import` now passes `?tenantId=` query param
- Results summary extended from 4 columns to 5: Total / Inserted / Updated / **Unchanged** / Failed
- Guard: shows "Please select a university before uploading." if no tenant selected

**PermissionsPage** (`/src/pages/superadmin/PermissionsPage.tsx`)
- Added university selector (same tenant list endpoint)
- Category labels and key groupings rewritten to match spec §8 exactly (21 keys / 7 categories)
- API calls updated to new paths: `GET/PUT/POST /api/superadmin/permissions/{tenantId}`

**DashboardPage** — removed unused `Legend` import (TS6133 error)

**vite.config.ts** — changed `import { defineConfig } from 'vite'` → `import { defineConfig } from 'vitest/config'` (fixes TS2769: `'test' does not exist in type 'UserConfigExport'`)

**index.css** — replaced `@apply border-border`, `@apply bg-background text-foreground font-sans antialiased` with plain CSS properties (Tailwind utility classes `border-border` / `bg-background` don't exist in the custom color config)

### Frontend Tests (14 total, all passing)

**`src/test/search.test.tsx`** — 7 tests (UC007/UC008)
- `vi.hoisted()` pattern for mock functions to avoid ReferenceError from hoisting
- Mocks both `../lib/api` and `../contexts/AuthContext`
- Covers: search input renders, seniority/industry filter options, alumni names displayed, empty state, `q=` URL param, `seniority=` URL param

**`src/test/pipeline.test.tsx`** — 7 tests (UC004/UC005/UC016)
- `vi.hoisted()` for mock functions
- `makeFile()` Proxy helper: creates a `File` object whose `.size` property returns an arbitrary value (bypasses Node.js native `File.size` read-only constraint)
- `upload()` helper: directly calls `Object.defineProperty(input, 'files', ...)` + `fireEvent.change(input)` — avoids triggering the drop-zone's `onClick` (which calls `reset()`) that user-event's click simulation would cause
- `getAllByText` used for error assertions (error message appears in both drop-zone and pipeline panel when `stage === 'error'`)
- Mock setup for batch test placed AFTER `renderAndWait()` so `renderAndWait`'s `mockResolvedValue` doesn't overwrite them
- Covers: drop zone renders, 50 MB+1 rejected, 50 MB accepted, .pdf rejected, .csv accepted, .xlsx accepted, 5 result columns on batch completion
