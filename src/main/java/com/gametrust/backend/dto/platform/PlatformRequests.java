package com.gametrust.backend.dto.platform;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public final class PlatformRequests {

    private PlatformRequests() {
    }

    public record MatchmakingRequest(
            @NotBlank String gameId,
            @NotBlank String primaryRole,
            @NotBlank String rank,
            @NotBlank String region,
            @NotEmpty List<String> neededRoles,
            boolean micRequired) {
    }

    public record TournamentRegistrationRequest(
            @NotBlank String teamName,
            @NotBlank String captainDiscord) {
    }

    public record CreatePostRequest(
            @NotBlank String content,
            String tag,
            String game) {
    }
}
