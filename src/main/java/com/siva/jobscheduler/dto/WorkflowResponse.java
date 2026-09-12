package com.siva.jobscheduler.dto;

import java.util.List;
import java.util.Set;

public record WorkflowResponse(
        String rootJobId,
        List<WorkflowNodeDto> nodes,
        int totalNodes,
        int blockedNodes,
        int failedNodes
) {
    public record WorkflowNodeDto(
            String jobId,
            String jobName,
            String status,
            Set<String> dependencyIds,
            Set<String> dependentIds
    ) {}
}
