import http from 'k6/http';
import exec from 'k6/execution';
import { sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const BASES = (__ENV.BASE_URLS || 'http://localhost:8080').split(',');
const CAMPAIGN_ID = __ENV.CAMPAIGN_ID || '2';
const USER_OFFSET = Number(__ENV.USER_OFFSET || 100000);
const USERS = Number(__ENV.USERS || 100);
const RETRY_WAIT_SECONDS = Number(__ENV.RETRY_WAIT_SECONDS || 0.05);

const created = new Counter('issue_201');
const existing = new Counter('issue_200');
const rejected = new Counter('issue_400');
const tooMany = new Counter('issue_429');
const other = new Counter('issue_other');
const attempts = new Trend('attempts_per_user');
const timeToIssue = new Trend('time_to_issue', true);

export const options = {
  scenarios: {
    issue: { executor: 'per-vu-iterations', vus: USERS, iterations: 1, maxDuration: '5m' },
  },
  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
};

export default function () {
  const userId = USER_OFFSET + exec.vu.idInTest;
  const base = BASES[exec.vu.idInTest % BASES.length];
  const startedAt = Date.now();
  let tries = 0;

  while (true) {
    tries += 1;
    const res = http.post(`${base}/api/v1/coupon-campaigns/${CAMPAIGN_ID}/issues`, null, {
      headers: { 'X-USER-ID': String(userId) },
    });
    if (res.status === 429) {
      tooMany.add(1);
      sleep(RETRY_WAIT_SECONDS);
      continue;
    }
    if (res.status === 201) created.add(1);
    else if (res.status === 200) existing.add(1);
    else if (res.status === 400) rejected.add(1);
    else other.add(1);
    break;
  }

  attempts.add(tries);
  timeToIssue.add(Date.now() - startedAt);
}
