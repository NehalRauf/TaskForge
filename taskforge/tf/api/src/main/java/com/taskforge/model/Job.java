package com.taskforge.model;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "jobs")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Job {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 64)
    private String type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private QueuePriority queuePriority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private JobStatus status = JobStatus.QUEUED;

    @Column(columnDefinition = "TEXT")
    private String payload;

    @Column(length = 64)
    private String assignedWorker;

    @Builder.Default @Column(nullable = false)
    private int retryCount = 0;

    @Column(nullable = false) @Builder.Default
    private int maxRetries = 3;

    @Column(columnDefinition = "TEXT")
    private String failureReason;

    @Column private Instant startedAt;
    @Column private Instant completedAt;
    @Column private Long durationMs;

    @CreationTimestamp @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp @Column(nullable = false)
    private Instant updatedAt;

    public enum JobStatus { QUEUED, RUNNING, COMPLETED, FAILED, DEAD_LETTER }
    public enum QueuePriority { HIGH, NORMAL, LOW }
}
