package com.taskforge.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskforge.model.Job;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisQueueService {
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${taskforge.queue.high}") private String highQueue;
    @Value("${taskforge.queue.normal}") private String normalQueue;
    @Value("${taskforge.queue.low}") private String lowQueue;
    @Value("${taskforge.queue.dlq}") private String dlqQueue;

    public void enqueue(Job job) {
        try {
            redisTemplate.opsForList().rightPush(resolveQueue(job.getQueuePriority()), serialize(job));
            log.info("Enqueued job {} on {}", job.getId(), resolveQueue(job.getQueuePriority()));
        } catch (Exception e) {
            throw new RuntimeException("Enqueue failed", e);
        }
    }

    public void requeue(Job job) {
        try {
            redisTemplate.opsForList().leftPush(resolveQueue(job.getQueuePriority()), serialize(job));
        } catch (Exception e) {
            log.error("Requeue failed for {}: {}", job.getId(), e.getMessage());
        }
    }

    public void moveToDlq(Job job) {
        try {
            redisTemplate.opsForList().rightPush(dlqQueue, serialize(job));
            log.warn("Job {} moved to DLQ", job.getId());
        } catch (Exception e) {
            log.error("DLQ move failed: {}", e.getMessage());
        }
    }

    public Map<String, Long> getQueueDepths() {
        return Map.of(
            "high",   size(highQueue),
            "normal", size(normalQueue),
            "low",    size(lowQueue),
            "dlq",    size(dlqQueue)
        );
    }

    private String resolveQueue(Job.QueuePriority p) {
        return switch (p) { case HIGH -> highQueue; case NORMAL -> normalQueue; case LOW -> lowQueue; };
    }

    private String serialize(Job job) throws Exception {
        Map<String, String> ref = new HashMap<>();
        ref.put("id", job.getId().toString());
        ref.put("type", job.getType());
        ref.put("priority", job.getQueuePriority().name());
        return objectMapper.writeValueAsString(ref);
    }

    private long size(String key) {
        try { Long s = redisTemplate.opsForList().size(key); return s != null ? s : 0L; }
        catch (Exception e) { return 0L; }
    }
}
