package com.example.usercenter.controller;

import com.example.usercenter.common.BaseResponse;
import com.example.usercenter.common.ErrorCode;
import com.example.usercenter.common.ResultUtils;
import com.example.usercenter.exception.BusinessException;
import com.example.usercenter.model.dto.user.UserLoginRequest;
import com.example.usercenter.model.dto.user.UserRegisterRequest;
import com.example.usercenter.model.entity.User;
import com.example.usercenter.model.vo.UserVO;
import com.example.usercenter.service.LoginUserService;
import com.example.usercenter.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/user")
public class UserController {

    private final UserService userService;
    private final LoginUserService loginUserService;

    public UserController(UserService userService, LoginUserService loginUserService) {
        this.userService = userService;
        this.loginUserService = loginUserService;
    }

    @PostMapping("/register")
    public BaseResponse<Long> register(
            @Valid @RequestBody UserRegisterRequest request
    ) {
        Long userId = userService.userRegister(request);
        return ResultUtils.success(userId);
    }

    @PostMapping("/login")
    public BaseResponse<UserVO> login(
            @Valid @RequestBody UserLoginRequest request,
            HttpServletRequest httpRequest
    ) {
        UserVO userVO = userService.userLogin(request, httpRequest);
        return ResultUtils.success(userVO);
    }

    @GetMapping("/current")
    public BaseResponse<UserVO> current(HttpServletRequest request) {
        return ResultUtils.success(loginUserService.requireLogin(request));
    }

    // 注销并使当前 Session 失效
    @PostMapping("/logout")
    public BaseResponse<Boolean> logout(
            HttpServletRequest request
    ) {
        var session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResultUtils.success(true);
    }


    @GetMapping("/search")
    public BaseResponse<List<UserVO>> searchUsers(
            String username,
            HttpServletRequest request
    ) {
        loginUserService.requireAdmin(request);

        List<UserVO> data = userService.lambdaQuery()
                .like(
                        StringUtils.hasText(username),
                        User::getUsername,
                        username
                )
                .list()
                .stream()
                .map(userService::toUserVO)
                .toList();

        return ResultUtils.success(data);
    }

    // 管理员逻辑删除用户
    @PostMapping("/delete")
    public BaseResponse<Boolean> delete(
            @RequestBody Long id,
            HttpServletRequest request
    ) {
        loginUserService.requireAdmin(request);

        if (id == null || id <= 0) {
            throw new BusinessException(
                    ErrorCode.PARAMS_ERROR
            );
        }

        return ResultUtils.success(
                userService.removeById(id)
        );
    }
}
