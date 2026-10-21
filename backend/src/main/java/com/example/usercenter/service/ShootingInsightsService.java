package com.example.usercenter.service;

import com.example.usercenter.model.vo.ShootingInsightsVO;

public interface ShootingInsightsService {
    ShootingInsightsVO getOwnedInsights(Long userId);
}
