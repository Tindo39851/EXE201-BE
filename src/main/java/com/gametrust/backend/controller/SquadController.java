package com.gametrust.backend.controller;

import com.gametrust.backend.dto.common.ApiResponse;
import com.gametrust.backend.dto.platform.PlatformRequests.MatchmakingRequest;
import com.gametrust.backend.service.PlatformService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/squads")
public class SquadController {
    private final PlatformService service;

    public SquadController(PlatformService service) {
        this.service = service;
    }

    @GetMapping("/players")
    public ApiResponse<List<Map<String, Object>>> players(
            @RequestParam(required = false) String game,
            @RequestParam(required = false) String rank,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) Boolean micRequired) {
        return ApiResponse.success(service.getPlayers(game, rank, role, region, micRequired));
    }

    @PostMapping("/matchmake")
    public ResponseEntity<ApiResponse<Map<String, Object>>> matchmake(
            @Valid @RequestBody MatchmakingRequest request,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(ApiResponse.success("Matchmaking completed", service.matchmake(request, user.getUsername())));
    }

    @PostMapping("/invite/{playerId}")
    public ApiResponse<Map<String, Object>> invite(
            @PathVariable String playerId,
            @AuthenticationPrincipal UserDetails user) {
        return ApiResponse.success(service.invitePlayer(playerId, user.getUsername()));
    }

    @GetMapping("/invites/my")
    public ApiResponse<List<Map<String, Object>>> myInvites(
            @AuthenticationPrincipal UserDetails user) {
        return ApiResponse.success(service.getMyInvites(user.getUsername()));
    }

    @PostMapping("/invites/{id}/respond")
    public ApiResponse<Map<String, Object>> respondToInvite(
            @PathVariable String id,
            @RequestParam(defaultValue = "true") boolean accept,
            @AuthenticationPrincipal UserDetails user) {
        return ApiResponse.success(service.respondToInvite(id, accept));
    }
}
