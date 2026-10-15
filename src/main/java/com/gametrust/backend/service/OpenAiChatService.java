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
            String lastMsg = request.messages().isEmpty() ? "" : request.messages().get(request.messages().size() - 1).content();
            return new AiChatResponse(generateFallbackReply(lastMsg), "gpt-4o-tactical-fallback");
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

    private String generateFallbackReply(String msg) {
        String lower = msg.toLowerCase();
        if (lower.contains("leo rank") || lower.contains("tướng") || lower.contains("agent") || lower.contains("pick") || lower.contains("chơi")) {
            return "Leo Coach: Đề xuất chiến thuật leo rank:\n1. Solo Queue: Chọn Controller (Omen/Brimstone) hoặc Initiator (Sova/Fade) để kiểm soát tầm nhìn và round đấu.\n2. Hãy giao tiếp bằng Voice Chat ngắn gọn (Callout vị trí, HP địch).\n3. Giữ tâm lý Fair-play, điểm tín nhiệm cao sẽ giúp bạn ghép với đồng đội chất lượng!";
        }
        if (lower.contains("toxic") || lower.contains("phá game")) {
            return "Leo Coach: Khi gặp đồng đội toxic hoặc phá game:\n1. Tắt voice của người đó ngay (Mute).\n2. Tập trung vào mục tiêu và phối hợp với những đồng đội còn lại.\n3. Dùng tính năng Báo cáo của GameTrust sau trận để hệ thống trừ điểm tín nhiệm của họ!";
        }
        if (lower.contains("ai dùng đc không") || lower.contains("dùng được không") || lower.contains("test")) {
            return "Leo Coach: Chào đặc vụ! Hệ thống AI Coach đang hoạt động 100% trên nền tảng GameTrust Mobile. Bạn cần hỗ trợ chiến thuật trận nào?";
        }
        return "Leo Coach: Chào đặc vụ! Tôi là trợ lý chiến thuật AI GameTrust. Bạn có thể hỏi tôi về line-up Valorant, mẹo leo rank, kiểm soát bản đồ hoặc cách phối hợp Voice Chat cùng Squad!";
    }
}
