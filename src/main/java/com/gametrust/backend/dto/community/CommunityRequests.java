package com.gametrust.backend.dto.community;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

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
            @Min(value = 2, message = "Room capacity must be at least 2")
            @Max(value = 50, message = "Room capacity must not exceed 50")
            Integer capacity
    ) {
    }

    public record UpdateRoomRequest(
            @Size(min = 1, max = 60, message = "Room name must contain 1 to 60 characters")
            String name,
            @Min(value = 2, message = "Room capacity must be at least 2")
            @Max(value = 50, message = "Room capacity must not exceed 50")
            Integer capacity,
            Boolean locked
    ) {
    }

    public record UpdateVoiceMemberRequest(Boolean muted) {
    }
}
