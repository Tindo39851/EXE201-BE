package com.gametrust.backend.dto.community;

public record VoiceJoinResponse(
        String serverUrl,
        String participantToken,
        long expiresInSeconds,
        String roomId,
        String livekitRoomName
) {
}
