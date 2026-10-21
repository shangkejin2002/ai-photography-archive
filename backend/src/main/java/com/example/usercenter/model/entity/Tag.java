package com.example.usercenter.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@TableName("tag")
@Data
public class Tag {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String name;
    private String tagType;
    private LocalDateTime createTime;
}
