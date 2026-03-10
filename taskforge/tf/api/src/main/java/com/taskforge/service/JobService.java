package com.taskforge.service;
import com.taskforge.model.Job;
import com.taskforge.model.JobDtos;
import com.taskforge.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobService {
    private final JobRepository jobRepository;
    private final RedisQueueService redisQueueService;
    private final WebSocketNotificationService wsService;

    @Transactional
    public JobDtos.JobResponse createJob(JobDtos.CreateJobRequest req) {
        Job job = Job.builder()
            .type(req.getType())
            .queuePriority(req.getQueuePriority())
            .payload(req.getPayload())
            .maxRetries(req.getMaxRetries() != null ? req.getMaxRetries() : 3)
            .build();
        job = jobRepository.save(job);
        redisQueueService.enqueue(job);
        JobDtos.JobResponse resp = JobDtos.JobResponse.from(job);
        wsService.broadcast(JobDtos.JobEvent.builder().eventType("JOB_CREATED").job(resp).timestamp(Instant.now()).build());
        return resp;
    }

    @Transactional(readOnly = true)
    public JobDtos.JobResponse getJob(UUID id) {
        return jobRepository.findById(id).map(JobDtos.JobResponse::from)
            .orElseThrow(() -> new JobNotFoundException("Job not found: " + id));
    }

    @Transactional(readOnly = true)
    public JobDtos.JobListResponse listJobs(int page, int size, String status, String priority) {
        var pageable = PageRequest.of(page, size);
        Page<Job> jobPage;
        if (status != null) {
            jobPage = jobRepository.findByStatusOrderByCreatedAtDesc(Job.JobStatus.valueOf(status.toUpperCase()), pageable);
        } else if (priority != null) {
            jobPage = jobRepository.findByQueuePriorityOrderByCreatedAtDesc(Job.QueuePriority.valueOf(priority.toUpperCase()), pageable);
        } else {
            jobPage = jobRepository.findAllByOrderByCreatedAtDesc(pageable);
        }
        return new JobDtos.JobListResponse(
            jobPage.getContent().stream().map(JobDtos.JobResponse::from).toList(),
            jobPage.getTotalElements(), page, size);
    }

    @Transactional
    public JobDtos.JobResponse retryJob(UUID id) {
        Job job = jobRepository.findById(id).orElseThrow(() -> new JobNotFoundException("Job not found: " + id));
        if (job.getStatus() != Job.JobStatus.FAILED && job.getStatus() != Job.JobStatus.DEAD_LETTER) {
            throw new IllegalStateException("Only FAILED or DEAD_LETTER jobs can be retried");
        }
        job.setStatus(Job.JobStatus.QUEUED);
        job.setRetryCount(0);
        job.setFailureReason(null);
        job.setAssignedWorker(null);
        job.setStartedAt(null);
        job.setCompletedAt(null);
        job.setDurationMs(null);
        job = jobRepository.save(job);
        redisQueueService.enqueue(job);
        JobDtos.JobResponse resp = JobDtos.JobResponse.from(job);
        wsService.broadcast(JobDtos.JobEvent.builder().eventType("JOB_RETRIED").job(resp).timestamp(Instant.now()).build());
        return resp;
    }

    @Transactional
    public void markJobStarted(UUID id, String workerId) {
        Job job = jobRepository.findById(id).orElseThrow(() -> new JobNotFoundException("Job not found: " + id));
        job.setStatus(Job.JobStatus.RUNNING);
        job.setAssignedWorker(workerId);
        job.setStartedAt(Instant.now());
        job = jobRepository.save(job);
        wsService.broadcast(JobDtos.JobEvent.builder().eventType("JOB_STARTED").job(JobDtos.JobResponse.from(job)).timestamp(Instant.now()).build());
    }

    @Transactional
    public void markJobCompleted(UUID id, String workerId) {
        Job job = jobRepository.findById(id).orElseThrow(() -> new JobNotFoundException("Job not found: " + id));
        Instant now = Instant.now();
        job.setStatus(Job.JobStatus.COMPLETED);
        job.setCompletedAt(now);
        if (job.getStartedAt() != null) job.setDurationMs(now.toEpochMilli() - job.getStartedAt().toEpochMilli());
        job = jobRepository.save(job);
        wsService.broadcast(JobDtos.JobEvent.builder().eventType("JOB_COMPLETED").job(JobDtos.JobResponse.from(job)).timestamp(Instant.now()).build());
    }

    @Transactional
    public void markJobFailed(UUID id, String workerId, String reason) {
        Job job = jobRepository.findById(id).orElseThrow(() -> new JobNotFoundException("Job not found: " + id));
        job.setRetryCount(job.getRetryCount() + 1);
        job.setFailureReason(reason);
        job.setAssignedWorker(workerId);
        if (job.getRetryCount() >= job.getMaxRetries()) {
            job.setStatus(Job.JobStatus.DEAD_LETTER);
            job = jobRepository.save(job);
            redisQueueService.moveToDlq(job);
            wsService.broadcast(JobDtos.JobEvent.builder().eventType("JOB_DLQ").job(JobDtos.JobResponse.from(job)).timestamp(Instant.now()).build());
        } else {
            job.setStatus(Job.JobStatus.FAILED);
            job = jobRepository.save(job);
            scheduleRetry(job);
            wsService.broadcast(JobDtos.JobEvent.builder().eventType("JOB_FAILED").job(JobDtos.JobResponse.from(job)).timestamp(Instant.now()).build());
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getStats() {
        return Map.of(
            "jobCounts", Map.of(
                "queued",    jobRepository.countByStatus(Job.JobStatus.QUEUED),
                "running",   jobRepository.countByStatus(Job.JobStatus.RUNNING),
                "completed", jobRepository.countByStatus(Job.JobStatus.COMPLETED),
                "failed",    jobRepository.countByStatus(Job.JobStatus.FAILED),
                "dlq",       jobRepository.countByStatus(Job.JobStatus.DEAD_LETTER)
            ),
            "queueDepths", redisQueueService.getQueueDepths(),
            "total", jobRepository.count()
        );
    }

    @Transactional(readOnly = true)
    public List<JobDtos.JobResponse> getDeadLetterJobs() {
        return jobRepository.findDeadLetterJobs().stream().map(JobDtos.JobResponse::from).toList();
    }

    private void scheduleRetry(Job job) {
        long delayMs = (long) (5000 * Math.pow(5, job.getRetryCount() - 1));

        new Thread(() -> {
            try {
                Thread.sleep(delayMs);
                job.setStatus(Job.JobStatus.QUEUED);
                jobRepository.save(job);
                redisQueueService.requeue(job);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    public static class JobNotFoundException extends RuntimeException {
        public JobNotFoundException(String msg) {
            super(msg);
        }
    }
    }
