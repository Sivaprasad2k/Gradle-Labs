package com.siva.jobscheduler.api;

import com.siva.jobscheduler.application.*;
import com.siva.jobscheduler.execution.JobExecutor;
import com.siva.jobscheduler.scheduler.JobScheduler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;

import static org.junit.jupiter.api.Assertions.*;

class RestApiServerTest {

    private RestApiServer server;
    private HttpClient client;
    private int port;

    @BeforeEach
    void setUp() throws IOException {
        Clock clock = Clock.systemUTC();
        JobExecutor executor = new JobExecutor(2, clock);
        JobScheduler scheduler = new JobScheduler(clock, executor);

        JobApplicationService jobService = new JobApplicationService(scheduler, null, null);
        ExecutionApplicationService execService = new ExecutionApplicationService(scheduler, null);
        WorkflowApplicationService wfService = new WorkflowApplicationService(scheduler, null, null);
        SchedulerApplicationService schedulerService = new SchedulerApplicationService(scheduler, executor, null, null);

        port = 8091;
        server = new RestApiServer(port, jobService, execService, wfService, schedulerService);
        server.start();

        client = HttpClient.newHttpClient();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void testGetSchedulerStatusEndpoint() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/scheduler"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("\"status\":\"RUNNING\""));
    }

    @Test
    void testHaltAndResumeViaRestApi() throws Exception {
        // Halt
        HttpRequest haltReq = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/scheduler/halt"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> haltResp = client.send(haltReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, haltResp.statusCode());
        assertTrue(haltResp.body().contains("\"halted\":true"));

        // Resume
        HttpRequest resumeReq = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/scheduler/resume"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> resumeResp = client.send(resumeReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resumeResp.statusCode());
        assertTrue(resumeResp.body().contains("\"resumed\":true"));
    }

    @Test
    void testCreateJobEndpoint() throws Exception {
        String body = """
                {
                  "jobId": "api-job-1",
                  "jobName": "API Registered Job",
                  "taskType": "IN_MEMORY",
                  "scheduledAt": %d,
                  "failurePolicy": "CONTINUE"
                }
                """.formatted(System.currentTimeMillis() + 60000);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/jobs"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, resp.statusCode());
        assertTrue(resp.body().contains("\"jobId\":\"api-job-1\""));
    }

    @Test
    void testStaticWebHandler() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("Job Scheduler Console"));
    }
}
