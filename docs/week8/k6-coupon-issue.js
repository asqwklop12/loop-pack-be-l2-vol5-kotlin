import http from 'k6/http';
import exec from 'k6/execution';
import { Counter } from 'k6/metrics';

const BASES = (__ENV.BASE_URLS || 'http://localhost:8080').split(',');
const CAMPAIGN_ID = __ENV.CAMPAIGN_ID || '2';
const USER_OFFSET = Number(__ENV.USER_OFFSET || 100000);

const created = new Counter('issue_201');
const existing = new Counter('issue_200');
const rejected = new Counter('issue_400');
const tooMany = new Counter('issue_429');
const other = new Counter('issue_other');

export const options = {
  scenarios: {
    issue: { executor: 'shared-iterations', vus: 100, iterations: 1000, maxDuration: '2m' },
  },
  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
};

export default function () {
  const userId = USER_OFFSET + exec.scenario.iterationInTest;
  const res = http.post(`${BASES[exec.scenario.iterationInTest % BASES.length]}/api/v1/coupon-campaigns/${CAMPAIGN_ID}/issues`, null, {
    headers: { 'X-USER-ID': String(userId) },
  });
  if (res.status === 201) created.add(1);
  else if (res.status === 200) existing.add(1);
  else if (res.status === 400) rejected.add(1);
  else if (res.status === 429) tooMany.add(1);
  else other.add(1);
}
