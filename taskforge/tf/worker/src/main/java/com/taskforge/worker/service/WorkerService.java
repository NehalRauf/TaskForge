package com.taskforge.worker.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkerService {
    private final RedisTemplate<String, String> redisTemplate;
    private final JobProcessor jobProcessor;
    private final ApiCallbackService apiCallbackService;
    private final ObjectMapper objectMapper;

    @Value("${taskforge.worker.id}") private String workerId;
    @Value("${taskforge.worker.concurrency:2}") private int concurrency;
    @Value("${taskforge.worker.poll-timeout-seconds:5}") private int pollTimeout;
    @Value("${taskforge.queue.high}") private String highQueue;
    @Value("${taskforge.queue.normal}") private String normalQueue;
    @Value("${taskforge.queue.low}") private String lowQueue;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private ExecutorService executorService;

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        running.set(true);
        executorService = Executors.newFixedThreadPool(concurrency);
        log.info("Worker {} starting {} threads", workerId, concurrency);
        for (int i = 0; i < concurrency; i++) {
            final String threadName = workerId + "-t" + i;
            executorService.submit(() -> consumeLoop(threadName));
        }
    }

    private void consumeLoop(String threadName) {
    log.info("Consumer thread {} started", threadName);

    while (running.get()) {
        try {
            String jobJson = redisTemplate.opsForList()
                    .leftPop(highQueue, Duration.ofSeconds(1));

            if (jobJson == null) {
                jobJson = redisTemplate.opsForList()
                        .leftPop(normalQueue, Duration.ofSeconds(1));
            }

            if (jobJson == null) {
                jobJson = redisTemplate.opsForList()
                        .leftPop(lowQueue, Duration.ofSeconds(1));
            }

            if (jobJson == null) {
                continue;
            }

            processJob(jobJson, threadName);

        } catch (Exception e) {
            log.error("Consumer {} error: {}", threadName, e.getMessage());

            try {
                Thread.sleep(1000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    log.info("Consumer thread {} stopped", threadName);
}

    private void processJob(String jobJson, String threadName) {
        UUID jobId = null;
        try {
            Map<?, ?> ref = objectMapper.readValue(jobJson, Map.class);
            jobId = UUID.fromString((String) ref.get("id"));
            String jobType = (String) ref.get("type");
            log.info("[{}] Processing job {} type={}", threadName, jobId, jobType);
            apiCallbackService.notifyStarted(jobId, workerId);
            jobProcessor.process(jobId, jobType, ref);
            apiCallbackService.notifyCompleted(jobId, workerId);
        } catch (JobProcessor.JobProcessingException e) {
            log.warn("[{}] Job {} failed: {}", threadName, jobId, e.getMessage());
            if (jobId != null) apiCallbackService.notifyFailed(jobId, workerId, e.getMessage());
        } catch (Exception e) {
            log.error("[{}] Unexpected error on job {}: {}", threadName, jobId, e.getMessage());
            if (jobId != null) apiCallbackService.notifyFailed(jobId, workerId, "Unexpected: " + e.getMessage());
        }
    }

    public boolean isRunning() { return running.get(); }

    public void stop() {
        running.set(false);
        if (executorService != null) executorService.shutdownNow();
    }
}
