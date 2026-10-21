package com.example.usercenter.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.usercenter.model.dto.user.UserRegisterRequest;
import com.example.usercenter.model.entity.User;
import com.example.usercenter.model.dto.user.UserLoginRequest;
import com.example.usercenter.model.vo.UserVO;
import jakarta.servlet.http.HttpServletRequest;

public interface UserService extends IService<User> {

    Long userRegister(UserRegisterRequest request);

    UserVO userLogin(
            UserLoginRequest request,
            HttpServletRequest httpRequest
    );

    UserVO toUserVO(User user);
}