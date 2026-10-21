package com.example.usercenter.model.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserRegisterRequest(

        @NotBlank
        @Size(min = 4, max = 32)
        String userAccount,

        @NotBlank
        @Size(min = 8, max = 72)
        String userPassword,

        @NotBlank
        String checkPassword

) {
}
