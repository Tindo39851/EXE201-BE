package com.gametrust.backend.controller;

import com.gametrust.backend.dto.common.ApiResponse;
import com.gametrust.backend.dto.platform.PlatformRequests.CreatePostRequest;
import com.gametrust.backend.dto.platform.PlatformRequests.CreateCommentRequest;
import com.gametrust.backend.service.PlatformService;
import com.gametrust.backend.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    public ApiResponse<List<Map<String, Object>>> feed(
            @RequestParam(required = false) String category,
            @AuthenticationPrincipal UserPrincipal user) {
        return ApiResponse.success(service.getPosts(category, user == null ? null : user.getId()));
    }

    @PostMapping("/posts")
    public ApiResponse<Map<String, Object>> create(
            @Valid @RequestBody CreatePostRequest request,
            @AuthenticationPrincipal UserPrincipal user) {
        return ApiResponse.success("Post created", service.createPost(request, user.getUsername(), user.getId()));
    }

    @PutMapping("/posts/{id}/like")
    public ApiResponse<Map<String, Object>> like(
            @PathVariable String id,
            @AuthenticationPrincipal UserPrincipal user) {
        return ApiResponse.success(service.setPostLike(id, user.getId(), true));
    }

    @DeleteMapping("/posts/{id}/like")
    public ApiResponse<Map<String, Object>> unlike(
            @PathVariable String id,
            @AuthenticationPrincipal UserPrincipal user) {
        return ApiResponse.success(service.setPostLike(id, user.getId(), false));
    }

    @GetMapping("/posts/{id}/comments")
    public ApiResponse<List<Map<String, Object>>> comments(@PathVariable String id) {
        return ApiResponse.success(service.getPostComments(id));
    }

    @PostMapping("/posts/{id}/comments")
    public ApiResponse<Map<String, Object>> comment(
            @PathVariable String id,
            @Valid @RequestBody CreateCommentRequest request,
            @AuthenticationPrincipal UserPrincipal user) {
        return ApiResponse.success("Comment created", service.createPostComment(
                id, request.content(), user.getUsername(), user.getId()));
    }

    @DeleteMapping("/posts/{id}")
    public ApiResponse<Void> delete(
            @PathVariable String id,
            @AuthenticationPrincipal UserPrincipal user) {
        boolean moderator = user.getAuthorities().stream().anyMatch(authority ->
                "ROLE_ADMIN".equals(authority.getAuthority()) || "ROLE_MODERATOR".equals(authority.getAuthority()));
        service.deletePost(id, user.getUsername(), user.getId(), moderator);
        return ApiResponse.success("Post deleted", null);
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
