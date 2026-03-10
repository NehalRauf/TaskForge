package com.taskforge.controller;
import com.taskforge.model.JobDtos;
import com.taskforge.service.JobService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class JobController {
    private final JobService jobService;

    @PostMapping
    public ResponseEntity<JobDtos.JobResponse> createJob(@Valid @RequestBody JobDtos.CreateJobRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(jobService.createJob(req));
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobDtos.JobResponse> getJob(@PathVariable UUID id) {
        return ResponseEntity.ok(jobService.getJob(id));
    }

    @GetMapping
    public ResponseEntity<JobDtos.JobListResponse> listJobs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority) {
        return ResponseEntity.ok(jobService.listJobs(page, size, status, priority));
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<JobDtos.JobResponse> retryJob(@PathVariable UUID id) {
        return ResponseEntity.ok(jobService.retryJob(id));
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(jobService.getStats());
    }

    @GetMapping("/dlq")
    public ResponseEntity<List<JobDtos.JobResponse>> getDlq() {
        return ResponseEntity.ok(jobService.getDeadLetterJobs());
    }

    // Worker callbacks
    @PostMapping("/{id}/started")
    public ResponseEntity<Void> started(@PathVariable UUID id, @RequestParam String workerId) {
        jobService.markJobStarted(id, workerId); return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/completed")
    public ResponseEntity<Void> completed(@PathVariable UUID id, @RequestParam String workerId) {
        jobService.markJobCompleted(id, workerId); return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/failed")
    public ResponseEntity<Void> failed(@PathVariable UUID id, @RequestParam String workerId,
            @RequestParam(defaultValue = "Unknown error") String reason) {
        jobService.markJobFailed(id, workerId, reason); return ResponseEntity.ok().build();
    }

    @ExceptionHandler(JobService.JobNotFoundException.class)
    public ResponseEntity<Map<String,String>> notFound(JobService.JobNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String,String>> conflict(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String,String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
    }
}
