package com.example.usercenter.service;

import com.example.usercenter.common.ErrorCode;
import com.example.usercenter.exception.BusinessException;
import com.example.usercenter.model.entity.User;
import com.example.usercenter.model.vo.UserVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import static com.example.usercenter.constant.UserConstant.ADMIN_ROLE;
import static com.example.usercenter.constant.UserConstant.USER_LOGIN_STATE;

@Service
public class LoginUserService {

    private final UserService userService;

    public LoginUserService(UserService userService) {
        this.userService = userService;
    }

    public UserVO requireLogin(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }

        Object value = session.getAttribute(USER_LOGIN_STATE);
        if (!(value instanceof UserVO cached)) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }

        User current = userService.getById(cached.id());
        if (current == null || !Integer.valueOf(0).equals(current.getUserStatus())) {
            session.invalidate();
            throw new BusinessException(ErrorCode.NOT_LOGIN, "用户不存在或已停用");
        }

        UserVO refreshed = userService.toUserVO(current);
        session.setAttribute(USER_LOGIN_STATE, refreshed);
        return refreshed;
    }

    public Long requireUserId(HttpServletRequest request) {
        return requireLogin(request).id();
    }

    public UserVO requireAdmin(HttpServletRequest request) {
        UserVO current = requireLogin(request);
        if (!Integer.valueOf(ADMIN_ROLE).equals(current.userRole())) {
            throw new BusinessException(ErrorCode.NO_AUTH);
        }
        return current;
    }
}
