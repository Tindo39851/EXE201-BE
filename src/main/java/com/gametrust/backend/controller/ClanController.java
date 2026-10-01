package com.gametrust.backend.controller;

import com.gametrust.backend.dto.common.ApiResponse;
import com.gametrust.backend.service.PlatformService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/clans")
public class ClanController {
    private final PlatformService service;

    public ClanController(PlatformService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> clans(
            @RequestParam(required = false) String tier,
            @RequestParam(required = false) String region) {
        return ApiResponse.success(service.getClans(tier, region));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> clan(@PathVariable int id) {
        return ApiResponse.success(service.getClan(id));
    }

    @PostMapping("/{id}/join-request")
    public ApiResponse<Map<String, Object>> join(
            @PathVariable int id,
            @AuthenticationPrincipal UserDetails user) {
        return ApiResponse.success(service.requestJoinClan(id, user.getUsername()));
    }
}
