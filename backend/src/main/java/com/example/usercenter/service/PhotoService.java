package com.example.usercenter.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.usercenter.model.entity.Photo;
import com.example.usercenter.model.vo.PhotoVO;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface PhotoService extends IService<Photo> {
    PhotoVO upload(Long userId, MultipartFile file);

    List<PhotoVO> listOwned(Long userId, Boolean favorite);

    PhotoVO getOwned(Long userId, Long photoId);

    PhotoVO setFavorite(Long userId, Long photoId, boolean favorite);

    PhotoVO setLocationName(Long userId, Long photoId, String locationName);

    int reprocessMetadata(Long userId);

    PhotoVO requestAnalysis(Long userId, Long photoId);

    int requestPendingAnalyses(Long userId);

    List<PhotoVO> localizeJapaneseDescriptions(Long userId, List<Long> photoIds);

    boolean deleteOwned(Long userId, Long photoId);

    PhotoContent loadContent(Long userId, Long photoId, String variant);

    record PhotoContent(Resource resource, String contentType) {
    }
}
