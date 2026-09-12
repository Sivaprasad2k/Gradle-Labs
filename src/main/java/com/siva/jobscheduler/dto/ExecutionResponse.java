package com.siva.jobscheduler.dto;

import com.siva.jobscheduler.domain.JobExecution;

import java.util.List;

public record ExecutionResponse(
        String executionId,
        String jobId,
        String jobName,
        int occurrenceNumber,
        String status,
        String scheduledAt,
        String startedAt,
        String completedAt,
        String failureReason,
        int attemptCount,
        Long durationMillis,
        List<AttemptResponse> attempts
) {
    public static ExecutionResponse fromDomain(JobExecution execution) {
        List<AttemptResponse> attemptDtos = execution.getAttempts().stream()
                .map(AttemptResponse::fromDomain)
                .toList();

        return new ExecutionResponse(
                execution.getExecutionId(),
                execution.getJob().id(),
                execution.getJob().name(),
                execution.getOccurrenceNumber(),
                execution.getStatus().name(),
                execution.getJob().scheduledAt().toString(),
                execution.getStartedAt() != null ? execution.getStartedAt().toString() : null,
                execution.getCompletedAt() != null ? execution.getCompletedAt().toString() : null,
                execution.getFailure() != null ? execution.getFailure().getMessage() : null,
                execution.getAttemptCount(),
                execution.getDuration() != null ? execution.getDuration().toMillis() : null,
                attemptDtos
        );
    }
}
