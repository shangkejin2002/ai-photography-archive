package com.example.usercenter.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.usercenter.common.ErrorCode;
import com.example.usercenter.exception.BusinessException;
import com.example.usercenter.mapper.PortfolioMapper;
import com.example.usercenter.mapper.PortfolioPhotoMapper;
import com.example.usercenter.model.dto.portfolio.PortfolioCreateRequest;
import com.example.usercenter.model.dto.portfolio.PortfolioUpdateRequest;
import com.example.usercenter.model.entity.Portfolio;
import com.example.usercenter.model.entity.PortfolioPhoto;
import com.example.usercenter.model.vo.PhotoVO;
import com.example.usercenter.model.vo.PortfolioVO;
import com.example.usercenter.service.PhotoService;
import com.example.usercenter.service.PortfolioService;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

@Service
public class PortfolioServiceImpl extends ServiceImpl<PortfolioMapper, Portfolio> implements PortfolioService {

    private final PortfolioPhotoMapper portfolioPhotoMapper;
    private final PhotoService photoService;
    private final String publicBaseUrl;

    public PortfolioServiceImpl(
            PortfolioPhotoMapper portfolioPhotoMapper,
            PhotoService photoService,
            @Value("${app.public-base-url:http://localhost:8000}") String publicBaseUrl
    ) {
        this.portfolioPhotoMapper = portfolioPhotoMapper;
        this.photoService = photoService;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    @Override
    @Transactional
    public PortfolioVO createOwned(Long userId, PortfolioCreateRequest request) {
        List<Long> photoIds = validatePhotos(userId, request.photoIds(), request.coverPhotoId());
        LocalDateTime now = LocalDateTime.now();
        Portfolio portfolio = new Portfolio();
        portfolio.setUserId(userId);
        portfolio.setTitle(request.title().trim());
        portfolio.setSubtitle(blankToNull(request.subtitle()));
        portfolio.setDescription(blankToNull(request.description()));
        portfolio.setSourcePrompt(blankToNull(request.sourcePrompt()));
        portfolio.setStatus("ACTIVE");
        portfolio.setCoverPhotoId(request.coverPhotoId() == null ? photoIds.getFirst() : request.coverPhotoId());
        portfolio.setShareEnabled(0);
        portfolio.setCreateTime(now);
        portfolio.setUpdateTime(now);
        portfolio.setIsDelete(0);
        if (!save(portfolio)) throw new BusinessException(ErrorCode.SYSTEM_ERROR, "作品集创建失败");
        replacePhotos(userId, portfolio.getId(), photoIds);
        return toVO(portfolio);
    }

    @Override
    public List<PortfolioVO> listOwned(Long userId) {
        return lambdaQuery().eq(Portfolio::getUserId, userId)
                .orderByDesc(Portfolio::getUpdateTime).orderByDesc(Portfolio::getId)
                .list().stream().map(this::toVO).toList();
    }

    @Override
    public PortfolioVO getOwned(Long userId, Long portfolioId) {
        return toVO(requireOwned(userId, portfolioId));
    }

    @Override
    @Transactional
    public PortfolioVO updateOwned(Long userId, Long portfolioId, PortfolioUpdateRequest request) {
        Portfolio portfolio = requireOwned(userId, portfolioId);
        List<Long> photoIds = validatePhotos(userId, request.photoIds(), request.coverPhotoId());
        portfolio.setTitle(request.title().trim());
        portfolio.setSubtitle(blankToNull(request.subtitle()));
        portfolio.setDescription(blankToNull(request.description()));
        portfolio.setCoverPhotoId(request.coverPhotoId() == null ? photoIds.getFirst() : request.coverPhotoId());
        portfolio.setUpdateTime(LocalDateTime.now());
        if (!updateById(portfolio)) throw new BusinessException(ErrorCode.SYSTEM_ERROR, "作品集更新失败");
        replacePhotos(userId, portfolioId, photoIds);
        return toVO(portfolio);
    }

    @Override
    @Transactional
    public PortfolioVO enableSharing(Long userId, Long portfolioId) {
        Portfolio portfolio = requireOwned(userId, portfolioId);
        if (portfolio.getShareToken() == null || portfolio.getShareToken().isBlank()) {
            portfolio.setShareToken(UUID.randomUUID().toString().replace("-", ""));
        }
        portfolio.setShareEnabled(1);
        portfolio.setSharedAt(LocalDateTime.now());
        portfolio.setUpdateTime(LocalDateTime.now());
        if (!updateById(portfolio)) throw new BusinessException(ErrorCode.SYSTEM_ERROR, "无法开启作品集分享");
        return toVO(portfolio);
    }

    @Override
    @Transactional
    public PortfolioVO disableSharing(Long userId, Long portfolioId) {
        Portfolio portfolio = requireOwned(userId, portfolioId);
        portfolio.setShareEnabled(0);
        portfolio.setUpdateTime(LocalDateTime.now());
        if (!updateById(portfolio)) throw new BusinessException(ErrorCode.SYSTEM_ERROR, "无法关闭作品集分享");
        return toVO(portfolio);
    }

    @Override
    public PortfolioVO getShared(String shareToken) {
        PortfolioVO portfolio = toVO(requireShared(shareToken));
        List<PhotoVO> publicPhotos = portfolio.photos().stream()
                .map(photo -> toPublicPhoto(photo, shareToken))
                .toList();
        String publicCoverUrl = publicPhotos.stream()
                .filter(photo -> photo.id().equals(portfolio.coverPhotoId()))
                .findFirst().or(() -> publicPhotos.stream().findFirst())
                .map(PhotoVO::thumbnailUrl).orElse(null);
        return new PortfolioVO(
                portfolio.id(), portfolio.title(), portfolio.subtitle(), portfolio.description(),
                null, portfolio.status(), portfolio.coverPhotoId(), publicCoverUrl,
                true, shareToken, portfolio.shareUrl(), portfolio.sharedAt(),
                publicPhotos, portfolio.createTime(), portfolio.updateTime()
        );
    }

    @Override
    public SharedPhotoContent loadSharedPhotoContent(String shareToken, Long photoId, String variant) {
        Portfolio portfolio = requireShared(shareToken);
        Long matchingLinks = portfolioPhotoMapper.selectCount(new LambdaQueryWrapper<PortfolioPhoto>()
                .eq(PortfolioPhoto::getPortfolioId, portfolio.getId())
                .eq(PortfolioPhoto::getPhotoId, photoId));
        if (matchingLinks == 0) throw new BusinessException(ErrorCode.NOT_FOUND, "照片不属于这个公开作品集");
        if (!"original".equalsIgnoreCase(variant) && !"thumbnail".equalsIgnoreCase(variant)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的照片尺寸类型");
        }
        // Public shares use the web-safe thumbnail so original EXIF/GPS metadata is never exposed.
        PhotoService.PhotoContent content = photoService.loadContent(portfolio.getUserId(), photoId, "thumbnail");
        return new SharedPhotoContent(content.resource(), content.contentType());
    }

    @Override
    @Transactional
    public boolean deleteOwned(Long userId, Long portfolioId) {
        Portfolio portfolio = requireOwned(userId, portfolioId);
        if (!removeById(portfolio.getId())) throw new BusinessException(ErrorCode.SYSTEM_ERROR, "作品集删除失败");
        return true;
    }

    private Portfolio requireOwned(Long userId, Long portfolioId) {
        if (portfolioId == null || portfolioId <= 0) throw new BusinessException(ErrorCode.PARAMS_ERROR, "作品集 ID 无效");
        Portfolio portfolio = lambdaQuery().eq(Portfolio::getId, portfolioId).eq(Portfolio::getUserId, userId).one();
        if (portfolio == null) throw new BusinessException(ErrorCode.NOT_FOUND, "作品集不存在");
        return portfolio;
    }

    private Portfolio requireShared(String shareToken) {
        if (shareToken == null || !shareToken.matches("[a-fA-F0-9]{32}")) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "分享链接不存在或已关闭");
        }
        Portfolio portfolio = lambdaQuery()
                .eq(Portfolio::getShareToken, shareToken)
                .eq(Portfolio::getShareEnabled, 1)
                .one();
        if (portfolio == null) throw new BusinessException(ErrorCode.NOT_FOUND, "分享链接不存在或已关闭");
        return portfolio;
    }

    private List<Long> validatePhotos(Long userId, List<Long> ids, Long coverPhotoId) {
        List<Long> photoIds = new ArrayList<>(new LinkedHashSet<>(ids));
        if (photoIds.isEmpty() || photoIds.size() > 20) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "作品集必须包含 1 至 20 张照片");
        }
        for (Long photoId : photoIds) photoService.getOwned(userId, photoId);
        if (coverPhotoId != null && !photoIds.contains(coverPhotoId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "封面必须来自作品集中的照片");
        }
        return photoIds;
    }

    private void replacePhotos(Long userId, Long portfolioId, List<Long> photoIds) {
        portfolioPhotoMapper.delete(new LambdaQueryWrapper<PortfolioPhoto>()
                .eq(PortfolioPhoto::getPortfolioId, portfolioId));
        for (int index = 0; index < photoIds.size(); index++) {
            PortfolioPhoto link = new PortfolioPhoto();
            link.setUserId(userId);
            link.setPortfolioId(portfolioId);
            link.setPhotoId(photoIds.get(index));
            link.setSortOrder(index);
            link.setCreateTime(LocalDateTime.now());
            portfolioPhotoMapper.insert(link);
        }
    }

    private PortfolioVO toVO(Portfolio portfolio) {
        List<PortfolioPhoto> links = portfolioPhotoMapper.selectList(new LambdaQueryWrapper<PortfolioPhoto>()
                .eq(PortfolioPhoto::getPortfolioId, portfolio.getId())
                .orderByAsc(PortfolioPhoto::getSortOrder));
        List<PhotoVO> photos = new ArrayList<>();
        for (PortfolioPhoto link : links) {
            try {
                photos.add(photoService.getOwned(portfolio.getUserId(), link.getPhotoId()));
            } catch (BusinessException ignored) {
                // A deleted source photo is omitted while keeping the remaining portfolio usable.
            }
        }
        String coverUrl = photos.stream()
                .filter(photo -> photo.id().equals(portfolio.getCoverPhotoId()))
                .findFirst().or(() -> photos.stream().findFirst())
                .map(PhotoVO::thumbnailUrl).orElse(null);
        return new PortfolioVO(
                portfolio.getId(), portfolio.getTitle(), portfolio.getSubtitle(), portfolio.getDescription(),
                portfolio.getSourcePrompt(), portfolio.getStatus(), portfolio.getCoverPhotoId(), coverUrl,
                Integer.valueOf(1).equals(portfolio.getShareEnabled()), portfolio.getShareToken(),
                shareUrl(portfolio), portfolio.getSharedAt(),
                List.copyOf(photos), portfolio.getCreateTime(), portfolio.getUpdateTime()
        );
    }

    private String shareUrl(Portfolio portfolio) {
        if (!Integer.valueOf(1).equals(portfolio.getShareEnabled()) || portfolio.getShareToken() == null) return null;
        return publicBaseUrl + "/shared/" + portfolio.getShareToken();
    }

    private PhotoVO toPublicPhoto(PhotoVO photo, String shareToken) {
        String base = "/api/public/portfolios/" + shareToken + "/photos/" + photo.id() + "/content";
        return new PhotoVO(
                photo.id(), "Photograph " + photo.id(),
                base + "?variant=original", base + "?variant=thumbnail", "image/jpeg", null,
                photo.width(), photo.height(), null, null, photo.cameraModel(), null,
                null, null, null, null, photo.isoValue(), photo.takenAt(), photo.orientation(),
                null, null, null, null, null, null, photo.locationName(),
                null, null, null, photo.localizedAiDescription(), photo.aiTags(), null, null, null, null, null, null, null, null
        );
    }

    private String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }
}
