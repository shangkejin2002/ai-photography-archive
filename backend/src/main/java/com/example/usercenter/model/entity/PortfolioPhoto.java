package com.example.usercenter.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@TableName("portfolio_photo")
@Data
public class PortfolioPhoto {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long portfolioId;
    private Long photoId;
    private Integer sortOrder;
    private LocalDateTime createTime;
}
