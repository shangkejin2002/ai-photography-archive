package com.example.usercenter.model.vo;

import java.util.List;

public record AICreatePreviewVO(
        PortfolioIntentVO intent,
        String suggestedTitle,
        String suggestedSubtitle,
        List<PhotoCandidateVO> candidates,
        List<String> diagnostics,
        List<String> suggestions
) {
}
