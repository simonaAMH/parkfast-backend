package com.example.licenta.Services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AssistantService {

    private final RestTemplate restTemplate;
    private final String openAiApiKey;
    private final HttpHeaders deleteHeaders;

    private final Map<String, LocalDateTime> threadActivityMap = new ConcurrentHashMap<>();

    public AssistantService(RestTemplate restTemplate,
                            @Value("${openai.api.key}") String openAiApiKey) {
        this.restTemplate = restTemplate;
        this.openAiApiKey = openAiApiKey;
        this.deleteHeaders = new HttpHeaders();
        this.deleteHeaders.setBearerAuth(this.openAiApiKey);
        this.deleteHeaders.add("OpenAI-Beta", "assistants=v2");
    }

    public void recordThreadActivity(String threadId) {
        threadActivityMap.put(threadId, LocalDateTime.now());
    }

    private void deleteThread(String threadId) {
        HttpEntity<Void> request = new HttpEntity<>(deleteHeaders);
        try {
            restTemplate.exchange(
                    "https://api.openai.com/v1/threads/" + threadId,
                    HttpMethod.DELETE,
                    request,
                    Void.class
            );
            System.out.println("Successfully deleted thread: " + threadId);
        } catch (Exception e) {
            System.err.println("Failed to delete thread " + threadId + ": " + e.getMessage());
        }
    }

    @Scheduled(fixedRate = 300000)
    public void cleanUpInactiveThreads() {
        LocalDateTime now = LocalDateTime.now();
        System.out.println("Running cleanup task for inactive threads at " + now);
        threadActivityMap.entrySet().removeIf(entry -> {
            boolean shouldRemove = Duration.between(entry.getValue(), now).toMinutes() >= 5;
            if (shouldRemove) {
                System.out.println("Thread " + entry.getKey() + " inactive for 5 minutes or more. Deleting.");
                deleteThread(entry.getKey());
            }
            return shouldRemove;
        });
        System.out.println("Cleanup task finished. Current active threads: " + threadActivityMap.size());
    }
}