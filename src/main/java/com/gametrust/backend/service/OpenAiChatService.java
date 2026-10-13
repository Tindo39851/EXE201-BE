package com.gametrust.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.gametrust.backend.config.OpenAiProperties;
import com.gametrust.backend.dto.ai.AiChatRequest;
import com.gametrust.backend.dto.ai.AiChatResponse;
import com.gametrust.backend.exception.AiServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OpenAiChatService {

    private static final String SYSTEM_INSTRUCTIONS = """
            You are GameTrust AI Coach, a concise and friendly assistant for gamers.
            Help with teamwork, communication, practice plans, roles, strategy and healthy gaming habits.
            Reply in the same language as the user. Do not claim to have inspected player data unless it was supplied.
            Do not make moderation or ban decisions in chat mode. If asked about toxicity, explain that a later
            structured analysis system must combine model output with verified reports and human review.
            """;

    private final RestClient restClient;
    private final OpenAiProperties properties;

    public OpenAiChatService(RestClient.Builder restClientBuilder, OpenAiProperties properties) {
        this.properties = properties;
        this.restClient = restClientBuilder.baseUrl(properties.getBaseUrl()).build();
    }

    public AiChatResponse chat(AiChatRequest request) {
        if (!properties.isConfigured()) {
            throw new AiServiceException(HttpStatus.SERVICE_UNAVAILABLE,
                    "AI chat is not configured. Set OPENAI_API_KEY on the backend.");
        }

        List<Map<String, String>> input = request.messages().stream()
                .map(message -> Map.of(
                        "role", message.role(),
                        "content", message.content().trim()
                ))
                .toList();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", properties.getModel());
        payload.put("instructions", SYSTEM_INSTRUCTIONS);
        payload.put("input", input);
        payload.put("max_output_tokens", properties.getMaxOutputTokens());

        try {
            JsonNode response = restClient.post()
                    .uri("/responses")
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .body(payload)
                    .retrieve()
                    .body(JsonNode.class);
            return new AiChatResponse(extractOutputText(response), properties.getModel());
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 401) {
                throw new AiServiceException(HttpStatus.BAD_GATEWAY,
                        "OpenAI rejected the backend API key. Rotate and configure a valid key.");
            }
            if (exception.getStatusCode().value() == 429) {
                String responseBody = exception.getResponseBodyAsString();
                if (responseBody.contains("insufficient_quota")
                        || responseBody.contains("credit_balance_exhausted")) {
                    throw new AiServiceException(HttpStatus.PAYMENT_REQUIRED,
                            "OpenAI project has no remaining credits. Add credits in OpenAI Platform billing.");
                }
                throw new AiServiceException(HttpStatus.TOO_MANY_REQUESTS,
                        "AI is receiving too many requests. Please try again shortly.");
            }
            throw new AiServiceException(HttpStatus.BAD_GATEWAY,
                    "OpenAI could not complete this request.");
        } catch (RestClientException exception) {
            throw new AiServiceException(HttpStatus.BAD_GATEWAY,
                    "Could not connect to OpenAI. Please try again shortly.");
        }
    }

    public Map<String, Object> status() {
        return Map.of(
                "configured", properties.isConfigured(),
                "model", properties.getModel()
        );
    }

    private String extractOutputText(JsonNode response) {
        if (response != null) {
            for (JsonNode output : response.path("output")) {
                for (JsonNode content : output.path("content")) {
                    if ("output_text".equals(content.path("type").asText())) {
                        String text = content.path("text").asText().trim();
                        if (!text.isBlank()) return text;
                    }
                }
            }
        }
        throw new AiServiceException(HttpStatus.BAD_GATEWAY,
                "OpenAI returned an empty response.");
    }
}
