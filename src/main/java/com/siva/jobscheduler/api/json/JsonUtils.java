package com.siva.jobscheduler.api.json;

import com.siva.jobscheduler.dto.*;

import java.util.*;

public class JsonUtils {

    public static String toJson(Object obj) {
        if (obj == null) return "null";

        if (obj instanceof String s) {
            return "\"" + escapeJson(s) + "\"";
        }
        if (obj instanceof Number || obj instanceof Boolean) {
            return obj.toString();
        }
        if (obj instanceof JobResponse r) {
            return jobResponseToJson(r);
        }
        if (obj instanceof ExecutionResponse r) {
            return executionResponseToJson(r);
        }
        if (obj instanceof AttemptResponse r) {
            return attemptResponseToJson(r);
        }
        if (obj instanceof SchedulerStatusResponse r) {
            return schedulerStatusToJson(r);
        }
        if (obj instanceof WorkflowResponse r) {
            return workflowResponseToJson(r);
        }
        if (obj instanceof ErrorResponse r) {
            return String.format("{\"code\":\"%s\",\"message\":\"%s\",\"timestamp\":\"%s\",\"path\":\"%s\"}",
                    escapeJson(r.code()), escapeJson(r.message()), escapeJson(r.timestamp()), escapeJson(r.path()));
        }
        if (obj instanceof PageResponse<?> p) {
            StringBuilder sb = new StringBuilder();
            sb.append("{\"items\":[");
            for (int i = 0; i < p.items().size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(toJson(p.items().get(i)));
            }
            sb.append("],\"page\":").append(p.page())
                    .append(",\"size\":").append(p.size())
                    .append(",\"totalElements\":").append(p.totalElements())
                    .append(",\"totalPages\":").append(p.totalPages())
                    .append("}");
            return sb.toString();
        }
        if (obj instanceof List<?> list) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(toJson(list.get(i)));
            }
            sb.append("]");
            return sb.toString();
        }
        if (obj instanceof Map<?, ?> map) {
            StringBuilder sb = new StringBuilder("{");
            int i = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(escapeJson(entry.getKey().toString())).append("\":")
                        .append(toJson(entry.getValue()));
                i++;
            }
            sb.append("}");
            return sb.toString();
        }

        return "\"" + escapeJson(obj.toString()) + "\"";
    }

    private static String jobResponseToJson(JobResponse r) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"id\":\"").append(escapeJson(r.id())).append("\",");
        sb.append("\"jobId\":\"").append(escapeJson(r.jobId())).append("\",");
        sb.append("\"name\":\"").append(escapeJson(r.name())).append("\",");
        sb.append("\"jobName\":\"").append(escapeJson(r.jobName())).append("\",");
        sb.append("\"scheduledAt\":\"").append(escapeJson(r.scheduledAt())).append("\",");
        sb.append("\"taskType\":\"").append(escapeJson(r.taskType())).append("\",");
        sb.append("\"taskPayload\":").append(toJson(r.taskPayload())).append(",");
        sb.append("\"dependencyIds\":").append(toJson(new ArrayList<>(r.dependencyIds()))).append(",");
        sb.append("\"failurePolicy\":\"").append(escapeJson(r.failurePolicy())).append("\",");
        sb.append("\"isRecurring\":").append(r.isRecurring()).append(",");
        if (r.recurrence() != null) {
            sb.append("\"recurrence\":{");
            sb.append("\"type\":\"").append(escapeJson(r.recurrence().type())).append("\",");
            sb.append("\"intervalMs\":").append(r.recurrence().intervalMs()).append(",");
            sb.append("\"maxOccurrences\":").append(r.recurrence().maxOccurrences()).append(",");
            sb.append("\"endTime\":").append(r.recurrence().endTime() != null ? "\"" + escapeJson(r.recurrence().endTime()) + "\"" : "null");
            sb.append("}");
        } else {
            sb.append("\"recurrence\":null");
        }
        sb.append("}");
        return sb.toString();
    }

    private static String executionResponseToJson(ExecutionResponse r) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"executionId\":\"").append(escapeJson(r.executionId())).append("\",");
        sb.append("\"jobId\":\"").append(escapeJson(r.jobId())).append("\",");
        sb.append("\"jobName\":\"").append(escapeJson(r.jobName())).append("\",");
        sb.append("\"occurrenceNumber\":").append(r.occurrenceNumber()).append(",");
        sb.append("\"status\":\"").append(escapeJson(r.status())).append("\",");
        sb.append("\"scheduledAt\":\"").append(escapeJson(r.scheduledAt())).append("\",");
        sb.append("\"startedAt\":").append(r.startedAt() != null ? "\"" + escapeJson(r.startedAt()) + "\"" : "null").append(",");
        sb.append("\"completedAt\":").append(r.completedAt() != null ? "\"" + escapeJson(r.completedAt()) + "\"" : "null").append(",");
        sb.append("\"failureReason\":").append(r.failureReason() != null ? "\"" + escapeJson(r.failureReason()) + "\"" : "null").append(",");
        sb.append("\"attemptCount\":").append(r.attemptCount()).append(",");
        sb.append("\"durationMillis\":").append(r.durationMillis()).append(",");
        sb.append("\"attempts\":").append(toJson(r.attempts()));
        sb.append("}");
        return sb.toString();
    }

    private static String attemptResponseToJson(AttemptResponse r) {
        return String.format("{\"attemptNumber\":%d,\"startedAt\":\"%s\",\"completedAt\":%s,\"outcome\":\"%s\",\"failureError\":%s,\"durationMillis\":%s}",
                r.attemptNumber(),
                escapeJson(r.startedAt()),
                r.completedAt() != null ? "\"" + escapeJson(r.completedAt()) + "\"" : "null",
                escapeJson(r.outcome()),
                r.failureError() != null ? "\"" + escapeJson(r.failureError()) + "\"" : "null",
                r.durationMillis() != null ? r.durationMillis() : "null");
    }

    private static String schedulerStatusToJson(SchedulerStatusResponse r) {
        return String.format("{\"status\":\"%s\",\"totalJobs\":%d,\"activeExecutions\":%d,\"runningExecutions\":%d,\"scheduledExecutions\":%d,\"blockedExecutions\":%d,\"failedExecutions\":%d,\"completedExecutions\":%d,\"activeWorkerThreads\":%d,\"totalWorkerThreads\":%d,\"pendingQueueSize\":%d,\"dbStatus\":\"%s\",\"dbName\":\"%s\",\"version\":\"%s\",\"environment\":\"%s\",\"uptime\":\"%s\"}",
                escapeJson(r.status()), r.totalJobs(), r.activeExecutions(), r.runningExecutions(), r.scheduledExecutions(), r.blockedExecutions(), r.failedExecutions(), r.completedExecutions(),
                r.activeWorkerThreads(), r.totalWorkerThreads(), r.pendingQueueSize(), escapeJson(r.dbStatus()), escapeJson(r.dbName()), escapeJson(r.version()), escapeJson(r.environment()), escapeJson(r.uptime()));
    }

    private static String workflowResponseToJson(WorkflowResponse r) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"rootJobId\":\"").append(escapeJson(r.rootJobId())).append("\",");
        sb.append("\"totalNodes\":").append(r.totalNodes()).append(",");
        sb.append("\"blockedNodes\":").append(r.blockedNodes()).append(",");
        sb.append("\"failedNodes\":").append(r.failedNodes()).append(",");
        sb.append("\"nodes\":[");
        for (int i = 0; i < r.nodes().size(); i++) {
            if (i > 0) sb.append(",");
            WorkflowResponse.WorkflowNodeDto node = r.nodes().get(i);
            sb.append("{\"jobId\":\"").append(escapeJson(node.jobId())).append("\",")
                    .append("\"jobName\":\"").append(escapeJson(node.jobName())).append("\",")
                    .append("\"status\":\"").append(escapeJson(node.status())).append("\",")
                    .append("\"dependencyIds\":").append(toJson(new ArrayList<>(node.dependencyIds()))).append(",")
                    .append("\"dependentIds\":").append(toJson(new ArrayList<>(node.dependentIds()))).append("}");
        }
        sb.append("]}");
        return sb.toString();
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    public static CreateJobRequest parseCreateJobRequest(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("Request body cannot be empty");
        }

        String id = extractJsonString(json, "id");
        if (id == null) {
            id = extractJsonString(json, "jobId");
        }
        String name = extractJsonString(json, "name");
        if (name == null) {
            name = extractJsonString(json, "jobName");
        }
        if (name == null) {
            name = id;
        }

        String scheduledAt = extractJsonString(json, "scheduledAt");
        String taskType = extractJsonString(json, "taskType");
        String failurePolicy = extractJsonString(json, "failurePolicy");
        Set<String> dependencyIds = extractJsonStringArray(json, "dependencyIds");

        CreateJobRequest.RecurrenceDto recurrence = null;
        if (json.contains("\"recurrence\"") && !json.contains("\"recurrence\":null")) {
            String recType = extractJsonString(json, "type");
            Long intervalMs = extractJsonLong(json, "intervalMs");
            Integer maxOccurrences = extractJsonInt(json, "maxOccurrences");
            String endTime = extractJsonString(json, "endTime");
            if (intervalMs != null && intervalMs > 0) {
                recurrence = new CreateJobRequest.RecurrenceDto(
                        recType != null ? recType : "FIXED_RATE",
                        intervalMs,
                        maxOccurrences,
                        endTime
                );
            }
        }

        return new CreateJobRequest(id, name, scheduledAt, taskType, Collections.emptyMap(), dependencyIds, failurePolicy, recurrence);
    }

    private static String extractJsonString(String json, String key) {
        String pattern = "\"" + key + "\":\"";
        int start = json.indexOf(pattern);
        if (start == -1) {
            pattern = "\"" + key + "\": \"";
            start = json.indexOf(pattern);
        }
        if (start == -1) return null;
        start += pattern.length();
        int end = json.indexOf("\"", start);
        if (end == -1) return null;
        return json.substring(start, end);
    }

    private static Long extractJsonLong(String json, String key) {
        String pattern = "\"" + key + "\":";
        int start = json.indexOf(pattern);
        if (start == -1) {
            pattern = "\"" + key + "\": ";
            start = json.indexOf(pattern);
        }
        if (start == -1) return null;
        start += pattern.length();
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
            end++;
        }
        if (end == start) return null;
        try {
            return Long.parseLong(json.substring(start, end).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer extractJsonInt(String json, String key) {
        Long val = extractJsonLong(json, key);
        return val != null ? val.intValue() : null;
    }

    private static Set<String> extractJsonStringArray(String json, String key) {
        String pattern = "\"" + key + "\":";
        int start = json.indexOf(pattern);
        if (start == -1) return Collections.emptySet();
        int arrayStart = json.indexOf("[", start);
        int arrayEnd = json.indexOf("]", arrayStart);
        if (arrayStart == -1 || arrayEnd == -1) return Collections.emptySet();

        String content = json.substring(arrayStart + 1, arrayEnd).trim();
        if (content.isEmpty()) return Collections.emptySet();

        Set<String> set = new HashSet<>();
        for (String part : content.split(",")) {
            String item = part.trim().replaceAll("^\"|\"$", "");
            if (!item.isEmpty()) {
                set.add(item);
            }
        }
        return set;
    }
}
