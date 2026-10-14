package com.gametrust.backend.dto.ai;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

public record AiPlayerAnalysisRequest(
        @NotNull(message = "Player JSON is required")
        JsonNode playerData
) {
}
