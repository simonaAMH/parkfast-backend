package com.example.licenta.Controllers;

import com.example.licenta.Services.AssistantService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        try {
            // 1. Create a thread
            HttpEntity<String> threadRequestEntity = new HttpEntity<>("{}", headers);
            ResponseEntity<Map> threadResponseEntity = restTemplate.postForEntity(
                    "https://api.openai.com/v1/threads", threadRequestEntity, Map.class);

            if (threadResponseEntity.getBody() == null || !threadResponseEntity.getBody().containsKey("id")) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("response", "Error: Could not create thread or thread ID missing.", "timestamp", timestamp));
            }
            String threadId = (String) threadResponseEntity.getBody().get("id");
            assistantService.recordThreadActivity(threadId);

            // 2. Add message to thread
            Map<String, Object> messageBody = Map.of(
                    "role", "user",
                    "content", userMessage
            );
            HttpEntity<Map<String, Object>> messageRequestEntity = new HttpEntity<>(messageBody, headers);
            restTemplate.postForEntity(
                    "https://api.openai.com/v1/threads/" + threadId + "/messages", messageRequestEntity, Map.class);
            assistantService.recordThreadActivity(threadId);

            // 3. Run the assistant
            Map<String, Object> runBody = Map.of("assistant_id", this.assistantId);
            HttpEntity<Map<String, Object>> runRequestEntity = new HttpEntity<>(runBody, headers);
            ResponseEntity<Map> runResponseEntity = restTemplate.postForEntity(
                    "https://api.openai.com/v1/threads/" + threadId + "/runs", runRequestEntity, Map.class);

            if (runResponseEntity.getBody() == null || !runResponseEntity.getBody().containsKey("id")) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("response", "Error: Could not create run or run ID missing.", "timestamp", timestamp));
            }
            String runId = (String) runResponseEntity.getBody().get("id");
            assistantService.recordThreadActivity(threadId);

            // 4. Poll until run completes
            String runStatus;
            long startTime = System.currentTimeMillis();
            long timeoutMillis = 60000;

            do {
                if (System.currentTimeMillis() - startTime > timeoutMillis) {
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(Map.of("response", "Error: Run timed out waiting for completion.", "timestamp", timestamp));
                }
                Thread.sleep(1500);
                HttpEntity<Void> statusRequestEntity = new HttpEntity<>(headers);
                ResponseEntity<Map> statusResponseEntity = restTemplate.exchange(
                        "https://api.openai.com/v1/threads/" + threadId + "/runs/" + runId,
                        HttpMethod.GET,
                        statusRequestEntity,
                        Map.class
                );
                if (statusResponseEntity.getBody() == null || !statusResponseEntity.getBody().containsKey("status")) {
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(Map.of("response", "Error: Could not get run status or status missing.", "timestamp", timestamp));
                }
                runStatus = (String) statusResponseEntity.getBody().get("status");
                if ("failed".equalsIgnoreCase(runStatus) || "cancelled".equalsIgnoreCase(runStatus) || "expired".equalsIgnoreCase(runStatus) || "requires_action".equalsIgnoreCase(runStatus)) {
                    assistantService.recordThreadActivity(threadId);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(Map.of("response", "Error: Run ended with status: " + runStatus, "timestamp", timestamp));
                }
                assistantService.recordThreadActivity(threadId);
            } while (!"completed".equals(runStatus));


            // 5. Fetch assistant reply
            HttpEntity<Void> messagesRequestEntity = new HttpEntity<>(headers);
            ResponseEntity<Map> messagesResponseEntity = restTemplate.exchange(
                    "https://api.openai.com/v1/threads/" + threadId + "/messages?order=asc",
                    HttpMethod.GET,
                    messagesRequestEntity,
                    Map.class
            );

            if (messagesResponseEntity.getBody() == null || !messagesResponseEntity.getBody().containsKey("data")) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("response", "Error: Could not retrieve messages or data missing.", "timestamp", timestamp));
            }
            List<Map<String, Object>> messages = (List<Map<String, Object>>) messagesResponseEntity.getBody().get("data");

            String rawAssistantReply = messages.stream() // Renamed to rawAssistantReply
                    .filter(m -> "assistant".equals(m.get("role")))
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
                    .reduce((first, second) -> second)
                    .orElse("No suitable reply from assistant.");

            String assistantReply = CITATION_PATTERN.matcher(rawAssistantReply).replaceAll("").trim();

            return ResponseEntity.ok(Map.of(
                    "response", assistantReply,
                    "timestamp", timestamp
            ));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Chat interrupted for user message: " + userMessage + " - " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("response", "Error: Request processing was interrupted.", "timestamp", timestamp));
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Chat failed for user message: " + userMessage + " - " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("response", "Error: " + e.getClass().getSimpleName() + " - " + e.getMessage(), "timestamp", timestamp));
        }
    }
}