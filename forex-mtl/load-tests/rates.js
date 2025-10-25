import http from 'k6/http';
import { check } from 'k6';

const currencies = ['USD', 'EUR', 'JPY', 'GBP', 'AUD', 'CAD', 'CHF', 'NZD', 'SGD'];

export const options = {
  scenarios: {
    graceful_ramp: {
      executor: 'ramping-vus',
      startVUs: 0,
      startTime: '5s',
      stages: [
        { duration: '25s', target: 25 },
        { duration: '1m', target: 50 },
        { duration: '25s', target: 25 },
      ],
      gracefulRampDown: '15s',
    },
  },
};

export default function() {
  const from = currencies[Math.floor(Math.random() * currencies.length)];

  let to;
  do {
    to = currencies[Math.floor(Math.random() * currencies.length)];
  } while (to === from);

  const url = `http://forex-app:8081/rates?from=${from}&to=${to}`;
  const res = http.get(url);

  check(res, { 'status is 200': (r) => r.status === 200 });
}

