package com.taskforge.worker.service;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import java.util.UUID;

@Slf4j
@Service
public class ApiCallbackService {
    private final RestClient restClient;

    public ApiCallbackService(RestClient.Builder builder,
            @Value("${taskforge.worker.api-base-url}") String apiBaseUrl) {
        this.restClient = builder.baseUrl(apiBaseUrl).build();
    }

    public void notifyStarted(UUID jobId, String workerId) {
        call("/api/v1/jobs/" + jobId + "/started?workerId=" + workerId, jobId);
    }
    public void notifyCompleted(UUID jobId, String workerId) {
        call("/api/v1/jobs/" + jobId + "/completed?workerId=" + workerId, jobId);
    }
    public void notifyFailed(UUID jobId, String workerId, String reason) {
        try {
            String encoded = java.net.URLEncoder.encode(reason, "UTF-8");
            call("/api/v1/jobs/" + jobId + "/failed?workerId=" + workerId + "&reason=" + encoded, jobId);
        } catch (Exception e) {
            call("/api/v1/jobs/" + jobId + "/failed?workerId=" + workerId, jobId);
        }
    }
    private void call(String uri, UUID jobId) {
        try {
            restClient.post().uri(uri).retrieve().toBodilessEntity();
        } catch (Exception e) {
            log.warn("API callback failed for job {}: {}", jobId, e.getMessage());
        }
    }
}
