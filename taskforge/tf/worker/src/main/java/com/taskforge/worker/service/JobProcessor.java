package com.taskforge.worker.service;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

@Slf4j
@Service
public class JobProcessor {
    private final Random random = new Random();

    public void process(UUID jobId, String jobType, Map<?, ?> jobRef) throws JobProcessingException {
        switch (jobType.toLowerCase()) {
            case "email"     -> run(jobId, "email",     200,  800, 0.05, "SMTP connection refused");
            case "video"     -> run(jobId, "video",    3000, 8000, 0.10, "FFmpeg codec error");
            case "report"    -> run(jobId, "report",    500, 2000, 0.03, "Template not found");
            case "thumbnail" -> run(jobId, "thumbnail", 300, 1200, 0.08, "Image decode error");
            case "export"    -> run(jobId, "export",   1000, 4000, 0.06, "S3 upload failed");
            case "notify"    -> run(jobId, "notify",    100,  500, 0.02, "FCM token expired");
            case "cleanup"   -> run(jobId, "cleanup",   500, 1500, 0.01, "Permission denied");
            default          -> run(jobId, jobType,     200, 1000, 0.05, "Processing error");
        }
    }

    private void run(UUID id, String type, int minMs, int maxMs, double failRate, String failMsg)
            throws JobProcessingException {
        log.info("Processing {} job {}", type, id);
        try {
            Thread.sleep(minMs + (long)(random.nextDouble() * (maxMs - minMs)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new JobProcessingException("Interrupted");
        }
        if (random.nextDouble() < failRate) throw new JobProcessingException(failMsg);
        log.info("Completed {} job {}", type, id);
    }

    public static class JobProcessingException extends Exception {
        public JobProcessingException(String msg) { super(msg); }
    }
}
