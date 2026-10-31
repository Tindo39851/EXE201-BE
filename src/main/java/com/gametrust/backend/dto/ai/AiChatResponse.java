package com.gametrust.backend.dto.ai;

import java.util.List;

public record AiChatResponse(String reply, String model, List<RoomRecommendation> recommendations) {
    public AiChatResponse(String reply, String model) {
        this(reply, model, List.of());
    }

    public record RoomRecommendation(
            String id,
            String roomName,
            String gameId,
            String gameName,
            String rankRequirement,
            String playMode,
            int onlineCount,
            int capacity,
            int availableSlots,
            String joinUrl
    ) {}
}
