# Deploying the AlumIndex backend to Render (free)

The frontend is static (GitHub Pages, https://alumindex.org). The backend is a
Spring Boot app and runs on Render as a Docker web service. The database stays on
Supabase. Config lives in `render.yaml` (a Render Blueprint) at the repo root.

## 1. Create the service
1. Go to https://render.com → **Get Started** → sign in with **GitHub**
   (the `amralwaeli` account that owns this repo). Authorize Render.
2. Dashboard → **New +** → **Blueprint**.
3. Select the **AlumIndex** repo. Render detects `render.yaml` and shows the
   `alumindex-backend` service.
4. Render will prompt for the three secret env vars (`sync: false` in the blueprint).
   Enter your **rotated** values:
   - `DB_PASSWORD` — new Supabase database password
   - `JWT_SECRET`  — new random string, ≥32 chars
   - `OPENAI_API_KEY` — new key (optional; only used for roster import)
5. Click **Apply** / **Create**. Render builds the Docker image and deploys.

## 2. Watch the first deploy
- Open the service → **Logs**. The build runs Maven, then the app boots.
- Look for **Flyway** running the `V1…V12` migrations (this creates the tables and
  seed accounts in Supabase on first boot), then `Started AlumIndexApplication`.
- When the health check at `/actuator/health` passes, the status goes **Live**.

## 3. Get the URL and verify
- Your URL looks like `https://alumindex-backend.onrender.com`.
- Open `https://alumindex-backend.onrender.com/actuator/health` → expect
  `{"status":"UP"}`.

## 4. Point the frontend at the backend
1. GitHub repo → **Settings → Secrets and variables → Actions → Variables tab** →
   set repository variable:
   - Name: `VITE_API_BASE_URL`
   - Value: your Render URL (bare, **no** `/api`, no trailing slash)
2. GitHub → **Actions → Deploy frontend to GitHub Pages → Run workflow**.
3. Hard-refresh https://alumindex.org/login (Ctrl+Shift+R) and sign in.

## Notes & troubleshooting
- **Cold start:** the free instance sleeps after ~15 min idle. The first request
  after that takes ~30–60s to wake — so the first login may be slow, then normal.
- **Memory:** the free plan has 512 MB RAM. This app (Tomcat + Hibernate + Flyway
  + Apache POI) is on the tight side. If the log shows `OutOfMemoryError` /
  `Killed`, either reduce the heap (`-XX:MaxRAMPercentage=60.0` in the Dockerfile)
  or upgrade to the Starter plan.
- **405 on /api/auth/login** → `VITE_API_BASE_URL` still points at alumindex.org;
  it must be the Render URL.
- **CORS error** → `FRONTEND_ORIGIN` must be exactly `https://alumindex.org`.
- **DB connection errors** → re-check `DB_PASSWORD`; confirm the Supabase project
  is active (free Supabase projects pause after inactivity — resume it in the
  Supabase dashboard).
