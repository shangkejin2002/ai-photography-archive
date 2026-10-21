package com.example.usercenter.controller;

import com.example.usercenter.common.BaseResponse;
import com.example.usercenter.common.ResultUtils;
import com.example.usercenter.model.dto.portfolio.AICreatePreviewRequest;
import com.example.usercenter.model.vo.AICreatePreviewVO;
import com.example.usercenter.service.AICreateService;
import com.example.usercenter.service.LoginUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ai-create")
public class AICreateController {
    private final AICreateService aiCreateService;
    private final LoginUserService loginUserService;

    public AICreateController(AICreateService aiCreateService, LoginUserService loginUserService) {
        this.aiCreateService = aiCreateService;
        this.loginUserService = loginUserService;
    }

    @PostMapping("/preview")
    public BaseResponse<AICreatePreviewVO> preview(
            @Valid @RequestBody AICreatePreviewRequest body,
            HttpServletRequest request
    ) {
        Long userId = loginUserService.requireUserId(request);
        return ResultUtils.success(aiCreateService.preview(userId, body.prompt()));
    }
}
