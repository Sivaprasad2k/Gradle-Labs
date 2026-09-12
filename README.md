# Java Job Scheduler (V8)

## Project Overview
A lightweight, durable Java 17 job scheduler designed to demonstrate core engineering concepts including clean architecture, bounded concurrent execution using `ExecutorService`, explicit thread-safe job lifecycle management, controlled failure recovery with exponential backoff retries, job dependency DAG workflow control, recurring fixed-rate scheduling with coalescing, durable MongoDB persistence, restart recovery, REST API, CLI administration tool, Web Administration Console, time-dependent unit testing, and fundamental scheduling algorithms without relying on heavy enterprise frameworks.

## V8 Scope & Architectural Evolution
Version 8 (V8) introduces the **External Administration Layer**:
1. **Application Service Layer** (`com.siva.jobscheduler.application`): Clean service boundary separating domain logic from external interfaces.
2. **REST API** (`com.siva.jobscheduler.api`): Hosted on JDK `HttpServer` under `/api/v1/...`.
3. **CLI Administration Tool** (`com.siva.jobscheduler.cli.JobSchedulerCli`): Terminal interface operating over HTTP REST endpoints.
4. **Web Administration Console** (`src/main/resources/web`): Modern dark navy/charcoal operator console served directly at `http://localhost:8080/`.

## Architecture & Core Components
```text
com.siva.jobscheduler
├── application
│   ├── JobApplicationService.java
│   ├── ExecutionApplicationService.java
│   ├── WorkflowApplicationService.java
│   └── SchedulerApplicationService.java
├── dto
│   ├── CreateJobRequest.java
│   ├── JobResponse.java
│   ├── ExecutionResponse.java
│   ├── AttemptResponse.java
│   ├── WorkflowResponse.java
│   ├── SchedulerStatusResponse.java
│   ├── PageResponse.java
│   └── ErrorResponse.java
├── api
│   ├── json
│   │   └── JsonUtils.java
│   └── RestApiServer.java
├── cli
│   └── JobSchedulerCli.java
├── domain
├── task
├── persistence
├── recurrence
├── dependency
├── scheduler
├── execution
└── JobSchedulerApplication.java
```

## Technology Stack
- **Language**: Java 17
- **Build Tool**: Gradle 9.7.1
- **HTTP Server**: JDK `com.sun.net.httpserver.HttpServer`
- **Database**: MongoDB Sync Driver 5.1.0
- **Testing**: JUnit 5 (Jupiter)

## Build and Run Instructions

**To clean and run the test suite:**
```bash
./gradlew clean test
```

**To build the project:**
```bash
./gradlew build
```

**To execute the demonstration application (starts Web Console on http://localhost:8080):**
```bash
./gradlew run --console=plain
```

**To run CLI commands against a running scheduler:**
```bash
java -cp build/classes/java/main com.siva.jobscheduler.cli.JobSchedulerCli scheduler status
java -cp build/classes/java/main com.siva.jobscheduler.cli.JobSchedulerCli job list
```

## Architecture Documentation
Detailed decision records and version documentation are located in `/docs`:
- `docs/architecture/architecture.md`
- `docs/versions/v1.md` ... `docs/versions/v8.md`
- `docs/decisions/ADR-001-priority-queue.md` ... `docs/decisions/ADR-008-rest-api-cli-web-console.md`

## Version Roadmap
- **V1–V7**: Sequential queue, concurrency, lifecycle state machine, retries, DAG workflow control, fixed-rate recurrence, durable persistence, restart recovery.
- **V8 (Current)**: REST API, CLI administration tool, and Web Administration Console.
- **Future Versions**: May explore observability and metrics (V9).
