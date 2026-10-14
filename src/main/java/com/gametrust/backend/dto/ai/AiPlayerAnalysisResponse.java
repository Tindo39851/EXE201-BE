package com.gametrust.backend.dto.ai;

import java.util.List;

public record AiPlayerAnalysisResponse(
        int toxicityScore,
        boolean isToxic,
        String riskLevel,
        String summary,
        List<String> evidence,
        String recommendation,
        boolean humanReviewRequired,
        String model
) {
}
