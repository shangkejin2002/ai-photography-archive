package com.example.usercenter.exception;

import java.util.Map;

/** Maps pre-i18n detail text to stable API keys while preserving source compatibility. */
final class LegacyErrorKeys {
    private static final Map<String, String> KEYS = Map.ofEntries(
            Map.entry("用户不存在或已停用", "error.user.unavailable"),
            Map.entry("两次密码不一致", "error.password.mismatch"),
            Map.entry("账号已存在", "error.account.exists"),
            Map.entry("注册失败", "error.register.failed"),
            Map.entry("账号或密码错误", "error.login.invalid"),
            Map.entry("用户已停用", "error.user.disabled"),
            Map.entry("请选择照片文件", "error.photo.required"),
            Map.entry("照片不能超过 20MB", "error.photo.tooLarge"),
            Map.entry("照片像素尺寸过大", "error.photo.dimensions"),
            Map.entry("照片保存失败", "error.photo.save"),
            Map.entry("图片内容无效", "error.photo.invalidContent"),
            Map.entry("缩略图更新失败", "error.photo.thumbnail"),
            Map.entry("照片文件不存在", "error.photo.fileNotFound"),
            Map.entry("无法识别图片文件", "error.photo.unrecognized"),
            Map.entry("仅支持 JPEG 和 PNG 图片", "error.photo.type"),
            Map.entry("图片读取失败", "error.photo.read"),
            Map.entry("无效的照片存储路径", "error.photo.path"),
            Map.entry("照片记录创建失败", "error.photo.recordCreate"),
            Map.entry("照片记录更新失败", "error.photo.recordUpdate"),
            Map.entry("收藏状态更新失败", "error.photo.favorite"),
            Map.entry("地点名称更新失败", "error.photo.location"),
            Map.entry("无法提交 AI 分析", "error.photo.analysis"),
            Map.entry("照片删除失败", "error.photo.delete"),
            Map.entry("不支持的照片尺寸类型", "error.photo.variant"),
            Map.entry("照片 ID 无效", "error.photo.id"),
            Map.entry("照片不存在", "error.photo.notFound"),
            Map.entry("照片元数据更新失败", "error.photo.metadata"),
            Map.entry("作品集创建失败", "error.portfolio.create"),
            Map.entry("作品集更新失败", "error.portfolio.update"),
            Map.entry("无法开启作品集分享", "error.portfolio.shareEnable"),
            Map.entry("无法关闭作品集分享", "error.portfolio.shareDisable"),
            Map.entry("照片不属于这个公开作品集", "error.portfolio.photoNotShared"),
            Map.entry("作品集删除失败", "error.portfolio.delete"),
            Map.entry("作品集 ID 无效", "error.portfolio.id"),
            Map.entry("作品集不存在", "error.portfolio.notFound"),
            Map.entry("分享链接不存在或已关闭", "error.portfolio.shareNotFound"),
            Map.entry("作品集必须包含 1 至 20 张照片", "error.portfolio.photoCount"),
            Map.entry("封面必须来自作品集中的照片", "error.portfolio.cover"),
            Map.entry("PDF 中文字体资源缺失", "error.pdf.font"),
            Map.entry("生成 PDF 失败，请稍后重试", "error.pdf.generate")
    );

    private LegacyErrorKeys() {
    }

    static String resolve(String legacyDescription, String fallback) {
        return KEYS.getOrDefault(legacyDescription, fallback);
    }
}
