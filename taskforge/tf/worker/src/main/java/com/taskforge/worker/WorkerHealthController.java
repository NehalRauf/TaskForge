package com.taskforge.worker;
import com.taskforge.worker.service.WorkerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class WorkerHealthController {
    private final WorkerService workerService;
    @GetMapping("/actuator/health")
    public Map<String, Object> health() {
        return Map.of("status", workerService.isRunning() ? "UP" : "DOWN");
    }
}
