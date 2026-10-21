package com.example.usercenter.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("photo_tag")
@Data
public class PhotoTag {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long photoId;
    private Long tagId;
    private BigDecimal confidence;
    private LocalDateTime createTime;
}
