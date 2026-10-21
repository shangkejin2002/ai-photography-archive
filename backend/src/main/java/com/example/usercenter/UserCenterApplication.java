package com.example.usercenter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.mybatis.spring.annotation.MapperScan;

@SpringBootApplication
@MapperScan("com.example.usercenter.mapper")
public class UserCenterApplication {
    public static void main(String[] args){
        SpringApplication.run(UserCenterApplication.class, args);
    }
}
