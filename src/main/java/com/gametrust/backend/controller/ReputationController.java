package com.gametrust.backend.controller;

import com.gametrust.backend.dto.common.ApiResponse;
import com.gametrust.backend.dto.platform.PlatformRequests.CreateReportRequest;
import com.gametrust.backend.dto.platform.PlatformRequests.CreateReviewRequest;
import com.gametrust.backend.service.PlatformService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reputation")
public class ReputationController {
    private final PlatformService service;

    public ReputationController(PlatformService service) {
        this.service = service;
    }

    @GetMapping("/metrics")
    public ApiResponse<Map<String, Object>> metrics() {
        return ApiResponse.success(service.getMetrics());
    }

    @GetMapping("/reports")
    public ApiResponse<List<Map<String, Object>>> reports() {
        return ApiResponse.success(service.getReports());
    }

    @GetMapping("/reviews")
    public ApiResponse<List<Map<String, Object>>> reviews() {
        return ApiResponse.success(service.getReviews());
    }

    @GetMapping("/top-players")
    public ApiResponse<List<Map<String, Object>>> topPlayers() {
        return ApiResponse.success(service.getTopPlayers());
    }

    @PostMapping("/reviews")
    public ApiResponse<Map<String, Object>> createReview(
            @Valid @RequestBody CreateReviewRequest request,
            @AuthenticationPrincipal UserDetails user) {
        return ApiResponse.success("Review submitted successfully", service.createReview(request, user.getUsername()));
    }

    @PostMapping("/reports")
    public ApiResponse<Map<String, Object>> createReport(
            @Valid @RequestBody CreateReportRequest request,
            @AuthenticationPrincipal UserDetails user) {
        return ApiResponse.success("Report submitted successfully", service.createReport(request, user.getUsername()));
    }
}
