package com.example.llmgateway.gateway.mock;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/mock/v1")
public class MockLlmController {

    @PostMapping("/chat/completions")
    public Map<String, Object> createCompletion(
            @RequestBody JsonNode request
    ) {
        String userMessage = findLastUserMessage(request);

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "assistant");
        message.put(
                "content",
                "Mock reply from the gateway: " + userMessage
        );

        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("index", 0);
        choice.put("message", message);
        choice.put("finish_reason", "stop");

        Map<String, Object> usage = new LinkedHashMap<>();
        usage.put("prompt_tokens", 8);
        usage.put("completion_tokens", 12);
        usage.put("total_tokens", 20);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", "chatcmpl-mock");
        response.put("object", "chat.completion");
        response.put("model", request.path("model").asText("mock-model"));
        response.put("choices", new Object[]{choice});
        response.put("usage", usage);
        return response;
    }

    private String findLastUserMessage(JsonNode request) {
        JsonNode messages = request.path("messages");
        String content = "hello";

        for (JsonNode message : messages) {
            if ("user".equals(message.path("role").asText())) {
                content = message.path("content").asText(content);
            }
        }
        return content;
    }
}
