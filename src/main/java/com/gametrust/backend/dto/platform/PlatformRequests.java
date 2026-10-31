package com.gametrust.backend.dto.platform;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

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
            @NotBlank @Size(max = 2000) String content,
            @Size(max = 32) String tag,
            @Size(max = 64) String game) {
    }

    public record CreateCommentRequest(
            @NotBlank @Size(max = 1000) String content) {
    }

    public record CreateReviewRequest(
            @NotBlank String user,
            int stars,
            @NotBlank String quote,
            String badge) {
    }

    public record CreateReportRequest(
            @NotBlank String user,
            @NotBlank String type,
            String reason) {
    }

    public record UpdateSquadProfileRequest(
            String game,
            String rank,
            List<String> roles,
            String region,
            Boolean micAvailable,
            String bio,
            Boolean lookingForSquad) {
    }
}
