package com.example.usercenter.model.dto.user;

import jakarta.validation.constraints.NotBlank;

public record UserLoginRequest(

        @NotBlank
        String userAccount,

        @NotBlank
        String userPassword

) {
}