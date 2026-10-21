package com.example.usercenter.service;

import com.example.usercenter.model.vo.AICreatePreviewVO;

public interface AICreateService {
    AICreatePreviewVO preview(Long userId, String prompt);
}
