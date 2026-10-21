package com.example.usercenter.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@TableName("portfolio")
@Data
public class Portfolio {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String title;
    private String subtitle;
    private String description;
    private String sourcePrompt;
    private String status;
    private Long coverPhotoId;
    private Integer shareEnabled;
    private String shareToken;
    private LocalDateTime sharedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDelete;
}
