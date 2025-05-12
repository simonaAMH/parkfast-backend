package com.example.licenta.Controllers;

import com.example.licenta.Services.AssistantService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    private final RestTemplate restTemplate;
    private final String openAiApiKey;
    private final String assistantId;
    private final AssistantService assistantService;
    private final HttpHeaders headers;

    private static final Pattern CITATION_PATTERN = Pattern.compile("【.*?†source】");

    public AssistantController(
            RestTemplate restTemplate,
            @Value("${openai.api.key}") String openAiApiKey,
            @Value("${openai.assistant.id}") String assistantId,
            AssistantService assistantService) {
        this.restTemplate = restTemplate;
        this.openAiApiKey = openAiApiKey;
        this.assistantId = assistantId;
        this.assistantService = assistantService;

        this.headers = new HttpHeaders();
        this.headers.setContentType(MediaType.APPLICATION_JSON);
        this.headers.setBearerAuth(this.openAiApiKey);
        this.headers.add("OpenAI-Beta", "assistants=v2");
    }

    @PostMapping("/chat")
    public ResponseEntity<Map<String, String>> chat(@RequestBody Map<String, String> request) {
        String userMessage = request.get("message");
        String existingThreadId = request.get("threadId");
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String currentThreadId = existingThreadId;

        Map<String, String> errorResponseBoilerplate = new HashMap<>();
        errorResponseBoilerplate.put("timestamp", timestamp);


        try {
            if (currentThreadId == null || currentThreadId.trim().isEmpty()) {
                // No threadId provided, or it's empty. Create a new one.
                System.out.println("No valid threadId provided, creating a new thread.");
                HttpEntity<String> threadRequestEntity = new HttpEntity<>("{}", headers);
                ResponseEntity<Map> threadResponseEntity = restTemplate.postForEntity(
                        "https://api.openai.com/v1/threads", threadRequestEntity, Map.class);

                if (threadResponseEntity.getBody() == null || !threadResponseEntity.getBody().containsKey("id")) {
                    errorResponseBoilerplate.put("response", "Error: Could not create thread or thread ID missing.");
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponseBoilerplate);
                }
                currentThreadId = (String) threadResponseEntity.getBody().get("id");
                System.out.println("New thread created with Id: " + currentThreadId);
            } else {
                System.out.println("Continuing with existing threadId: " + currentThreadId);
            }
            assistantService.recordThreadActivity(currentThreadId);

            // 2. Add message to thread (using currentThreadId)
            Map<String, Object> messageBody = Map.of(
                    "role", "user",
                    "content", userMessage
            );
            HttpEntity<Map<String, Object>> messageRequestEntity = new HttpEntity<>(messageBody, headers);
            try {
                restTemplate.postForEntity(
                        "https://api.openai.com/v1/threads/" + currentThreadId + "/messages", messageRequestEntity, Map.class);
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                    System.err.println("Error: Thread with ID " + currentThreadId + " not found when trying to add message. A new thread will be implicitly created by OpenAI if we proceed, or handle as error.");
                    errorResponseBoilerplate.put("response", "Error: Provided conversation thread ID " + currentThreadId + " was not found.");
                    errorResponseBoilerplate.put("threadId", currentThreadId);
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponseBoilerplate);
                }
                throw e;
            }

            assistantService.recordThreadActivity(currentThreadId);

            // 3. Run the assistant (using currentThreadId)
            Map<String, Object> runBody = Map.of("assistant_id", this.assistantId);
            HttpEntity<Map<String, Object>> runRequestEntity = new HttpEntity<>(runBody, headers);
            ResponseEntity<Map> runResponseEntity = restTemplate.postForEntity(
                    "https://api.openai.com/v1/threads/" + currentThreadId + "/runs", runRequestEntity, Map.class);

            if (runResponseEntity.getBody() == null || !runResponseEntity.getBody().containsKey("id")) {
                errorResponseBoilerplate.put("response", "Error: Could not create run or run ID missing.");
                errorResponseBoilerplate.put("threadId", currentThreadId);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponseBoilerplate);
            }
            String runId = (String) runResponseEntity.getBody().get("id");
            assistantService.recordThreadActivity(currentThreadId);

            // 4. Poll until run completes (using currentThreadId and runId)
            String runStatus;
            long startTime = System.currentTimeMillis();
            long timeoutMillis = 90000;

            do {
                if (System.currentTimeMillis() - startTime > timeoutMillis) {
                    errorResponseBoilerplate.put("response", "Error: Run timed out waiting for completion.");
                    errorResponseBoilerplate.put("threadId", currentThreadId);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponseBoilerplate);
                }
                Thread.sleep(1500); // Polling interval
                HttpEntity<Void> statusRequestEntity = new HttpEntity<>(headers);
                ResponseEntity<Map> statusResponseEntity = restTemplate.exchange(
                        "https://api.openai.com/v1/threads/" + currentThreadId + "/runs/" + runId,
                        HttpMethod.GET,
                        statusRequestEntity,
                        Map.class
                );
                if (statusResponseEntity.getBody() == null || !statusResponseEntity.getBody().containsKey("status")) {
                    errorResponseBoilerplate.put("response", "Error: Could not get run status or status missing.");
                    errorResponseBoilerplate.put("threadId", currentThreadId);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponseBoilerplate);
                }
                runStatus = (String) statusResponseEntity.getBody().get("status");
                if ("failed".equalsIgnoreCase(runStatus) || "cancelled".equalsIgnoreCase(runStatus) || "expired".equalsIgnoreCase(runStatus) || "requires_action".equalsIgnoreCase(runStatus)) {
                    assistantService.recordThreadActivity(currentThreadId);
                    errorResponseBoilerplate.put("response", "Error: Run ended with status: " + runStatus);
                    errorResponseBoilerplate.put("threadId", currentThreadId);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponseBoilerplate);
                }
                assistantService.recordThreadActivity(currentThreadId);
            } while (!"completed".equals(runStatus));


            // 5. Fetch assistant reply (using currentThreadId)
            HttpEntity<Void> messagesRequestEntity = new HttpEntity<>(headers);
            // Fetch messages in descending order to easily get the latest assistant reply if there are many.
            // Then, we'll find the first assistant message from the top.
            ResponseEntity<Map> messagesResponseEntity = restTemplate.exchange(
                    "https://api.openai.com/v1/threads/" + currentThreadId + "/messages?order=desc&limit=20", // Get recent messages
                    HttpMethod.GET,
                    messagesRequestEntity,
                    Map.class
            );

            if (messagesResponseEntity.getBody() == null || !messagesResponseEntity.getBody().containsKey("data")) {
                errorResponseBoilerplate.put("response", "Error: Could not retrieve messages or data missing.");
                errorResponseBoilerplate.put("threadId", currentThreadId);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponseBoilerplate);
            }
            List<Map<String, Object>> messages = (List<Map<String, Object>>) messagesResponseEntity.getBody().get("data");

            // Find the latest assistant message
            String rawAssistantReply = messages.stream()
                    .filter(m -> "assistant".equals(m.get("role")))
                    .findFirst()
                    .map(m -> {
                        List<Map<String, Object>> contentParts = (List<Map<String, Object>>) m.get("content");
                        if (contentParts != null && !contentParts.isEmpty()) {
                            return contentParts.stream()
                                    .filter(cp -> "text".equals(cp.get("type")))
                                    .map(cp -> {
                                        Map<String, Object> textObject = (Map<String, Object>) cp.get("text");
                                        return textObject != null ? (String) textObject.get("value") : "";
                                    })
                                    .filter(val -> val != null && !val.isEmpty())
                                    .collect(Collectors.joining("\n"));
                        }
                        return "";
                    })
                    .filter(s -> s != null && !s.isEmpty())
                    .orElse("No suitable reply from assistant in the latest messages.");


            String assistantReply = CITATION_PATTERN.matcher(rawAssistantReply).replaceAll("").trim();

            Map<String, String> successResponse = new HashMap<>();
            successResponse.put("response", assistantReply);
            successResponse.put("timestamp", timestamp);
            successResponse.put("threadId", currentThreadId);

            return ResponseEntity.ok(successResponse);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Chat interrupted for user message: " + userMessage + (currentThreadId != null ? ", threadId: " + currentThreadId : "") + " - " + e.getMessage());
            errorResponseBoilerplate.put("response", "Error: Request processing was interrupted.");
            if (currentThreadId != null) errorResponseBoilerplate.put("threadId", currentThreadId);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponseBoilerplate);
        } catch (HttpClientErrorException e) {
            e.printStackTrace();
            System.err.println("Chat failed for user message: " + userMessage + (currentThreadId != null ? ", threadId: " + currentThreadId : "") + " - Status: " + e.getStatusCode() + " Body: " + e.getResponseBodyAsString());
            errorResponseBoilerplate.put("response", "Error communicating with AI service: " + e.getStatusCode() + (e.getResponseBodyAsString().contains("No active run") ? " (No active run on thread)" : ""));
            if (currentThreadId != null) errorResponseBoilerplate.put("threadId", currentThreadId);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponseBoilerplate);
        }
        catch (Exception e) {
            e.printStackTrace();
            System.err.println("Chat failed for user message: " + userMessage + (currentThreadId != null ? ", threadId: " + currentThreadId : "") + " - " + e.getMessage());
            errorResponseBoilerplate.put("response", "Error: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            if (currentThreadId != null) errorResponseBoilerplate.put("threadId", currentThreadId);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponseBoilerplate);
        }
    }
}