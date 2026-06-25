# AlumIndex Load Tests (K6)

Performance test suite for the AlumIndex backend API using [Grafana K6](https://k6.io).

## Prerequisites

- Backend running on `http://localhost:8081` (`.\start-backend.ps1` from project root)
- Seeded database (V3 seed: UTM tenant with 1,020 alumni / ~200 career events)
- K6 binary — either on PATH or use the bundled `k6-v0.57.0-windows-amd64\k6.exe`

## Running

```powershell
# From the project root:
.\loadtest\k6-v0.57.0-windows-amd64\k6.exe run loadtest\api-load.js                 # load (default)
.\loadtest\k6-v0.57.0-windows-amd64\k6.exe run -e PROFILE=smoke loadtest\api-load.js   # 1 VU sanity
.\loadtest\k6-v0.57.0-windows-amd64\k6.exe run -e PROFILE=stress loadtest\api-load.js  # up to 100 VUs
```

## Profiles

| Profile | Shape | Purpose |
|---------|-------|---------|
| `smoke` | 1 VU × 30 s | Verify every endpoint returns 200 |
| `load` (default) | ramp 0→25 VUs, hold 2 min | Normal-traffic SLA validation |
| `stress` | ramp to 100 VUs | Find the breaking point |

## What it simulates

Each virtual user walks the real university-admin journey:
dashboard (5 parallel calls) → alumni list + search + profile drawer →
alerts + unread badge → donor insights, with think-time sleeps between steps.

## Thresholds (SLA)

- Transport errors < 1 %, application errors < 2 %
- Overall p95 < 2 s (DB is a remote Supabase pooler in ap-northeast-1, so ~110 ms
  network RTT is baked into every query)
- Dashboard batch p95 < 2.5 s · Alumni search p95 < 2 s · Donors p95 < 3 s

## Bugs this suite caught (2026-06-12)

1. **`lower(bytea)` SQL error** — null search param bound inside `CONCAT` made
   PostgreSQL infer `bytea`; every `/api/alumni` call 500'd. Fixed by binding a
   pre-computed `%pattern%` parameter.
2. **`LazyInitializationException`** — `/api/alerts` and `/api/dashboard/events`
   serialized JPA entities whose lazy `alumni.tenant` proxy escaped the session.
   Fixed by mapping to DTOs inside the transaction.
3. **N+1 on one-to-one** — `Alumni.profile` (mappedBy) cannot be lazy-proxied, so
   200 events fired 200 extra SELECTs ≈ 23 s/request on the remote pooler. Fixed
   with `LEFT JOIN FETCH` (23 s → 0.6 s, ~39× faster).
