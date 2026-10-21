package com.example.usercenter.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.usercenter.common.ErrorCode;
import com.example.usercenter.exception.BusinessException;
import com.example.usercenter.mapper.UserMapper;
import com.example.usercenter.model.dto.user.UserRegisterRequest;
import com.example.usercenter.model.entity.User;
import com.example.usercenter.service.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.example.usercenter.model.dto.user.UserLoginRequest;
import com.example.usercenter.model.vo.UserVO;
import jakarta.servlet.http.HttpServletRequest;

import static com.example.usercenter.constant.UserConstant.USER_LOGIN_STATE;

@Service
public class UserServiceImpl
        extends ServiceImpl<UserMapper, User>
        implements UserService {

    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public Long userRegister(UserRegisterRequest request) {

        String userAccount = request.userAccount();
        String userPassword = request.userPassword();
        String checkPassword = request.checkPassword();

        // 1. 两次密码必须一致
        if (!userPassword.equals(checkPassword)) {
            throw new BusinessException(
                    ErrorCode.PARAMS_ERROR,
                    "两次密码不一致"
            );
        }

        // 2. 账号不能重复
        long accountCount = lambdaQuery()
                .eq(User::getUserAccount, userAccount)
                .count();

        if (accountCount > 0) {
            throw new BusinessException(
                    ErrorCode.PARAMS_ERROR,
                    "账号已存在"
            );
        }

        // 3. 创建 User 对象
        User user = new User();
        user.setUserAccount(userAccount);

        // 4. 密码先 BCrypt 哈希，再保存
        String hashedPassword =
                passwordEncoder.encode(userPassword);

        user.setUserPassword(hashedPassword);

        // 5. 保存到数据库
        boolean saveResult = save(user);

        if (!saveResult) {
            throw new BusinessException(
                    ErrorCode.SYSTEM_ERROR,
                    "注册失败"
            );
        }

        // 6. 返回数据库自动生成的 id
        return user.getId();
    }

    @Override
    public UserVO userLogin(
            UserLoginRequest request,
            HttpServletRequest httpRequest
    ) {
        String userAccount = request.userAccount();
        String userPassword = request.userPassword();

        User user = lambdaQuery()
                .eq(User::getUserAccount, userAccount)
                .one();

        if (user == null ||
                !passwordEncoder.matches(
                        userPassword,
                        user.getUserPassword()
                )) {
            throw new BusinessException(
                    ErrorCode.PARAMS_ERROR,
                    "账号或密码错误"
            );
        }

        if (!Integer.valueOf(0).equals(user.getUserStatus())) {
            throw new BusinessException(
                    ErrorCode.NO_AUTH,
                    "用户已停用"
            );
        }

        UserVO safeUser = toUserVO(user);

        httpRequest.getSession();

        httpRequest.changeSessionId();

        httpRequest.getSession()
                .setAttribute(USER_LOGIN_STATE, safeUser);

        return safeUser;
    }

    @Override
    public UserVO toUserVO(User user) {
        return new UserVO(
                user.getId(),
                user.getUsername(),
                user.getUserAccount(),
                user.getAvatarUrl(),
                user.getGender(),
                user.getUserStatus(),
                user.getUserRole()
        );
    }
}
