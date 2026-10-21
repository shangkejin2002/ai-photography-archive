package com.example.usercenter.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.usercenter.model.dto.portfolio.PortfolioCreateRequest;
import com.example.usercenter.model.dto.portfolio.PortfolioUpdateRequest;
import com.example.usercenter.model.entity.Portfolio;
import com.example.usercenter.model.vo.PortfolioVO;
import org.springframework.core.io.Resource;

import java.util.List;

public interface PortfolioService extends IService<Portfolio> {
    PortfolioVO createOwned(Long userId, PortfolioCreateRequest request);
    List<PortfolioVO> listOwned(Long userId);
    PortfolioVO getOwned(Long userId, Long portfolioId);
    PortfolioVO updateOwned(Long userId, Long portfolioId, PortfolioUpdateRequest request);
    PortfolioVO enableSharing(Long userId, Long portfolioId);
    PortfolioVO disableSharing(Long userId, Long portfolioId);
    PortfolioVO getShared(String shareToken);
    SharedPhotoContent loadSharedPhotoContent(String shareToken, Long photoId, String variant);
    boolean deleteOwned(Long userId, Long portfolioId);

    record SharedPhotoContent(Resource resource, String contentType) {
    }
}
