# Architecture: Version 8

## Overview
The Job Scheduler V8 introduces the **External Administration Layer**: Application Service Layer, REST API (`/api/v1/...`), CLI Administration Tool (`JobSchedulerCli`), and Web Administration Console (`http://localhost:8080/`).

## Conceptual Architecture

```text
                    ┌──────────────────┐
                    │   Web Console    │
                    └────────┬─────────┘
                             │
                    ┌────────▼─────────┐
                    │     REST API     │
                    └────────┬─────────┘
                             │
                    ┌────────▼─────────┐
                    │ Application Layer│
                    └────────┬─────────┘
                             │
                       JobScheduler
                             │
               ┌─────────────┴─────────────┐
               │                           │
          JobExecutor                 Repositories
               │                           │
        ExecutorService                 MongoDB
```

## Package Architecture & Component Boundaries

```text
com.siva.jobscheduler/
│
├── application/
│   ├── JobApplicationService.java
│   ├── ExecutionApplicationService.java
│   ├── WorkflowApplicationService.java
│   └── SchedulerApplicationService.java
│
├── dto/
│   ├── CreateJobRequest.java
│   ├── JobResponse.java
│   ├── ExecutionResponse.java
│   ├── AttemptResponse.java
│   ├── WorkflowResponse.java
│   ├── SchedulerStatusResponse.java
│   ├── PageResponse.java
│   └── ErrorResponse.java
│
├── api/
│   ├── json/
│   │   └── JsonUtils.java
│   └── RestApiServer.java
│
├── cli/
│   └── JobSchedulerCli.java
│
├── domain/
├── task/
├── persistence/
├── recurrence/
├── dependency/
├── scheduler/
├── execution/
└── JobSchedulerApplication.java
```

### 1. Application Layer (`com.siva.jobscheduler.application`)
Clean application boundaries insulating the internal domain model and scheduling engine from external transport layers.

### 2. REST API Layer (`com.siva.jobscheduler.api`)
Hosted on JDK `com.sun.net.httpserver.HttpServer`. Serves JSON REST responses and static Web Console resources (`src/main/resources/web`).

### 3. CLI Layer (`com.siva.jobscheduler.cli`)
Command-line administration tool operating as an HTTP client over REST API endpoints.

### 4. Web Console (`src/main/resources/web`)
Operator web interface implementing a dark charcoal engineering console visual design and communicating exclusively over REST API.
