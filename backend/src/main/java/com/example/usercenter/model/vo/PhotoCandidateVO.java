package com.example.usercenter.model.vo;

import java.util.List;

public record PhotoCandidateVO(PhotoVO photo, Integer score, List<String> matchReasons) {
}
