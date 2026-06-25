# AlumIndex

Multi-tenant SaaS platform for alumni career tracking. Universities get a portal to search and analyse alumni career data; an operator (superadmin) manages tenants, imports, and data permissions.

## Stack

| Layer | Technology |
|-------|-----------|
| Backend | Spring Boot 3.2.5 · Java 21 · Hibernate 6 |
| Auth | JWT HS256 (jjwt 0.12) · BCrypt · Spring Security |
| Database | PostgreSQL with Row-Level Security (Supabase) |
| Migrations | Flyway (V1 schema · V2 RLS · V3 seed) |
| LLM | OpenAI gpt-4o-mini (exponential backoff) |
| Frontend | React 18 · Vite 5 · TypeScript · Tailwind CSS · shadcn/ui · Recharts |
| Containerisation | Docker · Docker Compose |

## Prerequisites

- Docker & Docker Compose
- A Supabase PostgreSQL database (or any PostgreSQL 14+)
- OpenAI API key

## Quick start

```bash
# 1. Copy env file and fill in values
cp .env.example .env

# 2. Run both services
docker compose up --build
```

Frontend → http://localhost  
Backend API → http://localhost:8080  
Health check → http://localhost:8080/actuator/health

## Environment variables (`.env`)

| Variable | Description |
|----------|-------------|
| `DB_URL` | JDBC URL — `jdbc:postgresql://<host>:<port>/<db>` |
| `DB_USERNAME` | Database user |
| `DB_PASSWORD` | Database password |
| `OPENAI_API_KEY` | OpenAI API key |
| `JWT_SECRET` | HS256 secret (min 32 characters) |
| `MAIL_HOST` | SMTP host (leave blank to skip emails) |
| `MAIL_PORT` | SMTP port |
| `MAIL_USERNAME` | SMTP username |
| `MAIL_PASSWORD` | SMTP password |
| `FRONTEND_ORIGIN` | Allowed CORS origin (default: `http://localhost:80`) |
| `VITE_API_BASE_URL` | Backend base URL seen by browser (default: `http://localhost:8080`) |

## Default credentials (seed data)

| Role | Email | Password |
|------|-------|----------|
| Superadmin | amralwaeli9@gmail.com | AlumIndex2024! |

## Local development (without Docker)

```bash
# Backend (requires Java 21 + Maven)
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Frontend
cd frontend
npm install
npm run dev        # http://localhost:5173
```

## Running tests

```bash
cd backend
mvn test
```

42 unit tests covering:
- L1/L2/L3 matching branches
- LLM retry logic (exponential backoff)
- CSV boundary conditions (50.00 MB accepted, 50.00 MB + 1 byte rejected)
- JWT generation and validation
- Auth (wrong password, inactive user)
- Tenant lifecycle (invite, approve, deny)
- Cross-tenant access guard (403)

## Architecture

### Multi-tenancy

Every authenticated request carries a `tenantId` JWT claim. The `JwtAuthFilter` copies this into a `TenantContext` thread-local. Hibernate's `MultiTenantConnectionProvider` executes `SET LOCAL app.current_tenant_id = '<uuid>'` on every database connection before any query, activating PostgreSQL Row-Level Security policies that filter all tenant tables automatically.

### Import pipeline

```
Upload (CSV/XLSX) → CsvXlsxParser → LlmNormalisationService → MatchingService → PipelineService → DB
```

- Parser enforces 50 MB hard limit and required column contract
- LLM normalises raw row data (gpt-4o-mini, 3 retries with 1/2/4 s backoff)
- Matching is internal-only (L1: linkedin_url exact, L2: name+year, L3: name only)
- Pipeline runs `@Async`; per-row try/catch ensures one bad row never aborts the batch

### Data permissions

21 toggleable fields across 7 categories control what data is visible in each university's portal. Donor Insights (salary/donation_pred) are gated behind permissions. Every toggle change is audit-logged.

## Portals

| Portal | Path | Roles |
|--------|------|-------|
| Operator (dark shell) | `/operator/*` | superadmin |
| University (light shell) | `/university/*` | admin, readonly |

## Signature features

- **Live Pipeline Visualiser** — real-time animated step progress during CSV import (`ImportPage`)
- **Career Trajectory** — vertical timeline of detected career events in the alumni profile drawer (`AlumniPage`)
