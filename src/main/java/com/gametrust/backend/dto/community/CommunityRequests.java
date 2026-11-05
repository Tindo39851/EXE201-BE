package com.gametrust.backend.dto.community;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class CommunityRequests {

    private CommunityRequests() {
    }

    public record SendMessageRequest(
            @NotBlank(message = "Message content is required")
            @Size(max = 2000, message = "Message must not exceed 2000 characters")
            String content
    ) {
    }

    public record UpdateMessageRequest(
            @NotBlank(message = "Message content is required")
            @Size(max = 2000, message = "Message must not exceed 2000 characters")
            String content
    ) {
    }

    public record CreateRoomRequest(
            @NotBlank(message = "Room name is required")
            @Size(max = 60, message = "Room name must not exceed 60 characters")
            String name,
            @Size(max = 40, message = "Rank requirement must not exceed 40 characters")
            String rankRequirement,
            @Size(max = 50, message = "Play mode must not exceed 50 characters")
            String playMode,
            @Min(value = 2, message = "Room capacity must be at least 2")
            @Max(value = 50, message = "Room capacity must not exceed 50")
            Integer capacity,
            @Size(max = 40, message = "Room tag must not exceed 40 characters")
            String tag,
            @Size(max = 20, message = "Visibility must not exceed 20 characters")
            String visibility,
            @Size(max = 5, message = "A room can have at most 5 personality tags")
            List<@NotBlank(message = "Personality tag must not be blank")
                    @Size(max = 30, message = "Personality tag must not exceed 30 characters") String> personalityTags,
            @Size(max = 300, message = "Room description must not exceed 300 characters")
            String description
    ) {
    }

    public record UpdateRoomRequest(
            @Size(min = 1, max = 60, message = "Room name must contain 1 to 60 characters")
            String name,
            @Min(value = 2, message = "Room capacity must be at least 2")
            @Max(value = 50, message = "Room capacity must not exceed 50")
            Integer capacity,
            Boolean locked,
            @Size(max = 40, message = "Rank requirement must not exceed 40 characters")
            String rankRequirement,
            @Size(max = 50, message = "Play mode must not exceed 50 characters")
            String playMode
    ) {
    }

    public record UpdateVoiceMemberRequest(Boolean muted) {
    }
}
