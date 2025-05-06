//package com.example.licenta.Controllers;
//
//import org.springframework.ai.chat.messages.SystemMessage;
//import org.springframework.ai.chat.messages.UserMessage;
//import org.springframework.ai.chat.model.ChatResponse;
//import org.springframework.ai.chat.prompt.Prompt;
//import org.springframework.ai.ollama.OllamaChatModel;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.web.bind.annotation.*;
//
//import java.time.LocalDateTime;
//import java.time.format.DateTimeFormatter;
//import java.util.List;
//import java.util.Map;
//
//@RestController
//@RequestMapping("/api/assistant")
//public class AssistantController {
//
//    private final OllamaChatModel chatModel;
//
//    @Autowired
//    public AssistantController(OllamaChatModel chatModel) {
//        this.chatModel = chatModel;
//    }
//
//    @PostMapping("/chat")
//    public Map<String, String> chat(@RequestBody Map<String, String> request) {
//        String userMessage = request.get("message");
//        String userContext = request.getOrDefault("context", "");
//
//        String currentDateTime = LocalDateTime.now()
//                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
//
//        Prompt prompt;
//        if (!userContext.isEmpty()) {
//            prompt = new Prompt(
//                    List.of(
//                            new SystemMessage("You are a helpful assistant specialized in answering questions based on provided context. Context: " + userContext),
//                            new UserMessage(userMessage)
//                    )
//            );
//        } else {
//            prompt = new Prompt(
//                    List.of(
//                            // A general system prompt might still be useful
//                            new SystemMessage("You are a helpful assistant."),
//                            new UserMessage(userMessage)
//                    )
//            );
//            // Or simply: prompt = new Prompt(new UserMessage(userMessage)); if no system message is desired
//        }
//
//        ChatResponse response = chatModel.call(prompt);
//
//        return Map.of(
//                "response", response.getResult() != null ? response.getResult().getOutput().getText() : "Error: No response from model",
//                "timestamp", currentDateTime
//        );
//    }
//}

package com.example.licenta.Controllers;

import org.springframework.ai.azure.openai.AzureOpenAiChatModel;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    private final AzureOpenAiChatModel chatModel;

    @Autowired
    public AssistantController(AzureOpenAiChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @PostMapping("/chat")
    public Map<String, String> chat(@RequestBody Map<String, String> request) {
        String userMessage = request.get("message");

        String currentDateTime = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        Prompt prompt = new Prompt(
                List.of(
                        new SystemMessage("You are a helpful assistant."),
                        new UserMessage(userMessage)
                )
        );

        ChatResponse response = chatModel.call(prompt);

        return Map.of(
                "response", response.getResult() != null ? response.getResult().getOutput().getText(): "Error: No response from model",
                "timestamp", currentDateTime
        );
    }
}
