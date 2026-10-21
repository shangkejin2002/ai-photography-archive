package com.example.usercenter.controller;

import com.example.usercenter.common.BaseResponse;
import com.example.usercenter.common.ResultUtils;
import com.example.usercenter.model.vo.PortfolioVO;
import com.example.usercenter.service.PortfolioService;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/public/portfolios")
public class PublicPortfolioController {

    private final PortfolioService portfolioService;

    public PublicPortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @GetMapping("/{shareToken}")
    public BaseResponse<PortfolioVO> detail(@PathVariable String shareToken) {
        return ResultUtils.success(portfolioService.getShared(shareToken));
    }

    @GetMapping("/{shareToken}/photos/{photoId}/content")
    public ResponseEntity<Resource> photoContent(
            @PathVariable String shareToken,
            @PathVariable Long photoId,
            @RequestParam(defaultValue = "thumbnail") String variant
    ) {
        PortfolioService.SharedPhotoContent content =
                portfolioService.loadSharedPhotoContent(shareToken, photoId, variant);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .contentType(MediaType.parseMediaType(content.contentType()))
                .body(content.resource());
    }
}
