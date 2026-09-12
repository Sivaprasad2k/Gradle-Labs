package com.siva.jobscheduler.cli;

import com.siva.jobscheduler.api.RestApiServer;
import com.siva.jobscheduler.application.*;
import com.siva.jobscheduler.execution.JobExecutor;
import com.siva.jobscheduler.scheduler.JobScheduler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.time.Clock;

import static org.junit.jupiter.api.Assertions.*;

class JobSchedulerCliTest {

    private RestApiServer server;
    private int port;
    private JobSchedulerCli cli;
    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;

    @BeforeEach
    void setUp() throws Exception {
        Clock clock = Clock.systemUTC();
        JobExecutor executor = new JobExecutor(2, clock);
        JobScheduler scheduler = new JobScheduler(clock, executor);

        JobApplicationService jobService = new JobApplicationService(scheduler, null, null);
        ExecutionApplicationService execService = new ExecutionApplicationService(scheduler, null);
        WorkflowApplicationService wfService = new WorkflowApplicationService(scheduler, null, null);
        SchedulerApplicationService schedulerService = new SchedulerApplicationService(scheduler, executor, null, null);

        port = 8092;
        server = new RestApiServer(port, jobService, execService, wfService, schedulerService);
        server.start();

        cli = new JobSchedulerCli("http://localhost:" + port);
        System.setOut(new PrintStream(outContent));
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void testCliSchedulerStatus() {
        int exitCode = cli.execute(new String[]{"scheduler", "status"});
        assertEquals(0, exitCode);
        String output = outContent.toString();
        assertTrue(output.contains("Scheduler Status Summary"));
        assertTrue(output.contains("RUNNING"));
    }

    @Test
    void testCliJobList() {
        int exitCode = cli.execute(new String[]{"scheduler", "job", "list"});
        assertEquals(0, exitCode);
        String output = outContent.toString();
        assertTrue(output.contains("Registered Jobs"));
    }

    @Test
    void testCliHaltAndResume() {
        int haltCode = cli.execute(new String[]{"halt"});
        assertEquals(0, haltCode);
        String haltOutput = outContent.toString();
        assertTrue(haltOutput.contains("Scheduler Halt"));

        outContent.reset();
        int resumeCode = cli.execute(new String[]{"resume"});
        assertEquals(0, resumeCode);
        String resumeOutput = outContent.toString();
        assertTrue(resumeOutput.contains("Scheduler Resume"));
    }
}
