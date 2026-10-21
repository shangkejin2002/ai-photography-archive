package com.example.usercenter.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("photo")
@Data
public class Photo {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String originalFilename;
    private String originalUrl;
    private String thumbnailUrl;
    private String originalStorageKey;
    private String thumbnailStorageKey;
    private String contentType;
    private Long fileSize;
    private Integer width;
    private Integer height;
    private Integer favorite;
    private String cameraMake;
    private String cameraModel;
    private String lensModel;
    private BigDecimal focalLength;
    @TableField("focal_length_35mm")
    private Integer focalLength35mm;
    private BigDecimal aperture;
    private String shutterSpeed;
    private Integer isoValue;
    private LocalDateTime takenAt;
    private Integer orientation;
    private BigDecimal exposureBias;
    private String meteringMode;
    private String whiteBalance;
    private String flashStatus;
    private BigDecimal gpsLatitude;
    private BigDecimal gpsLongitude;
    private String locationName;
    private String metadataStatus;
    private String metadataError;
    private String aiDescription;
    private String aiDescriptionJa;
    private String aiDescriptionJaVersion;
    private String analysisStatus;
    private String analysisError;
    private String analysisModel;
    private String analysisVersion;
    private Integer analysisAttempts;
    private LocalDateTime analyzedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDelete;
}
