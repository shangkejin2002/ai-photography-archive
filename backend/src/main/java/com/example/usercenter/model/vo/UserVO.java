package com.example.usercenter.model.vo;

public record UserVO(
        Long id,
        String username,
        String userAccount,
        String avatarUrl,
        Integer gender,
        Integer userStatus,
        Integer userRole
) {
}
