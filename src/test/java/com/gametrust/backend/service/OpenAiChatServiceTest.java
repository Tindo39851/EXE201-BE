package com.gametrust.backend.service;

import com.gametrust.backend.config.OpenAiProperties;
import com.gametrust.backend.dto.ai.AiChatRequest;
import com.gametrust.backend.dto.ai.AiChatResponse;
import com.gametrust.backend.exception.AiServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import org.springframework.http.HttpStatus;

class OpenAiChatServiceTest {

    @Test
    void sendsConversationToResponsesApiAndExtractsText() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiProperties properties = configuredProperties("test-key");
        OpenAiChatService service = new OpenAiChatService(builder, properties);
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(jsonPath("$.model").value("gpt-4.1-mini"))
                .andExpect(jsonPath("$.input[0].role").value("user"))
                .andExpect(jsonPath("$.input[0].content").value("Help me improve at Valorant"))
                .andRespond(withSuccess("""
                        {
                          "output": [
                            {
                              "type": "message",
                              "content": [
                                {"type": "output_text", "text": "Practice crosshair placement."}
                              ]
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        AiChatResponse response = service.chat(new AiChatRequest(List.of(
                new AiChatRequest.Message("user", "Help me improve at Valorant")
        )));

        assertEquals("Practice crosshair placement.", response.reply());
        assertEquals("gpt-4.1-mini", response.model());
        server.verify();
    }

    @Test
    void rejectsChatWhenBackendKeyIsMissing() {
        OpenAiChatService service = new OpenAiChatService(
                RestClient.builder(), configuredProperties(""));

        AiServiceException exception = assertThrows(AiServiceException.class,
                () -> service.chat(new AiChatRequest(List.of(
                        new AiChatRequest.Message("user", "Hello")
                ))));

        assertEquals(503, exception.getStatus().value());
    }

    @Test
    void reportsMissingCreditsSeparatelyFromRateLimits() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiChatService service = new OpenAiChatService(builder, configuredProperties("test-key"));
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"error":{"type":"insufficient_quota","code":"credit_balance_exhausted"}}
                                """));

        AiServiceException exception = assertThrows(AiServiceException.class,
                () -> service.chat(new AiChatRequest(List.of(
                        new AiChatRequest.Message("user", "Hello")
                ))));

        assertEquals(402, exception.getStatus().value());
        assertEquals("OpenAI project has no remaining credits. Add credits in OpenAI Platform billing.",
                exception.getMessage());
        server.verify();
    }

    private OpenAiProperties configuredProperties(String key) {
        OpenAiProperties properties = new OpenAiProperties();
        properties.setApiKey(key);
        properties.setModel("gpt-4.1-mini");
        properties.setBaseUrl("https://api.openai.com/v1");
        properties.setMaxOutputTokens(600);
        return properties;
    }
}
