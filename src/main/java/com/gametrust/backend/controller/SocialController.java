package com.gametrust.backend.controller;

import com.gametrust.backend.dto.common.ApiResponse;
import com.gametrust.backend.dto.platform.PlatformRequests.CreatePostRequest;
import com.gametrust.backend.service.PlatformService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/social")
public class SocialController {
    private final PlatformService service;

    public SocialController(PlatformService service) {
        this.service = service;
    }

    @GetMapping("/feed")
    public ApiResponse<List<Map<String, Object>>> feed(@RequestParam(required = false) String category) {
        return ApiResponse.success(service.getPosts(category));
    }

    @PostMapping("/posts")
    public ApiResponse<Map<String, Object>> create(
            @Valid @RequestBody CreatePostRequest request,
            @AuthenticationPrincipal UserDetails user) {
        return ApiResponse.success("Post created", service.createPost(request, user.getUsername()));
    }

    @PostMapping("/posts/{id}/like")
    public ApiResponse<Map<String, Object>> like(@PathVariable String id) {
        return ApiResponse.success(service.toggleLike(id));
    }

    @GetMapping("/online-players")
    public ApiResponse<List<Map<String, Object>>> onlinePlayers() {
        return ApiResponse.success(service.getOnlinePlayers());
    }

    @GetMapping("/trending-tags")
    public ApiResponse<List<Map<String, Object>>> trendingTags() {
        return ApiResponse.success(service.getTrendingTags());
    }
}
