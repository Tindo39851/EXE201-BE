package com.gametrust.backend.controller;

import com.gametrust.backend.dto.common.ApiResponse;
import com.gametrust.backend.dto.community.CommunityRequests.CreateRoomRequest;
import com.gametrust.backend.dto.community.CommunityRequests.SendMessageRequest;
import com.gametrust.backend.dto.community.CommunityRequests.UpdateMessageRequest;
import com.gametrust.backend.dto.community.CommunityRequests.UpdateRoomRequest;
import com.gametrust.backend.dto.community.CommunityRequests.UpdateVoiceMemberRequest;
import com.gametrust.backend.security.UserPrincipal;
import com.gametrust.backend.service.CommunityService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/community")
public class CommunityController {

    private final CommunityService service;

    public CommunityController(CommunityService service) {
        this.service = service;
    }

    @GetMapping("/games")
    public ApiResponse<List<Map<String, Object>>> games() {
        return ApiResponse.success(service.getGames());
    }

    @GetMapping("/games/{gameId}")
    public ApiResponse<Map<String, Object>> game(@PathVariable String gameId) {
        return ApiResponse.success(service.getGame(gameId));
    }

    @GetMapping("/games/{gameId}/channels")
    public ApiResponse<List<Map<String, Object>>> channels(@PathVariable String gameId) {
        return ApiResponse.success(service.getChannels(gameId));
    }

    @GetMapping("/channels/{channelId}/messages")
    public ApiResponse<List<Map<String, Object>>> messages(
            @PathVariable String channelId,
            @RequestParam(required = false) String before,
            @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.success(service.getMessages(channelId, before, limit));
    }

    @PostMapping("/channels/{channelId}/messages")
    public ApiResponse<Map<String, Object>> sendMessage(
            @PathVariable String channelId,
            @Valid @RequestBody SendMessageRequest request,
            @AuthenticationPrincipal UserPrincipal user) {
        return ApiResponse.success("Message sent", service.sendMessage(channelId, request, user));
    }

    @PatchMapping("/messages/{messageId}")
    public ApiResponse<Map<String, Object>> updateMessage(
            @PathVariable String messageId,
            @Valid @RequestBody UpdateMessageRequest request,
            @AuthenticationPrincipal UserPrincipal user) {
        return ApiResponse.success("Message updated", service.updateMessage(messageId, request, user));
    }

    @DeleteMapping("/messages/{messageId}")
    public ApiResponse<Void> deleteMessage(
            @PathVariable String messageId,
            @AuthenticationPrincipal UserPrincipal user) {
        service.deleteMessage(messageId, user);
        return ApiResponse.success("Message deleted", null);
    }

    @PostMapping("/games/{gameId}/rooms")
    public ApiResponse<Map<String, Object>> createRoom(
            @PathVariable String gameId,
            @Valid @RequestBody CreateRoomRequest request,
            @AuthenticationPrincipal UserPrincipal user) {
        return ApiResponse.success("Voice room created", service.createVoiceRoom(gameId, request, user));
    }

    @GetMapping("/rooms/{roomId}")
    public ApiResponse<Map<String, Object>> room(@PathVariable String roomId) {
        return ApiResponse.success(service.getRoom(roomId));
    }

    @PatchMapping("/rooms/{roomId}")
    public ApiResponse<Map<String, Object>> updateRoom(
            @PathVariable String roomId,
            @Valid @RequestBody UpdateRoomRequest request,
            @AuthenticationPrincipal UserPrincipal user) {
        return ApiResponse.success("Voice room updated", service.updateVoiceRoom(roomId, request, user));
    }

    @DeleteMapping("/rooms/{roomId}")
    public ApiResponse<Void> deleteRoom(
            @PathVariable String roomId,
            @AuthenticationPrincipal UserPrincipal user) {
        service.deleteVoiceRoom(roomId, user);
        return ApiResponse.success("Voice room deleted", null);
    }

    @PostMapping("/rooms/{roomId}/join")
    public ApiResponse<Map<String, Object>> joinRoom(
            @PathVariable String roomId,
            @AuthenticationPrincipal UserPrincipal user) {
        return ApiResponse.success("Joined voice room", service.joinVoiceRoom(roomId, user));
    }

    @PostMapping("/rooms/{roomId}/leave")
    public ApiResponse<Void> leaveRoom(
            @PathVariable String roomId,
            @AuthenticationPrincipal UserPrincipal user) {
        service.leaveVoiceRoom(roomId, user);
        return ApiResponse.success("Left voice room", null);
    }

    @PatchMapping("/rooms/{roomId}/members/{userId}")
    public ApiResponse<Map<String, Object>> updateMember(
            @PathVariable String roomId,
            @PathVariable String userId,
            @Valid @RequestBody UpdateVoiceMemberRequest request,
            @AuthenticationPrincipal UserPrincipal user) {
        return ApiResponse.success("Voice member updated", service.updateVoiceMember(roomId, userId, request, user));
    }

    @DeleteMapping("/rooms/{roomId}/members/{userId}")
    public ApiResponse<Void> kickMember(
            @PathVariable String roomId,
            @PathVariable String userId,
            @AuthenticationPrincipal UserPrincipal user) {
        service.kickVoiceMember(roomId, userId, user);
        return ApiResponse.success("Voice member removed", null);
    }
}
