package com.example.usercenter.controller;

import com.example.usercenter.common.BaseResponse;
import com.example.usercenter.common.ResultUtils;
import com.example.usercenter.model.dto.photo.PhotoFavoriteRequest;
import com.example.usercenter.model.dto.photo.PhotoLocationRequest;
import com.example.usercenter.model.dto.photo.PhotoLocalizationRequest;
import com.example.usercenter.model.vo.PhotoVO;
import com.example.usercenter.service.LoginUserService;
import com.example.usercenter.service.PhotoService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/photo")
public class PhotoController {

    private final PhotoService photoService;
    private final LoginUserService loginUserService;

    public PhotoController(PhotoService photoService, LoginUserService loginUserService) {
        this.photoService = photoService;
        this.loginUserService = loginUserService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BaseResponse<PhotoVO> upload(
            @RequestPart("file") MultipartFile file,
            HttpServletRequest request
    ) {
        Long userId = loginUserService.requireUserId(request);
        return ResultUtils.success(photoService.upload(userId, file));
    }

    @GetMapping
    public BaseResponse<List<PhotoVO>> list(
            @RequestParam(required = false) Boolean favorite,
            HttpServletRequest request
    ) {
        Long userId = loginUserService.requireUserId(request);
        return ResultUtils.success(photoService.listOwned(userId, favorite));
    }

    @GetMapping("/{photoId}")
    public BaseResponse<PhotoVO> detail(
            @PathVariable Long photoId,
            HttpServletRequest request
    ) {
        Long userId = loginUserService.requireUserId(request);
        return ResultUtils.success(photoService.getOwned(userId, photoId));
    }

    @PatchMapping("/{photoId}/favorite")
    public BaseResponse<PhotoVO> favorite(
            @PathVariable Long photoId,
            @Valid @RequestBody PhotoFavoriteRequest body,
            HttpServletRequest request
    ) {
        Long userId = loginUserService.requireUserId(request);
        return ResultUtils.success(photoService.setFavorite(userId, photoId, body.favorite()));
    }

    @PatchMapping("/{photoId}/location")
    public BaseResponse<PhotoVO> location(
            @PathVariable Long photoId,
            @Valid @RequestBody PhotoLocationRequest body,
            HttpServletRequest request
    ) {
        Long userId = loginUserService.requireUserId(request);
        return ResultUtils.success(photoService.setLocationName(userId, photoId, body.locationName()));
    }

    @PostMapping("/metadata/reprocess")
    public BaseResponse<Integer> reprocessMetadata(HttpServletRequest request) {
        Long userId = loginUserService.requireUserId(request);
        return ResultUtils.success(photoService.reprocessMetadata(userId));
    }

    @PostMapping("/{photoId}/analysis")
    public BaseResponse<PhotoVO> analyze(
            @PathVariable Long photoId,
            HttpServletRequest request
    ) {
        Long userId = loginUserService.requireUserId(request);
        return ResultUtils.success(photoService.requestAnalysis(userId, photoId));
    }

    @PostMapping("/analysis/pending")
    public BaseResponse<Integer> analyzePending(HttpServletRequest request) {
        Long userId = loginUserService.requireUserId(request);
        return ResultUtils.success(photoService.requestPendingAnalyses(userId));
    }

    @PostMapping("/localizations/ja")
    public BaseResponse<List<PhotoVO>> localizeJapaneseDescriptions(
            @Valid @RequestBody PhotoLocalizationRequest body,
            HttpServletRequest request
    ) {
        Long userId = loginUserService.requireUserId(request);
        return ResultUtils.success(photoService.localizeJapaneseDescriptions(userId, body.photoIds()));
    }

    @DeleteMapping("/{photoId}")
    public BaseResponse<Boolean> delete(
            @PathVariable Long photoId,
            HttpServletRequest request
    ) {
        Long userId = loginUserService.requireUserId(request);
        return ResultUtils.success(photoService.deleteOwned(userId, photoId));
    }

    @GetMapping("/{photoId}/content")
    public ResponseEntity<Resource> content(
            @PathVariable Long photoId,
            @RequestParam(defaultValue = "thumbnail") String variant,
            HttpServletRequest request
    ) {
        Long userId = loginUserService.requireUserId(request);
        PhotoService.PhotoContent content = photoService.loadContent(userId, photoId, variant);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                .contentType(MediaType.parseMediaType(content.contentType()))
                .body(content.resource());
    }
}
