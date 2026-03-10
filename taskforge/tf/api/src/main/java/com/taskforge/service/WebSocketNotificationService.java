package com.taskforge.service;
import com.taskforge.model.JobDtos;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketNotificationService {
    private final SimpMessagingTemplate messagingTemplate;

    public void broadcast(JobDtos.JobEvent event) {
        try {
            messagingTemplate.convertAndSend("/topic/jobs", event);
        } catch (Exception e) {
            log.warn("WebSocket broadcast failed: {}", e.getMessage());
        }
    }
}
