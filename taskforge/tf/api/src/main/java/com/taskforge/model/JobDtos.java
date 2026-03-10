package com.taskforge.model;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class JobDtos {

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class CreateJobRequest {
        @NotBlank(message = "Job type is required")
        private String type;
        @NotNull(message = "Queue priority is required")
        private Job.QueuePriority queuePriority;
        private String payload;
        private Integer maxRetries = 3;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class JobResponse {
        private UUID id;
        private String type;
        private Job.QueuePriority queuePriority;
        private Job.JobStatus status;
        private String payload;
        private String assignedWorker;
        private int retryCount;
        private int maxRetries;
        private String failureReason;
        private Instant startedAt;
        private Instant completedAt;
        private Long durationMs;
        private Instant createdAt;
        private Instant updatedAt;

        public static JobResponse from(Job j) {
            return JobResponse.builder()
                .id(j.getId()).type(j.getType()).queuePriority(j.getQueuePriority())
                .status(j.getStatus()).payload(j.getPayload()).assignedWorker(j.getAssignedWorker())
                .retryCount(j.getRetryCount()).maxRetries(j.getMaxRetries())
                .failureReason(j.getFailureReason()).startedAt(j.getStartedAt())
                .completedAt(j.getCompletedAt()).durationMs(j.getDurationMs())
                .createdAt(j.getCreatedAt()).updatedAt(j.getUpdatedAt()).build();
        }
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class JobListResponse {
        private List<JobResponse> jobs;
        private long total;
        private int page;
        private int size;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class JobEvent {
        private String eventType;
        private JobResponse job;
        private Instant timestamp;
    }
}
