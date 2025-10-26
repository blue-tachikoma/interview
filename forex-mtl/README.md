# Forex Proxy Service

This project implements a proxy service for fetching currency exchange rates. It is designed to handle high request volumes while respecting the constraints of the oneframe service.

## Problem statement

The oneframe service has the following limitations:

- Maximum 1000 requests per day.
- Data freshness requirement: clients cannot receive data older than 5 minutes.

Forex service needs to handle 10,000+ requests per day and ensure data freshness.

## Solution overview

To address these constraints, the forex service uses the following design:

### 1. Caching with Redis

- Redis is used as an in-memory cache to store currency rates.
- Advantages:
  - Extremely fast read operations (ideal for heavy read scenarios).
  - Supports TTL for automatic cache expiration.
  - Scales horizontally if needed.

### 2. Batch fetching

- The forex service queries the oneframe service at most once every 5 minutes.
- The fetched rates are stored in Redis. No TTL ensures returning stale data on Redis outage.
- All incoming requests during this 5-minute window are served directly from the cache.
- This ensures the 1,000 daily request limit of the oneframe service is not exceeded while serving 10x more requests.

### 3. Distributed locking

- To allow multiple instances of the proxy service to run safely:
  - Redis is used to implement a distributed lock.
  - Only one instance fetches fresh rates when the cache expires, preventing redundant requests to the oneframe service.

### Architecture diagram (simplified)

```
Client
  |
  v
Forex Service (multiple instances)
  |
  v
  +---> Redis Cache (store currency rates)
  |
  +---> OneFrame Service (max 1000 req/day)
```

## Features

- Handles high traffic efficiently.
- Ensures fresh data (max 5 minutes old).
- Supports horizontal scaling via distributed locks.

## API

### Endpoint

GET /rates?from={currency}&to={currency}

- `from`: Required 
- `to`: Required

### Responses

| Status Code | Description | Example Body |
|-------------|-------------|---------------|
| 200 OK | Successfully retrieved rate | `{"from":{"value":"USD"},"to":{"value":"JPY"},"price":{"value":0.39625560098102343},"timestamp":{"value":"2025-10-25T22:35:31.554Z"}}` |
| 400 Bad Request | Missing or invalid `from` or `to` parameter | `Pair UST-JPI is not valid` |
| 500 Internal Server Error | Unexpected server or upstream error | `Failed to get rate for USD-JPY` |

## How to run

### Prerequisites

- Java 17
- sbt
- Docker

### Steps

1. Clone the repository:

```bash
git clone git@github.com:blue-tachikoma/interview.git
cd interview/forex-mtl
```

2. Build Docker image:

```bash
sbt Docker/publishLocal
```

3. Run docker-compose:

```bash
docker compose --profile full up -d
```

4. Make a request:

```bash
curl -X GET "http://localhost:8081/rates?from=USD&to=JPY"
```

## How to test

### Unit tests:

```bash
sbt test
```

### Integration tests:

```bash
sbt ItTest/test
```

### Load test:

```bash
docker compose --profile load-test up -d
```

## Load test result

```
█ TOTAL RESULTS

checks_total.......: 1575258 13697.531874/s
checks_succeeded...: 100.00% 1575258 out of 1575258
checks_failed......: 0.00%   0 out of 1575258

✓ status is 200

HTTP
http_req_blocked...............: avg=1.67µs  min=481ns    med=1.54µs  max=3.83ms   p(90)=2.09µs  p(95)=2.39µs
http_req_connecting............: avg=4ns     min=0s       med=0s      max=351.72µs p(90)=0s      p(95)=0s
http_req_duration..............: avg=2.14ms  min=210.68µs med=1.96ms  max=81.05ms  p(90)=3.43ms  p(95)=4.19ms
  { expected_response:true }...: avg=2.14ms  min=210.68µs med=1.96ms  max=81.05ms  p(90)=3.43ms  p(95)=4.19ms
http_req_failed................: 0.00%   0 out of 1575258
http_req_receiving.............: avg=18.47µs min=4.91µs   med=16.65µs max=7.07ms   p(90)=23.48µs p(95)=26.85µs
http_req_sending...............: avg=5.2µs   min=1.56µs   med=4.75µs  max=5.03ms   p(90)=6.33µs  p(95)=7.11µs
http_req_tls_handshaking.......: avg=0s      min=0s       med=0s      max=0s       p(90)=0s      p(95)=0s
http_req_waiting...............: avg=2.12ms  min=195.97µs med=1.93ms  max=80.88ms  p(90)=3.41ms  p(95)=4.16ms
http_reqs......................: 1575258 13697.531874/s

EXECUTION
iteration_duration.............: avg=2.19ms  min=250.03µs med=2.01ms  max=82.51ms  p(90)=3.48ms  p(95)=4.24ms
iterations.....................: 1575258 13697.531874/s
vus............................: 26      min=0            max=50
vus_max........................: 50      min=50           max=50

NETWORK
data_received..................: 380 MB  3.3 MB/s
data_sent......................: 143 MB  1.2 MB/s
```

## What's next?

- Client authentication. Implement authentication using Bearer tokens, since the service is intended for internal use only.
- Rate limiting. Introduce request rate limiting to prevent accidental overload from internal consumers.
- Observability. Add comprehensive observability to understand the system’s behavior and performance under load:
  - Metrics: Track request latency, request rate, errors.
  - Structured logging: Use JSON-based structured logs to make it easier to analyze events in log aggregation systems.
  - Tracing: Add distributed tracing to track requests across components (forex, Redis, oneframe).
  Stack: Prometheus, Loki, Tempo, Grafana.
- Liveness and Readiness probes. Expose endpoints for health checks to indicate whether the service is running (`/healthz`) and ready to accept traffic (`/readyz`).
- Administrative Endpoints. Add internal-only endpoints to manually trigger rate updates — either for a specific currency pair or to refresh all rates.
- Enhanced Redis connection support. Add support for different Redis topologies, including cluster and master–replica setups.
