package com.example.usercenter.controller;

import com.example.usercenter.common.ErrorCode;
import com.example.usercenter.exception.BusinessException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test")
public class TestController {

    @GetMapping("/error")
    public void testError() {
        throw new BusinessException(
                ErrorCode.PARAMS_ERROR,
                "这是一个测试错误"
        );
    }
}