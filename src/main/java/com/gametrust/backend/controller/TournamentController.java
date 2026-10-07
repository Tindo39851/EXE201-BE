package com.gametrust.backend.controller;

import com.gametrust.backend.dto.common.ApiResponse;
import com.gametrust.backend.dto.platform.PlatformRequests.TournamentRegistrationRequest;
import com.gametrust.backend.service.PlatformService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tournaments")
public class TournamentController {
    private final PlatformService service;

    public TournamentController(PlatformService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> tournaments(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String game) {
        return ApiResponse.success(service.getTournaments(status, game));
    }

    @GetMapping("/my")
    public ApiResponse<List<Map<String, Object>>> myTournaments(
            @AuthenticationPrincipal UserDetails user) {
        return ApiResponse.success(service.getMyTournaments(user.getUsername()));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> tournament(@PathVariable String id) {
        return ApiResponse.success(service.getTournament(id));
    }

    @GetMapping("/{id}/bracket")
    public ApiResponse<List<Map<String, Object>>> bracket(@PathVariable String id) {
        return ApiResponse.success(service.getBracket(id));
    }

    @PostMapping("/{id}/register")
    public ApiResponse<Map<String, Object>> register(
            @PathVariable String id,
            @Valid @RequestBody TournamentRegistrationRequest request,
            @AuthenticationPrincipal UserDetails user) {
        return ApiResponse.success(service.registerTournament(id, request, user.getUsername()));
    }
}
