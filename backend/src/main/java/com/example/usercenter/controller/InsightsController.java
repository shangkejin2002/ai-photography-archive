package com.example.usercenter.controller;

import com.example.usercenter.common.BaseResponse;
import com.example.usercenter.common.ResultUtils;
import com.example.usercenter.model.vo.ShootingInsightsVO;
import com.example.usercenter.service.LoginUserService;
import com.example.usercenter.service.ShootingInsightsService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/insights")
public class InsightsController {

    private final ShootingInsightsService shootingInsightsService;
    private final LoginUserService loginUserService;

    public InsightsController(ShootingInsightsService shootingInsightsService, LoginUserService loginUserService) {
        this.shootingInsightsService = shootingInsightsService;
        this.loginUserService = loginUserService;
    }

    @GetMapping
    public BaseResponse<ShootingInsightsVO> get(HttpServletRequest request) {
        return ResultUtils.success(shootingInsightsService.getOwnedInsights(loginUserService.requireUserId(request)));
    }
}
