package com.siva.jobscheduler.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.siva.jobscheduler.api.json.JsonUtils;
import com.siva.jobscheduler.application.*;
import com.siva.jobscheduler.dto.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class RestApiServer {
    private final HttpServer server;
    private final int port;
    private final JobApplicationService jobService;
    private final ExecutionApplicationService execService;
    private final WorkflowApplicationService workflowService;
    private final SchedulerApplicationService schedulerService;

    public RestApiServer(int port, JobApplicationService jobService, ExecutionApplicationService execService,
                         WorkflowApplicationService workflowService, SchedulerApplicationService schedulerService) throws IOException {
        this.port = port;
        this.jobService = Objects.requireNonNull(jobService, "JobApplicationService cannot be null");
        this.execService = Objects.requireNonNull(execService, "ExecutionApplicationService cannot be null");
        this.workflowService = Objects.requireNonNull(workflowService, "WorkflowApplicationService cannot be null");
        this.schedulerService = Objects.requireNonNull(schedulerService, "SchedulerApplicationService cannot be null");

        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        configureRoutes();
    }

    private void configureRoutes() {
        server.createContext("/api/v1/jobs", new JobsHandler());
        server.createContext("/api/v1/executions", new ExecutionsHandler());
        server.createContext("/api/v1/workflows", new WorkflowsHandler());
        server.createContext("/api/v1/scheduler", new SchedulerHandler());
        server.createContext("/", new StaticWebHandler());
    }

    public void start() {
        server.start();
        System.out.printf("[%s] V8 REST API & Web Console Server started on port %d%n", java.time.Instant.now(), port);
    }

    public void stop() {
        server.stop(0);
        System.out.printf("[%s] V8 REST API Server stopped%n", java.time.Instant.now());
    }

    public int getPort() {
        return port;
    }

    private class JobsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 204, "");
                return;
            }

            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();

            try {
                if ("/api/v1/jobs".equals(path) || "/api/v1/jobs/".equals(path)) {
                    if ("GET".equalsIgnoreCase(method)) {
                        List<JobResponse> jobs = jobService.listJobs();
                        sendResponse(exchange, 200, JsonUtils.toJson(jobs));
                    } else if ("POST".equalsIgnoreCase(method)) {
                        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                        CreateJobRequest req = JsonUtils.parseCreateJobRequest(body);
                        JobResponse created = jobService.createJob(req);
                        sendResponse(exchange, 201, JsonUtils.toJson(created));
                    } else {
                        sendError(exchange, 405, "METHOD_NOT_ALLOWED", "Method not allowed");
                    }
                } else if (path.startsWith("/api/v1/jobs/")) {
                    String subPath = path.substring("/api/v1/jobs/".length());
                    if (subPath.endsWith("/cancel")) {
                        String jobId = subPath.substring(0, subPath.length() - "/cancel".length());
                        boolean cancelled = jobService.cancelJobExecution(jobId);
                        sendResponse(exchange, 200, "{\"success\":" + cancelled + "}");
                    } else if (subPath.endsWith("/cancel-recurrence")) {
                        String jobId = subPath.substring(0, subPath.length() - "/cancel-recurrence".length());
                        boolean cancelled = jobService.cancelJobRecurrence(jobId);
                        sendResponse(exchange, 200, "{\"success\":" + cancelled + "}");
                    } else {
                        Optional<JobResponse> jobOpt = jobService.getJob(subPath);
                        if (jobOpt.isPresent()) {
                            sendResponse(exchange, 200, JsonUtils.toJson(jobOpt.get()));
                        } else {
                            sendError(exchange, 404, "JOB_NOT_FOUND", "Job '" + subPath + "' not found");
                        }
                    }
                }
            } catch (IllegalArgumentException e) {
                sendError(exchange, 400, "BAD_REQUEST", e.getMessage());
            } catch (Exception e) {
                sendError(exchange, 500, "INTERNAL_SERVER_ERROR", e.getMessage());
            }
        }
    }

    private class ExecutionsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 204, "");
                return;
            }

            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();

            if (!"GET".equalsIgnoreCase(method)) {
                sendError(exchange, 405, "METHOD_NOT_ALLOWED", "Method not allowed");
                return;
            }

            try {
                if ("/api/v1/executions".equals(path) || "/api/v1/executions/".equals(path)) {
                    Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
                    int page = Integer.parseInt(params.getOrDefault("page", "0"));
                    int size = Integer.parseInt(params.getOrDefault("size", "20"));
                    String status = params.get("status");
                    String jobId = params.get("jobId");

                    PageResponse<ExecutionResponse> result = execService.listExecutions(status, jobId, page, size);
                    sendResponse(exchange, 200, JsonUtils.toJson(result));
                } else if (path.startsWith("/api/v1/executions/")) {
                    String execId = path.substring("/api/v1/executions/".length());
                    Optional<ExecutionResponse> execOpt = execService.getExecution(execId);
                    if (execOpt.isPresent()) {
                        sendResponse(exchange, 200, JsonUtils.toJson(execOpt.get()));
                    } else {
                        sendError(exchange, 404, "EXECUTION_NOT_FOUND", "Execution '" + execId + "' not found");
                    }
                }
            } catch (Exception e) {
                sendError(exchange, 500, "INTERNAL_SERVER_ERROR", e.getMessage());
            }
        }
    }

    private class WorkflowsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 204, "");
                return;
            }

            String path = exchange.getRequestURI().getPath();
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendError(exchange, 405, "METHOD_NOT_ALLOWED", "Method not allowed");
                return;
            }

            if (path.startsWith("/api/v1/workflows/")) {
                String jobId = path.substring("/api/v1/workflows/".length());
                Optional<WorkflowResponse> wfOpt = workflowService.getWorkflow(jobId);
                if (wfOpt.isPresent()) {
                    sendResponse(exchange, 200, JsonUtils.toJson(wfOpt.get()));
                } else {
                    sendError(exchange, 404, "WORKFLOW_NOT_FOUND", "Workflow for jobId '" + jobId + "' not found");
                }
            } else {
                sendError(exchange, 400, "BAD_REQUEST", "Job ID required for workflow inspection");
            }
        }
    }

    private class SchedulerHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 204, "");
                return;
            }

            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();

            if ("/api/v1/scheduler".equals(path) || "/api/v1/scheduler/".equals(path)) {
                if ("GET".equalsIgnoreCase(method)) {
                    SchedulerStatusResponse status = schedulerService.getStatus();
                    sendResponse(exchange, 200, JsonUtils.toJson(status));
                } else {
                    sendError(exchange, 405, "METHOD_NOT_ALLOWED", "Method not allowed");
                }
            } else if ("/api/v1/scheduler/resume".equals(path)) {
                if ("POST".equalsIgnoreCase(method)) {
                    boolean resumed = schedulerService.resume();
                    sendResponse(exchange, 200, "{\"resumed\":" + resumed + "}");
                } else {
                    sendError(exchange, 405, "METHOD_NOT_ALLOWED", "Method not allowed");
                }
            } else if ("/api/v1/scheduler/halt".equals(path)) {
                if ("POST".equalsIgnoreCase(method)) {
                    boolean halted = schedulerService.halt();
                    sendResponse(exchange, 200, "{\"halted\":" + halted + "}");
                } else {
                    sendError(exchange, 405, "METHOD_NOT_ALLOWED", "Method not allowed");
                }
            } else if ("/api/v1/scheduler/shutdown".equals(path)) {
                if ("POST".equalsIgnoreCase(method)) {
                    boolean shutdown = schedulerService.shutdown();
                    sendResponse(exchange, 200, "{\"shutdown\":" + shutdown + "}");
                } else {
                    sendError(exchange, 405, "METHOD_NOT_ALLOWED", "Method not allowed");
                }
            } else {
                sendError(exchange, 404, "NOT_FOUND", "Endpoint not found");
            }
        }
    }

    private class StaticWebHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if ("/".equals(path) || "/console".equals(path) || "/console/".equals(path)) {
                path = "/web/index.html";
            } else if (path.startsWith("/console/")) {
                path = "/web/" + path.substring("/console/".length());
            } else if (!path.startsWith("/web/")) {
                path = "/web" + path;
            }

            InputStream is = getClass().getResourceAsStream(path);
            if (is == null) {
                is = getClass().getResourceAsStream("/web/index.html");
            }

            if (is != null) {
                byte[] bytes = is.readAllBytes();
                String contentType = getContentType(path);
                exchange.getResponseHeaders().set("Content-Type", contentType);
                sendResponse(exchange, 200, bytes);
            } else {
                sendError(exchange, 404, "NOT_FOUND", "Web asset not found");
            }
        }

        private String getContentType(String path) {
            if (path.endsWith(".html")) return "text/html; charset=UTF-8";
            if (path.endsWith(".css")) return "text/css; charset=UTF-8";
            if (path.endsWith(".js")) return "application/javascript; charset=UTF-8";
            if (path.endsWith(".png")) return "image/png";
            if (path.endsWith(".svg")) return "image/svg+xml";
            return "text/plain";
        }
    }

    private void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String responseJson) throws IOException {
        byte[] bytes = responseJson.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        sendResponse(exchange, statusCode, bytes);
    }

    private void sendResponse(HttpExchange exchange, int statusCode, byte[] bytes) throws IOException {
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendError(HttpExchange exchange, int statusCode, String code, String message) throws IOException {
        ErrorResponse err = ErrorResponse.of(code, message, exchange.getRequestURI().getPath());
        sendResponse(exchange, statusCode, JsonUtils.toJson(err));
    }

    private Map<String, String> parseQueryParams(String query) {
        if (query == null || query.isBlank()) return Collections.emptyMap();
        Map<String, String> map = new HashMap<>();
        for (String param : query.split("&")) {
            String[] pair = param.split("=");
            if (pair.length == 2) {
                map.put(pair[0], pair[1]);
            }
        }
        return map;
    }
}
