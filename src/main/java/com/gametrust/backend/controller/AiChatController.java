package com.gametrust.backend.controller;

import com.gametrust.backend.dto.ai.AiChatRequest;
import com.gametrust.backend.dto.ai.AiChatResponse;
import com.gametrust.backend.dto.ai.AiPlayerAnalysisRequest;
import com.gametrust.backend.dto.ai.AiPlayerAnalysisResponse;
import com.gametrust.backend.dto.common.ApiResponse;
import com.gametrust.backend.service.OpenAiPlayerAnalysisService;
import com.gametrust.backend.service.OpenAiChatService;
import com.gametrust.backend.service.PremiumAccessService;
import com.gametrust.backend.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiChatController {

    private final OpenAiChatService chatService;
    private final OpenAiPlayerAnalysisService playerAnalysisService;
    private final PremiumAccessService premiumAccessService;

    public AiChatController(
            OpenAiChatService chatService,
            OpenAiPlayerAnalysisService playerAnalysisService,
            PremiumAccessService premiumAccessService) {
        this.chatService = chatService;
        this.playerAnalysisService = playerAnalysisService;
        this.premiumAccessService = premiumAccessService;
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status(@AuthenticationPrincipal UserPrincipal user) {
        premiumAccessService.requireAiAccess(user);
        return ApiResponse.success(chatService.status());
    }

    @PostMapping("/chat")
    public ApiResponse<AiChatResponse> chat(
            @AuthenticationPrincipal UserPrincipal user,
            @Valid @RequestBody AiChatRequest request) {
        premiumAccessService.requireAiAccess(user);
        return ApiResponse.success("AI response created", chatService.chat(request));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/analyze-player")
    public ApiResponse<AiPlayerAnalysisResponse> analyzePlayer(
            @AuthenticationPrincipal UserPrincipal user,
            @Valid @RequestBody AiPlayerAnalysisRequest request) {
        premiumAccessService.requireAiAccess(user);
        return ApiResponse.success("Player analysis created for moderator review",
                playerAnalysisService.analyze(request.playerData()));
    }
}
