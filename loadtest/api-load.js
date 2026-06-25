/**
 * AlumIndex API load test.
 *
 * Usage:
 *   k6 run loadtest/api-load.js                          # default: load profile
 *   k6 run -e PROFILE=smoke loadtest/api-load.js         # 1 VU sanity check
 *   k6 run -e PROFILE=stress loadtest/api-load.js        # find the breaking point
 *
 * The script logs in once per VU (setup shares one token — JWTs are stateless
 * so one token is valid for all VUs) and then walks the typical university
 * admin journey: dashboard → alumni search → profile drawer → alerts → donors.
 */
import http from 'k6/http'
import { check, group, sleep } from 'k6'
import { Rate, Trend } from 'k6/metrics'

const BASE = __ENV.BASE_URL || 'http://localhost:8081'
const EMAIL = __ENV.K6_EMAIL || 'admin@utm.edu.my'
const PASSWORD = __ENV.K6_PASSWORD || 'Admin@UTM2024'

const errorRate = new Rate('app_errors')
const dashboardTrend = new Trend('dashboard_ms', true)
const searchTrend = new Trend('alumni_search_ms', true)
const donorsTrend = new Trend('donors_ms', true)

const PROFILES = {
  smoke: {
    vus: 1,
    duration: '30s',
  },
  load: {
    stages: [
      { duration: '30s', target: 10 },   // warm up
      { duration: '1m',  target: 25 },   // normal traffic
      { duration: '2m',  target: 25 },   // sustain
      { duration: '30s', target: 0 },    // ramp down
    ],
  },
  stress: {
    stages: [
      { duration: '30s', target: 25 },
      { duration: '1m',  target: 50 },
      { duration: '1m',  target: 100 },
      { duration: '30s', target: 0 },
    ],
  },
}

const profile = PROFILES[__ENV.PROFILE || 'load']

export const options = {
  ...profile,
  thresholds: {
    // DB is a remote Supabase pooler (ap-northeast-1), so network RTT dominates.
    http_req_failed: ['rate<0.01'],          // <1% transport errors
    app_errors: ['rate<0.02'],               // <2% non-200 responses
    http_req_duration: ['p(95)<2000'],       // overall p95 under 2s
    dashboard_ms: ['p(95)<2500'],
    alumni_search_ms: ['p(95)<2000'],
    donors_ms: ['p(95)<3000'],               // loads full tenant profile set
  },
}

export function setup() {
  const res = http.post(
    `${BASE}/api/auth/login`,
    JSON.stringify({ email: EMAIL, password: PASSWORD }),
    { headers: { 'Content-Type': 'application/json' } },
  )
  if (res.status !== 200) {
    throw new Error(`Login failed (${res.status}): ${res.body}`)
  }
  return { token: JSON.parse(res.body).token }
}

export default function (data) {
  const auth = {
    headers: {
      Authorization: `Bearer ${data.token}`,
      'Content-Type': 'application/json',
    },
  }

  group('dashboard', () => {
    const t0 = Date.now()
    const responses = http.batch([
      ['GET', `${BASE}/api/dashboard/metrics`, null, auth],
      ['GET', `${BASE}/api/dashboard/seniority`, null, auth],
      ['GET', `${BASE}/api/dashboard/industry`, null, auth],
      ['GET', `${BASE}/api/dashboard/events`, null, auth],
      ['GET', `${BASE}/api/dashboard/years`, null, auth],
    ])
    dashboardTrend.add(Date.now() - t0)
    for (const r of responses) {
      const ok = check(r, { 'dashboard 200': (x) => x.status === 200 })
      errorRate.add(!ok)
    }
  })

  sleep(Math.random() * 2 + 1)

  group('alumni list + search', () => {
    const page = Math.floor(Math.random() * 10)
    const r1 = http.get(`${BASE}/api/alumni?page=${page}`, auth)
    const ok1 = check(r1, {
      'alumni page 200': (x) => x.status === 200,
      'alumni has content': (x) => JSON.parse(x.body).content.length > 0,
    })
    errorRate.add(!ok1)

    const t0 = Date.now()
    const names = ['Ahmad', 'Siti', 'Nur', 'Hassan', 'Petronas', 'Maybank']
    const q = names[Math.floor(Math.random() * names.length)]
    const r2 = http.get(`${BASE}/api/alumni?query=${q}`, auth)
    searchTrend.add(Date.now() - t0)
    const ok2 = check(r2, { 'alumni search 200': (x) => x.status === 200 })
    errorRate.add(!ok2)

    // Open one profile drawer (history)
    const list = JSON.parse(r1.body).content
    if (list.length > 0) {
      const id = list[Math.floor(Math.random() * list.length)].id
      const r3 = http.get(`${BASE}/api/alumni/${id}/history`, auth)
      const ok3 = check(r3, { 'history 200': (x) => x.status === 200 })
      errorRate.add(!ok3)
    }
  })

  sleep(Math.random() * 2 + 1)

  group('alerts', () => {
    const r1 = http.get(`${BASE}/api/alerts`, auth)
    const ok1 = check(r1, { 'alerts 200': (x) => x.status === 200 })
    errorRate.add(!ok1)

    const r2 = http.get(`${BASE}/api/alerts/unread-count`, auth)
    const ok2 = check(r2, { 'unread-count 200': (x) => x.status === 200 })
    errorRate.add(!ok2)
  })

  sleep(Math.random() * 2 + 1)

  group('donors', () => {
    const t0 = Date.now()
    const r = http.get(`${BASE}/api/donors?sort=likelihood`, auth)
    donorsTrend.add(Date.now() - t0)
    const ok = check(r, { 'donors 200': (x) => x.status === 200 })
    errorRate.add(!ok)
  })

  sleep(Math.random() * 3 + 1)
}
