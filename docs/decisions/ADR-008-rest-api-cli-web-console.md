# ADR-008: REST API, CLI & Web Console Administration Layer

## Status
Accepted

## Context
Version 8 of the Java Job Scheduler project requires an external administration layer consisting of:
1. Application Service Layer insulating domain model and core scheduler engine from external transport protocols.
2. REST API for remote administrative monitoring and management.
3. CLI Administration Tool.
4. Web Administration Console.

The architectural constraints dictate:
- No modification of V1-V7 business semantics.
- Web Console and CLI MUST communicate exclusively through REST.
- Web Console and CLI MUST NEVER access MongoDB directly.
- The REST layer MUST NOT implement scheduler business logic.
- Avoid introducing heavyweight frameworks like Spring Boot or unnecessary dependencies.

## Decision

### 1. Application Service Layer
Create `JobApplicationService`, `ExecutionApplicationService`, `WorkflowApplicationService`, and `SchedulerApplicationService` under `com.siva.jobscheduler.application`. These services coordinate V1-V7 domain objects and repositories without duplicating core scheduling, dependency graph evaluation, recurrence calculation, or execution dispatching.

### 2. HTTP Framework Selection
Select JDK standard `com.sun.net.httpserver.HttpServer`.
- **Rationale**: Included natively in Java 17, zero external footprint, high performance, handles REST endpoints and static Web Console resource serving cleanly without framework overhead.

### 3. REST Contracts & Error Handling
Standardize API base path under `/api/v1/*` with REST semantics (`GET`, `POST`). Standardize JSON response schemas and DTOs (`JobResponse`, `ExecutionResponse`, `WorkflowResponse`, `SchedulerStatusResponse`, `PageResponse`, `ErrorResponse`). Expose consistent JSON error responses with HTTP 400, 404, 405, and 500 status codes without exposing internal Java stack traces.

### 4. CLI Administration Tool
Implement `JobSchedulerCli` using JDK `HttpClient` to communicate with the REST API. Provide clear command grammar (`scheduler status`, `scheduler job list`, `scheduler execution list`, `scheduler workflow get`, `halt`, `resume`, `shutdown`) and support `--json` output for automated scripting.

### 5. Web Console Architecture
Implement a single-page web console (`index.html`, `style.css`, `app.js`) using standard Vanilla HTML5, CSS3, and JavaScript. Design adheres strictly to the canonical reference dark navy/charcoal engineering console visual theme. The browser communicates exclusively via REST API endpoints (`/api/v1/*`).

## Consequences

### Positive
- Zero framework overhead and minimal external dependencies.
- Strict isolation of administration, transport, and scheduling engine layers.
- Seamless CLI and Web Console management over unified REST contracts.
- Preserves 100% backward compatibility with V1-V7 tests and semantics.

### Negative
- Manual JSON serialization utility required in place of heavy reflection-based JSON mappers.
- JDK `HttpServer` requires custom route handler registration.
