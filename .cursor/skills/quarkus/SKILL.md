---
name: quarkus
description: Use when building, changing, reviewing, or testing Quarkus Java services in this repository, including REST endpoints, configuration, health, metrics, virtual threads, admission control, or the project-uno ingress. Use whenever the task mentions Quarkus, Jakarta REST, SmallRye, Vert.x on Quarkus, or MicroProfile in this repo.
---

# Quarkus in this repo

The only service is `ingress` (`com.projectuno:ingress`), a Quarkus 3.40.1 LTS application on Java 21. Follow this layout and these commands instead of inventing a second stack.

## Commands

From the repository root:

```bash
./mvnw verify          # unit tests + @QuarkusTest
./mvnw quarkus:dev     # dev mode with reload
./mvnw package         # fast-jar at target/quarkus-app/
```

Do not add Gradle. Do not bump the Quarkus BOM casually; pin `quarkus.platform.version` in `pom.xml` and keep the BOM import.

## Where code goes

| Change | Location |
| --- | --- |
| HTTP resource | `src/main/java/com/projectuno/ingress/api` |
| Admission / concurrency limit | `src/main/java/com/projectuno/ingress/capacity` |
| Blocking or downstream I/O | `src/main/java/com/projectuno/ingress/processing` |
| Health | `src/main/java/com/projectuno/ingress/health` |
| Config | `src/main/resources/application.properties` (`%test` / `%prod` prefixes) |
| Tests | same package under `src/test/java` |

Constructor injection only. Config that operators will override uses `@ConfigMapping`, not scattered `@ConfigProperty` strings.

## Concurrency contract

The service is built to hold **1,000,000 in-flight requests**:

- Vert.x event loops accept sockets and must stay non-blocking.
- `RequestResource.submit` is annotated `@RunOnVirtualThread` (`io.smallrye.common.annotation.RunOnVirtualThread`). Blocking work belongs in `RequestProcessor`, which runs on that virtual thread.
- Every admitted call takes one `CapacityGuard` permit and releases it in `finally`, including failures.
- New endpoints that do real work must use the same guard. Health, metrics, and `GET /api/v1/capacity` stay outside the gate so probes still work when the service is saturated.
- Keep `quarkus.http.limits.max-body-size` small. A large default body times one million buffered requests will exhaust the heap.
- Do not replace the gate with a fixed platform-thread pool. A pool of a few hundred threads cannot park a million waiting calls.
- Log rejections at debug and count them (`ingress.capacity.rejected`). An info log per rejection becomes a million-line storm.

`CapacityGuardTest.admitsOneMillionThenRejectsTheNext` is the check that the gate actually reaches the cap. Do not delete it or lower it to a token loop.

## HTTP and errors

- JSON in, JSON out. Unknown properties fail (`quarkus.jackson.fail-on-unknown-properties=true`).
- Bean validation runs in the resource before admission, so bad payloads do not consume a permit.
- Errors that clients can act on use `application/problem+json` via `Problems`.
- Echo or mint `X-Correlation-Id`. `CorrelationFilter` must clear MDC on the way out; carriers are reused.
- Readiness reports DOWN when the gate is shut or full. Liveness stays UP.

## Tests

- Pure gate behavior is a plain JUnit test that constructs `CapacityGuard` with `SimpleMeterRegistry`. Do not boot Quarkus for that.
- HTTP behavior uses `@QuarkusTest` and REST Assured.
- A class that uses `io.quarkus.test.InjectMock` needs its own `@TestProfile`. The dependency is `quarkus-junit-mockito` (the old `quarkus-junit5-mockito` artifact is only a relocation). Quarkus reuses one application across unprofiled `@QuarkusTest` classes, and a mock would leak into the happy-path tests.
- Saturation tests must block inside `RequestProcessor` (virtual thread), not on the event loop. Give the client a timeout so a stuck loop fails the test.
- Assert permit release after both success and failure (`inflight == 0`).

## Changes that look small and are not

- Removing `@RunOnVirtualThread` makes a blocking processor stall every event loop.
- Raising the body limit without a heap note makes the million-request cap unsafe.
- Catching exceptions from `RequestProcessor` without `release()` leaks permits until restart.
- Enabling a platform worker pool and dispatching the hot path there reintroduces the thread-cap this service exists to avoid.
