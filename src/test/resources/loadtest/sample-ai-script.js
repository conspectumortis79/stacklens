import http from 'k6/http';
import { check, sleep } from 'k6';

// StackLens patches BASE_URL at runtime — derived from docker/stacklens
// (primary service). The docker-compose.yml is truncated in the supplied
// inputs and no application.yml/properties/.env were attached, so the
// best-guess Spring Boot default port (8080) is used. Override with
// `k6 run -e BASE_URL=http://host:port load-test.js` when known.
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
  stages: [
    { duration: '30s', target: 20 },
    { duration: '60s', target: 20 },
    { duration: '10s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<2000'],
    http_req_failed:   ['rate<0.05'],

    // ── Main scenario endpoints (one pair per endpoint) ─────────────────────
    'http_req_duration{name:GET /api/knowledge/jobs}':          ['p(95)<2000'],
    'http_req_failed{name:GET /api/knowledge/jobs}':            ['rate<0.1'],

    'http_req_duration{name:GET /api/knowledge/jobs/1}':        ['p(95)<2000'],
    'http_req_failed{name:GET /api/knowledge/jobs/1}':          ['rate<0.1'],

    'http_req_duration{name:GET /api/config/language}':        ['p(95)<2000'],
    'http_req_failed{name:GET /api/config/language}':          ['rate<0.1'],

    'http_req_duration{name:PUT /api/config/language}':        ['p(95)<2000'],
    'http_req_failed{name:PUT /api/config/language}':          ['rate<0.1'],

    'http_req_duration{name:GET /api/ui-prefs/theme}':         ['p(95)<2000'],
    'http_req_failed{name:GET /api/ui-prefs/theme}':           ['rate<0.1'],

    'http_req_duration{name:GET /config}':                     ['p(95)<2000'],
    'http_req_failed{name:GET /config}':                       ['rate<0.1'],

    'http_req_duration{name:GET /search}':                     ['p(95)<2000'],
    'http_req_failed{name:GET /search}':                       ['rate<0.1'],

    'http_req_duration{name:GET /grafana-analysis}':           ['p(95)<2000'],
    'http_req_failed{name:GET /grafana-analysis}':             ['rate<0.1'],

    'http_req_duration{name:GET /review-analysis}':            ['p(95)<2000'],
    'http_req_failed{name:GET /review-analysis}':              ['rate<0.1'],

    'http_req_duration{name:GET /knowledge}':                  ['p(95)<2000'],
    'http_req_failed{name:GET /knowledge}':                    ['rate<0.1'],

    'http_req_duration{name:GET /tooling}':                     ['p(95)<2000'],
    'http_req_failed{name:GET /tooling}':                       ['rate<0.1'],

    'http_req_duration{name:GET /api/health/probe/gitlab/1}':  ['p(95)<2000'],
    'http_req_failed{name:GET /api/health/probe/gitlab/1}':    ['rate<0.1'],

    // ── Destructive benchmark scenario endpoints (relaxed p95) ─────────────
    'http_req_duration{name:POST /api/knowledge/reindex-all}': ['p(95)<10000'],
    'http_req_failed{name:POST /api/knowledge/reindex-all}':   ['rate<0.1'],

    'http_req_duration{name:POST /api/knowledge/reset}':       ['p(95)<10000'],
    'http_req_failed{name:POST /api/knowledge/reset}':         ['rate<0.1'],

    'http_req_duration{name:POST /api/admin/factory-reset-db}':['p(95)<10000'],
    'http_req_failed{name:POST /api/admin/factory-reset-db}':  ['rate<0.1'],
  },
  gracefulRampDown: '30s',
  gracefulStop:     '30s',
  scenarios: {
    // Runs AFTER the main scenario finishes its warm-up so that the
    // destructive endpoints do not destroy the data the main scenario
    // is supposed to read (lt1, lt2).
    destructiveBench: {
      executor:  'constant-vus',
      vus:       3,
      duration:  '30s',
      exec:      'destructiveBench',
      startTime: '95s',
    },
  },
};

function buildHeaders(token) {
  const h = { 'Content-Type': 'application/json' };
  if (token) h['Authorization'] = 'Bearer ' + token;
  return h;
}

// ────────────────────────────────────────────────────────────────────────────
//  Main scenario — exercises non-destructive read/write endpoints.
// ────────────────────────────────────────────────────────────────────────────
export default function () {
  const headers = buildHeaders(__ENV.JWT_TOKEN || '');

  // GET /api/knowledge/jobs  (KnowledgeJobsController#listJobs — bottleneck lt4)
  let res = http.get(BASE_URL + '/api/knowledge/jobs', {
    headers,
    tags: { name: 'GET /api/knowledge/jobs' },
  });
  check(res, {
    'GET /api/knowledge/jobs → 2xx': (r) => r.status >= 200 && r.status < 300,
    'GET /api/knowledge/jobs → 5xx': (r) => r.status >= 500,
  });
  sleep(0.3);

  // GET /api/knowledge/jobs/1  (KnowledgeJobsController#getJob)
  res = http.get(BASE_URL + '/api/knowledge/jobs/1', {
    headers,
    tags: { name: 'GET /api/knowledge/jobs/1' },
  });
  check(res, {
    'GET /api/knowledge/jobs/1 → 2xx': (r) => r.status >= 200 && r.status < 300,
    'GET /api/knowledge/jobs/1 → 5xx': (r) => r.status >= 500,
  });
  sleep(0.3);

  // GET /api/config/language  (LanguageConfigApiController — bottleneck lt7)
  res = http.get(BASE_URL + '/api/config/language', {
    headers,
    tags: { name: 'GET /api/config/language' },
  });
  check(res, {
    'GET /api/config/language → 2xx': (r) => r.status >= 200 && r.status < 300,
    'GET /api/config/language → 5xx': (r) => r.status >= 500,
  });
  sleep(0.3);

  // PUT /api/config/language  (LanguageConfigApiController — bottleneck lt7)
  res = http.put(BASE_URL + '/api/config/language',
    JSON.stringify({ language: 'en' }),
    {
      headers,
      tags: { name: 'PUT /api/config/language' },
    });
  check(res, {
    'PUT /api/config/language → 2xx': (r) => r.status >= 200 && r.status < 300,
    'PUT /api/config/language → 5xx': (r) => r.status >= 500,
  });
  sleep(0.3);

  // GET /api/ui-prefs/theme  (UiPreferencesController, inferred from
  // UiPreferencesSystemTest — POST on this path is not supported,
  // confirming GET is the correct method)
  res = http.get(BASE_URL + '/api/ui-prefs/theme', {
    headers,
    tags: { name: 'GET /api/ui-prefs/theme' },
  });
  check(res, {
    'GET /api/ui-prefs/theme → 2xx': (r) => r.status >= 200 && r.status < 300,
    'GET /api/ui-prefs/theme → 5xx': (r) => r.status >= 500,
  });
  sleep(0.3);

  // GET /config  (ConfigWebController — bottleneck lt3)
  res = http.get(BASE_URL + '/config', {
    headers,
    tags: { name: 'GET /config' },
  });
  check(res, {
    'GET /config → 2xx': (r) => r.status >= 200 && r.status < 300,
    'GET /config → 5xx': (r) => r.status >= 500,
  });
  sleep(0.3);

  // GET /search  (SearchWebController — bottleneck lt3)
  res = http.get(BASE_URL + '/search', {
    headers,
    tags: { name: 'GET /search' },
  });
  check(res, {
    'GET /search → 2xx': (r) => r.status >= 200 && r.status < 300,
    'GET /search → 5xx': (r) => r.status >= 500,
  });
  sleep(0.3);

  // GET /grafana-analysis  (GrafanaAnalyseController — bottleneck lt3)
  res = http.get(BASE_URL + '/grafana-analysis', {
    headers,
    tags: { name: 'GET /grafana-analysis' },
  });
  check(res, {
    'GET /grafana-analysis → 2xx': (r) => r.status >= 200 && r.status < 300,
    'GET /grafana-analysis → 5xx': (r) => r.status >= 500,
  });
  sleep(0.3);

  // GET /review-analysis  (ReviewWebController — bottleneck lt3)
  res = http.get(BASE_URL + '/review-analysis', {
    headers,
    tags: { name: 'GET /review-analysis' },
  });
  check(res, {
    'GET /review-analysis → 2xx': (r) => r.status >= 200 && r.status < 300,
    'GET /review-analysis → 5xx': (r) => r.status >= 500,
  });
  sleep(0.3);

  // GET /knowledge  (KnowledgeWebController — bottleneck lt3)
  res = http.get(BASE_URL + '/knowledge', {
    headers,
    tags: { name: 'GET /knowledge' },
  });
  check(res, {
    'GET /knowledge → 2xx': (r) => r.status >= 200 && r.status < 300,
    'GET /knowledge → 5xx': (r) => r.status >= 500,
  });
  sleep(0.3);

  // GET /tooling  (ToolingWebController — bottleneck lt3)
  res = http.get(BASE_URL + '/tooling', {
    headers,
    tags: { name: 'GET /tooling' },
  });
  check(res, {
    'GET /tooling → 2xx': (r) => r.status >= 200 && r.status < 300,
    'GET /tooling → 5xx': (r) => r.status >= 500,
  });
  sleep(0.3);

  // GET /api/health/probe/gitlab/1  (ConnectionHealthUseCase#probeOne — bottleneck lt5)
  res = http.get(BASE_URL + '/api/health/probe/gitlab/1', {
    headers,
    tags: { name: 'GET /api/health/probe/gitlab/1' },
  });
  check(res, {
    'GET /api/health/probe/gitlab/1 → 2xx': (r) => r.status >= 200 && r.status < 300,
    'GET /api/health/probe/gitlab/1 → 5xx': (r) => r.status >= 500,
  });

  sleep(1);
}

// ────────────────────────────────────────────────────────────────────────────
//  Destructive benchmark scenario — runs after the main scenario.
//  These endpoints wipe/rebuild state and therefore MUST NOT live in
//  default() (see StackLens performance review bottlenecks lt1, lt2).
// ────────────────────────────────────────────────────────────────────────────
export function destructiveBench() {
  const headers = buildHeaders(__ENV.JWT_TOKEN || '');

  // POST /api/knowledge/reindex-all  (KnowledgeOperationsController — bottleneck lt2)
  let res = http.post(BASE_URL + '/api/knowledge/reindex-all', null, {
    headers,
    tags: { name: 'POST /api/knowledge/reindex-all' },
  });
  check(res, {
    'POST /api/knowledge/reindex-all → 2xx': (r) => r.status >= 200 && r.status < 300,
    'POST /api/knowledge/reindex-all → 5xx': (r) => r.status >= 500,
  });
  sleep(2);

  // POST /api/knowledge/reset  (KnowledgeOperationsController — bottleneck lt2)
  res = http.post(BASE_URL + '/api/knowledge/reset', null, {
    headers,
    tags: { name: 'POST /api/knowledge/reset' },
  });
  check(res, {
    'POST /api/knowledge/reset → 2xx': (r) => r.status >= 200 && r.status < 300,
    'POST /api/knowledge/reset → 5xx': (r) => r.status >= 500,
  });
  sleep(2);

  // POST /api/admin/factory-reset-db  (AdminController — bottleneck lt1)
  res = http.post(BASE_URL + '/api/admin/factory-reset-db', null, {
    headers,
    tags: { name: 'POST /api/admin/factory-reset-db' },
  });
  check(res, {
    'POST /api/admin/factory-reset-db → 2xx': (r) => r.status >= 200 && r.status < 300,
    'POST /api/admin/factory-reset-db → 5xx': (r) => r.status >= 500,
  });

  sleep(5);
}