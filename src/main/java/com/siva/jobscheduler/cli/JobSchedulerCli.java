package com.siva.jobscheduler.cli;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class JobSchedulerCli {
    private static final String DEFAULT_BASE_URL = "http://localhost:8080";
    private final HttpClient client;
    private final String baseUrl;

    public JobSchedulerCli(String baseUrl) {
        this.baseUrl = baseUrl != null && !baseUrl.isBlank() ? baseUrl :
                System.getenv().getOrDefault("SCHEDULER_API_URL", DEFAULT_BASE_URL);
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            printHelp();
            return;
        }

        JobSchedulerCli cli = new JobSchedulerCli(null);
        int exitCode = cli.execute(args);
        System.exit(exitCode);
    }

    public int execute(String[] rawArgs) {
        if (rawArgs == null || rawArgs.length == 0) {
            printHelp();
            return 0;
        }

        boolean jsonMode = false;
        List<String> argList = new ArrayList<>();
        for (String arg : rawArgs) {
            if ("--json".equalsIgnoreCase(arg)) {
                jsonMode = true;
            } else {
                argList.add(arg);
            }
        }

        if (argList.isEmpty()) {
            printHelp();
            return 0;
        }

        // Handle optional leading "scheduler" keyword
        if (argList.size() > 1 && "scheduler".equalsIgnoreCase(argList.get(0))) {
            argList.remove(0);
        }

        String command = argList.get(0).toLowerCase();
        try {
            switch (command) {
                case "status" -> {
                    return fetchAndPrint("/api/v1/scheduler", "Scheduler Status Summary", jsonMode);
                }
                case "jobs", "job" -> {
                    if (argList.size() >= 2 && "list".equalsIgnoreCase(argList.get(1))) {
                        return fetchAndPrint("/api/v1/jobs", "Registered Jobs", jsonMode);
                    } else if (argList.size() >= 3 && "get".equalsIgnoreCase(argList.get(1))) {
                        return fetchAndPrint("/api/v1/jobs/" + argList.get(2), "Job Details: " + argList.get(2), jsonMode);
                    } else if (argList.size() >= 3 && "cancel".equalsIgnoreCase(argList.get(1))) {
                        return postAndPrint("/api/v1/jobs/" + argList.get(2) + "/cancel", "", "Cancel Job: " + argList.get(2), jsonMode);
                    } else {
                        System.err.println("Usage: scheduler job [list|get <jobId>|cancel <jobId>]");
                        return 1;
                    }
                }
                case "executions", "execution" -> {
                    if (argList.size() >= 2 && "list".equalsIgnoreCase(argList.get(1))) {
                        return fetchAndPrint("/api/v1/executions", "Execution History", jsonMode);
                    } else if (argList.size() >= 3 && "get".equalsIgnoreCase(argList.get(1))) {
                        return fetchAndPrint("/api/v1/executions/" + argList.get(2), "Execution Details: " + argList.get(2), jsonMode);
                    } else {
                        System.err.println("Usage: scheduler execution [list|get <executionId>]");
                        return 1;
                    }
                }
                case "workflow" -> {
                    if (argList.size() >= 3 && "get".equalsIgnoreCase(argList.get(1))) {
                        return fetchAndPrint("/api/v1/workflows/" + argList.get(2), "Workflow Inspection: " + argList.get(2), jsonMode);
                    } else {
                        System.err.println("Usage: scheduler workflow get <jobId>");
                        return 1;
                    }
                }
                case "halt" -> {
                    return postAndPrint("/api/v1/scheduler/halt", "", "Scheduler Halt", jsonMode);
                }
                case "resume" -> {
                    return postAndPrint("/api/v1/scheduler/resume", "", "Scheduler Resume", jsonMode);
                }
                case "shutdown" -> {
                    return postAndPrint("/api/v1/scheduler/shutdown", "", "Scheduler Shutdown", jsonMode);
                }
                case "help" -> {
                    printHelp();
                    return 0;
                }
                default -> {
                    System.err.println("Unknown command: " + command);
                    printHelp();
                    return 1;
                }
            }
        } catch (Exception e) {
            System.err.printf("Error communicating with REST API (%s): %s%n", baseUrl, e.getMessage());
            return 2;
        }
    }

    private int fetchAndPrint(String endpoint, String title, boolean jsonMode) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + endpoint))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        printResult(title, resp.statusCode(), resp.body(), jsonMode);
        return resp.statusCode() == 200 ? 0 : 1;
    }

    private int postAndPrint(String endpoint, String requestBody, String title, boolean jsonMode) throws Exception {
        HttpRequest.BodyPublisher bodyPublisher = requestBody == null || requestBody.isBlank() ?
                HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(requestBody);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + endpoint))
                .header("Content-Type", "application/json")
                .POST(bodyPublisher)
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        printResult(title, resp.statusCode(), resp.body(), jsonMode);
        return resp.statusCode() == 200 || resp.statusCode() == 201 ? 0 : 1;
    }

    private static void printResult(String title, int statusCode, String body, boolean jsonMode) {
        if (jsonMode) {
            System.out.println(body);
            return;
        }

        System.out.println("==================================================");
        System.out.printf(" %s (HTTP %d)%n", title, statusCode);
        System.out.println("==================================================");
        System.out.println(body);
        System.out.println("==================================================");
    }

    private static void printHelp() {
        System.out.println("""
            Job Scheduler CLI Administration Tool (V8)
            Usage:
              scheduler status                   Display scheduler operational status
              scheduler job list                 List registered jobs
              scheduler job get <jobId>          Get job details by ID
              scheduler job cancel <jobId>       Cancel active execution of a job
              scheduler execution list           List execution history
              scheduler execution get <execId>   Get execution details by ID
              scheduler workflow get <jobId>      Inspect DAG workflow for job
              scheduler halt                     Halt job dispatching
              scheduler resume                   Resume HALTED scheduler
              scheduler shutdown                 Shutdown scheduler process
              
            Options:
              --json                             Output raw JSON response
            """);
    }
}
