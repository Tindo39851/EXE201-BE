package com.gametrust.backend.controller;

import com.gametrust.backend.dto.ai.AiChatRequest;
import com.gametrust.backend.dto.ai.AiChatResponse;
import com.gametrust.backend.dto.common.ApiResponse;
import com.gametrust.backend.service.OpenAiChatService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiChatController {

    private final OpenAiChatService chatService;

    public AiChatController(OpenAiChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        return ApiResponse.success(chatService.status());
    }

    @PostMapping("/chat")
    public ApiResponse<AiChatResponse> chat(@Valid @RequestBody AiChatRequest request) {
        return ApiResponse.success("AI response created", chatService.chat(request));
    }
}
