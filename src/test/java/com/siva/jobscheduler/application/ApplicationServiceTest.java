package com.siva.jobscheduler.application;

import com.siva.jobscheduler.domain.*;
import com.siva.jobscheduler.dto.*;
import com.siva.jobscheduler.execution.JobExecutor;
import com.siva.jobscheduler.scheduler.JobScheduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ApplicationServiceTest {

    private JobScheduler scheduler;
    private JobExecutor executor;
    private JobApplicationService jobService;
    private ExecutionApplicationService execService;
    private WorkflowApplicationService wfService;
    private SchedulerApplicationService schedulerService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.systemUTC();
        executor = new JobExecutor(2, clock);
        scheduler = new JobScheduler(clock, executor);

        jobService = new JobApplicationService(scheduler, null, null);
        execService = new ExecutionApplicationService(scheduler, null);
        wfService = new WorkflowApplicationService(scheduler, null, null);
        schedulerService = new SchedulerApplicationService(scheduler, executor, null, null);
    }

    @Test
    void testCreateAndListJobs() {
        CreateJobRequest req = new CreateJobRequest("test-1", "Test Job 1", Instant.now().toString(), "IN_MEMORY", Collections.emptyMap(), Collections.emptySet(), "CONTINUE", null);
        JobResponse created = jobService.createJob(req);

        assertNotNull(created);
        assertEquals("test-1", created.jobId());
        assertEquals("Test Job 1", created.jobName());

        List<JobResponse> jobs = jobService.listJobs();
        assertNotNull(jobs);
    }

    @Test
    void testGetSchedulerStatus() {
        SchedulerStatusResponse status = schedulerService.getStatus();
        assertNotNull(status);
        assertEquals("RUNNING", status.status());
        assertEquals("v8.0.0", status.version());
    }

    @Test
    void testHaltAndResumeScheduler() {
        assertTrue(schedulerService.halt());
        assertEquals("HALTED", schedulerService.getStatus().status());

        assertTrue(schedulerService.resume());
        assertEquals("RUNNING", schedulerService.getStatus().status());
    }

    @Test
    void testListExecutionsWithPagination() {
        Job job = new Job("j1", "Job 1", Instant.now(), () -> {});
        scheduler.registerJob(job);

        PageResponse<ExecutionResponse> page = execService.listExecutions(null, null, 0, 10);
        assertNotNull(page);
        assertEquals(0, page.page());
        assertEquals(10, page.size());
    }
}
