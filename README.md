# project-uno ingress

Quarkus service that admits up to **1,000,000 in-flight HTTP requests** at once. Quarkus 3.40 (LTS) on Java 21. The HTTP server is Vert.x (non-blocking, HTTP/2). Each admitted call runs on a virtual thread so downstream I/O does not pin an event-loop thread or a platform thread.

## Capacity model

`app.capacity.max-inflight` (default `1000000`) is a lock-free compare-and-swap gate. A permit is held only while `RequestProcessor` runs, then released in a `finally` block, including when processing throws.

| Limit | Value | Why |
| --- | --- | --- |
| In-flight requests | 1,000,000 | Application gate (`CapacityGuard`) |
| Connections | 1,000,000 | `quarkus.http.limits.max-connections` |
| HTTP/2 streams per connection | 1,024 | One client can overlap many calls without 1,000,000 sockets |
| Request body | 512 bytes | Keeps worst-case buffering bounded |

When the gate is full, `POST /api/v1/requests` returns **429** with `Retry-After: 1` and an `application/problem+json` body. Readiness goes `DOWN` so a load balancer can shift traffic. Liveness stays `UP`.

One million concurrent sockets also depends on the host: file descriptors (`ulimit -n` at or above 1048576), `net.core.somaxconn`, and heap. A single client IP cannot open ~1e6 connections to one port because ephemeral ports stop near 64k. Use HTTP/2 multiplexing, or many source addresses, to reach the request cap. `CapacityGuardTest` acquires all 1,000,000 permits. The HTTP tests do not open 1,000,000 sockets.

Virtual threads make the cap reachable when `RequestProcessor` blocks on I/O. A platform-thread pool sized in the hundreds cannot hold a million waiting calls.

## API

`POST /api/v1/requests`

```json
{ "id": "order-1", "payload": "hello" }
```

- `202` `{ "id", "status": "ACCEPTED", "inflight" }`
- `400` problem document for invalid JSON or bean violations (`id` pattern `[A-Za-z0-9._:-]+`, max 64; `payload` required, max 128)
- `413` when the body exceeds 512 bytes
- `429` when saturated or shutting down

`GET /api/v1/capacity` returns `maxInflight`, `inflight`, `available`, `accepting`.

Send `X-Correlation-Id` (max 64 characters) and the service echoes it. Otherwise it generates a UUID. The id is written to the log MDC and cleared before the worker is reused.

`GET /q/health/live`, `GET /q/health/ready`, `GET /q/metrics`, `GET /q/openapi`.

## Run

```bash
./mvnw quarkus:dev
./mvnw package
java -XX:+UseZGC -Xms2g -Xmx4g -jar target/quarkus-app/quarkus-run.jar
```

Override the cap at runtime with `-Dapp.capacity.max-inflight=1000000` or `APP_CAPACITY_MAX_INFLIGHT`.

For a host that should actually hold the full cap, raise the descriptor limit before the process starts and prefer HTTP/2 clients.

## Tests

```bash
./mvnw verify
```

- `CapacityGuardTest` fills all 1,000,000 slots, checks the next call is rejected, and stress-tests the gate from many virtual threads.
- `IngressApiTest` covers the HTTP contract and a parallel burst.
- `SaturationTest` holds a cap of 4 blocked virtual threads, expects `429` and readiness `503`, then checks recovery.
- `ProcessorFailureTest` checks a thrown processor still returns the permit.

## Layout

```
src/main/java/com/projectuno/ingress/api          REST, correlation, problem responses
src/main/java/com/projectuno/ingress/capacity    admission gate
src/main/java/com/projectuno/ingress/processing  downstream extension point
src/main/java/com/projectuno/ingress/health      liveness and readiness
```

Put blocking work in `RequestProcessor`. Do not block the Vert.x event loop, and do not admit work without going through `CapacityGuard`.
