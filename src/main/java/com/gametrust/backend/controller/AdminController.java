package com.gametrust.backend.controller;

import com.gametrust.backend.dto.auth.UserResponse;
import com.gametrust.backend.dto.common.ApiResponse;
import com.gametrust.backend.entity.User;
import com.gametrust.backend.exception.ResourceNotFoundException;
import com.gametrust.backend.repository.UserRepository;
import com.gametrust.backend.service.PlatformService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final UserRepository userRepository;
    private final PlatformService platformService;

    public AdminController(UserRepository userRepository, PlatformService platformService) {
        this.userRepository = userRepository;
        this.platformService = platformService;
    }

    @GetMapping("/users")
    public ApiResponse<List<UserResponse>> users() {
        return ApiResponse.success(userRepository.findAll().stream().map(UserResponse::fromUser).toList());
    }

    @PatchMapping("/users/{id}/toggle-status")
    public ApiResponse<UserResponse> toggleStatus(@PathVariable String id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setActive(!user.isActive());
        user.touch();
        userRepository.save(user);
        return ApiResponse.success("User status updated successfully", UserResponse.fromUser(user));
    }

    // --- Tournament Management ---
    @GetMapping("/tournaments")
    public ApiResponse<List<Map<String, Object>>> getTournaments() {
        return ApiResponse.success(platformService.getTournaments(null, null));
    }

    @PostMapping("/tournaments")
    public ApiResponse<Map<String, Object>> createTournament(@RequestBody Map<String, Object> payload) {
        return ApiResponse.success("Tournament created successfully", platformService.createTournament(payload));
    }

    @PatchMapping("/tournaments/{id}/status")
    public ApiResponse<Map<String, Object>> updateTournamentStatus(
            @PathVariable String id,
            @RequestBody Map<String, String> body) {
        String status = body.getOrDefault("status", "UPCOMING");
        return ApiResponse.success("Tournament status updated", platformService.updateTournamentStatus(id, status));
    }

    @DeleteMapping("/tournaments/{id}")
    public ApiResponse<Void> deleteTournament(@PathVariable String id) {
        platformService.deleteTournament(id);
        return ApiResponse.success("Tournament deleted successfully", null);
    }

    // --- Moderation & Incident Reports ---
    @GetMapping("/reports")
    public ApiResponse<List<Map<String, Object>>> getReports() {
        return ApiResponse.success(platformService.getReports());
    }

    @PatchMapping("/reports/{id}/resolve")
    public ApiResponse<Map<String, Object>> resolveReport(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, String> body) {
        String action = body != null ? body.getOrDefault("action", "PENALTY") : "PENALTY";
        return ApiResponse.success("Report resolved successfully", platformService.resolveReport(id, action));
    }

    @PatchMapping("/reports/{id}/dismiss")
    public ApiResponse<Map<String, Object>> dismissReport(@PathVariable String id) {
        return ApiResponse.success("Report dismissed", platformService.dismissReport(id));
    }

    // --- Clan Management ---
    @GetMapping("/clans")
    public ApiResponse<List<Map<String, Object>>> getClans() {
        return ApiResponse.success(platformService.getClans(null, null));
    }

    @DeleteMapping("/clans/{id}")
    public ApiResponse<Void> deleteClan(@PathVariable String id) {
        platformService.deleteClan(id);
        return ApiResponse.success("Clan disbanded successfully", null);
    }

    // --- User Reputation & Role Oversight ---
    @PatchMapping("/users/{id}/reputation")
    public ApiResponse<UserResponse> updateReputation(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (body.containsKey("score")) {
            int score = ((Number) body.get("score")).intValue();
            user.setReputationScore(Math.max(0, Math.min(100, score)));
            user.touch();
            userRepository.save(user);
        }
        return ApiResponse.success("Reputation updated successfully", UserResponse.fromUser(user));
    }

    @PatchMapping("/users/{id}/role")
    public ApiResponse<UserResponse> updateRole(
            @PathVariable String id,
            @RequestBody Map<String, String> body) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        String roleStr = body.getOrDefault("role", "MEMBER").toUpperCase();
        try {
            user.setRole(com.gametrust.backend.entity.Role.valueOf(roleStr));
            user.touch();
            userRepository.save(user);
        } catch (IllegalArgumentException e) {
            user.setRole(com.gametrust.backend.entity.Role.MEMBER);
            user.touch();
            userRepository.save(user);
        }
        return ApiResponse.success("Role updated successfully", UserResponse.fromUser(user));
    }
}
