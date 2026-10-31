package com.gametrust.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.gametrust.backend.config.OpenAiProperties;
import com.gametrust.backend.dto.ai.AiChatRequest;
import com.gametrust.backend.dto.ai.AiChatResponse;
import com.gametrust.backend.dto.ai.AiChatResponse.RoomRecommendation;
import com.gametrust.backend.exception.AiServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final CommunityService communityService;

    @Autowired
    public OpenAiChatService(RestClient.Builder restClientBuilder, OpenAiProperties properties, CommunityService communityService) {
        this.properties = properties;
        this.communityService = communityService;
        this.restClient = restClientBuilder.baseUrl(properties.getBaseUrl()).build();
    }

    public OpenAiChatService(RestClient.Builder restClientBuilder, OpenAiProperties properties) {
        this(restClientBuilder, properties, null);
    }

    public AiChatResponse chat(AiChatRequest request) {
        if (!properties.isConfigured()) {
            throw new AiServiceException(HttpStatus.SERVICE_UNAVAILABLE,
                    "AI chat is not configured. Set OPENAI_API_KEY on the backend.");
        }

        String latestUserMessage = request.messages().stream()
                .filter(message -> "user".equals(message.role())).reduce((first, second) -> second)
                .map(message -> message.content().trim()).orElse("");
        boolean asksForRoom = latestUserMessage.toLowerCase().matches("(?s).*(room|ph[oò]ng|voice|lounge|lobby).*" );
        List<RoomRecommendation> recommendations = asksForRoom && communityService != null
                ? communityService.recommendVoiceRooms(latestUserMessage).stream().map(room -> new RoomRecommendation(
                    String.valueOf(room.get("id")), String.valueOf(room.get("roomName")),
                    String.valueOf(room.get("gameId")), String.valueOf(room.get("gameName")),
                    String.valueOf(room.get("rankRequirement")), String.valueOf(room.get("playMode")),
                    (int) room.get("onlineCount"), (int) room.get("capacity"), (int) room.get("availableSlots"),
                    String.valueOf(room.get("joinUrl")))).toList()
                : List.of();

        List<Map<String, String>> input = request.messages().stream()
                .map(message -> Map.of(
                        "role", message.role(),
                        "content", message.content().trim()
                ))
                .toList();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", properties.getModel());
        String instructions = SYSTEM_INSTRUCTIONS;
        if (asksForRoom) {
            instructions += "\nThe user asked for an existing voice room. Use only the live room catalog below; never invent a room. "
                    + "The backend already filtered rooms against stated criteria. If the catalog is empty, say no matching available room exists. "
                    + "Mention that rank/play mode may be unspecified when shown that way.\nLive room catalog: " + recommendations;
        }
        payload.put("instructions", instructions);
        payload.put("input", input);
        payload.put("max_output_tokens", properties.getMaxOutputTokens());

        try {
            JsonNode response = restClient.post()
                    .uri("/responses")
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .body(payload)
                    .retrieve()
                    .body(JsonNode.class);
            return new AiChatResponse(extractOutputText(response), properties.getModel(), recommendations);
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
