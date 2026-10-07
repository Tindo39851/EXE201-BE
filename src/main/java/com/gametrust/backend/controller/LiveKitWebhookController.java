package com.gametrust.backend.controller;

import com.gametrust.backend.dto.common.ApiResponse;
import com.gametrust.backend.service.LiveKitWebhookService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/livekit")
public class LiveKitWebhookController {

    private final LiveKitWebhookService service;

    public LiveKitWebhookController(LiveKitWebhookService service) {
        this.service = service;
    }

    @PostMapping(value = "/webhook", consumes = {"application/webhook+json", MediaType.APPLICATION_JSON_VALUE})
    public ApiResponse<Map<String, Object>> webhook(
            @RequestBody String rawBody,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        return ApiResponse.success("Webhook accepted", service.receive(rawBody, authorizationHeader));
    }
}
