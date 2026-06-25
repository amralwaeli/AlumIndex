# Deploying the AlumIndex backend to Railway

The frontend is static (GitHub Pages, https://alumindex.org). The backend is a
Spring Boot app and must run on its own host. These steps deploy it to Railway.

## 1. Create the project & database
1. Go to https://railway.app → **New Project** → **Deploy from GitHub repo** →
   pick this repo.
2. In the created service: **Settings → Source → Root Directory** = `backend`.
   (Railway will pick up `backend/Dockerfile` and `backend/railway.json`.)
3. In the same project: **+ New → Database → PostgreSQL**.

## 2. Set backend environment variables
In the **backend service → Variables**, add:

| Variable          | Value                                                                 |
|-------------------|-----------------------------------------------------------------------|
| `DB_URL`          | `jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}` |
| `DB_USERNAME`     | `${{Postgres.PGUSER}}`                                                 |
| `DB_PASSWORD`     | `${{Postgres.PGPASSWORD}}`                                             |
| `JWT_SECRET`      | a random string, **at least 32 characters**                           |
| `FRONTEND_ORIGIN` | `https://alumindex.org`                                                |
| `OPENAI_API_KEY`  | your key (optional — only needed for roster import normalisation)      |
| `MAIL_HOST` / `MAIL_PORT` / `MAIL_USERNAME` / `MAIL_PASSWORD` | optional — only for invite/approval emails |

The `${{Postgres.*}}` values are Railway *reference variables*; they auto-fill
from the Postgres service over the private network. Flyway creates the schema on
first boot, so the database can start empty.

> Do **not** set `PORT` — Railway injects it and the app already reads it.

## 3. Deploy & get the URL
1. Railway builds and deploys automatically. Watch **Deployments → Logs**;
   wait for the Flyway migrations and `Started ...Application`.
2. **Settings → Networking → Generate Domain**. You get a URL like
   `https://alumindex-backend-production.up.railway.app`.
3. Verify it's alive: open `<that-url>/actuator/health` → should show
   `{"status":"UP"}`.
   (Optionally add a custom subdomain like `api.alumindex.org` here, then create
   a matching CNAME at your DNS provider.)

## 4. Point the frontend at the backend
1. GitHub repo → **Settings → Secrets and variables → Actions → Variables tab**
   → **New repository variable**:
   - Name: `VITE_API_BASE_URL`
   - Value: the Railway URL from step 3 (bare, **no** `/api`, no trailing slash)
2. Re-run the deploy: **Actions → Deploy frontend to GitHub Pages → Run workflow**
   (or push any commit to `main`).
3. Hard-refresh https://alumindex.org/login (Ctrl+Shift+R) and sign in.

## Troubleshooting
- **405 on `/api/auth/login`** → `VITE_API_BASE_URL` still points at GitHub Pages
  (alumindex.org). It must point at the Railway host.
- **CORS error in console** → `FRONTEND_ORIGIN` on the backend must be exactly
  `https://alumindex.org` (no trailing slash).
- **Mixed content blocked** → the backend URL must be `https://`, not `http://`.
- **Health check fails / app won't start** → check DB vars; look for Flyway errors
  in the Railway deploy logs.
