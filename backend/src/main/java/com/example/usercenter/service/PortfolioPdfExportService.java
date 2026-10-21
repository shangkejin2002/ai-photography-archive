package com.example.usercenter.service;

import com.example.usercenter.common.ErrorCode;
import com.example.usercenter.exception.BusinessException;
import com.example.usercenter.i18n.AppMessages;
import com.example.usercenter.model.vo.PhotoVO;
import com.example.usercenter.model.vo.PortfolioVO;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.core.io.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@Slf4j
public class PortfolioPdfExportService {

    private static final String FONT_RESOURCE = "/fonts/NotoSansSC-VF.ttf";
    private static final PDRectangle PAGE_SIZE = PDRectangle.A4;
    private static final Color BACKGROUND = new Color(13, 14, 15);
    private static final Color PANEL = new Color(24, 25, 26);
    private static final Color TEXT = new Color(239, 238, 233);
    private static final Color MUTED = new Color(146, 147, 143);
    private static final Color FAINT = new Color(90, 91, 88);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy.MM.dd  HH:mm");

    private final PortfolioService portfolioService;
    private final PhotoService photoService;
    private final String publicBaseUrl;
    private final AppMessages messages;

    public PortfolioPdfExportService(
            PortfolioService portfolioService,
            PhotoService photoService,
            AppMessages messages,
            @Value("${app.public-base-url:http://localhost:8000}") String publicBaseUrl
    ) {
        this.portfolioService = portfolioService;
        this.photoService = photoService;
        this.messages = messages;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    public byte[] exportOwned(Long userId, Long portfolioId) {
        PortfolioVO portfolio = portfolioService.getOwned(userId, portfolioId);
        try (PDDocument document = new PDDocument();
             InputStream fontStream = getClass().getResourceAsStream(FONT_RESOURCE)) {
            if (fontStream == null) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "PDF 中文字体资源缺失");
            }
            PDFont font = PDType0Font.load(document, fontStream, true);
            List<PhotoAsset> assets = loadAssets(userId, portfolio.photos());
            drawCover(document, font, portfolio, coverAsset(portfolio.coverPhotoId(), assets));
            for (int index = 0; index < assets.size(); index += 1) {
                drawPhotoPage(document, font, assets.get(index), index, assets.size());
            }
            drawPromotionPage(document, font, portfolio);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            log.error("Failed to export portfolio {} as PDF", portfolioId, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成 PDF 失败，请稍后重试");
        }
    }

    private void drawPromotionPage(PDDocument document, PDFont font, PortfolioVO portfolio) throws IOException {
        boolean shared = Boolean.TRUE.equals(portfolio.shareEnabled()) && hasText(portfolio.shareUrl());
        String destination = (shared ? portfolio.shareUrl() : publicBaseUrl + "/user/register")
                + (messages.isJapanese() ? "?lang=ja" : "?lang=zh");
        String eyebrow = shared ? "CONTINUE THE STORY" : "CREATE YOUR OWN ARCHIVE";
        String title = messages.get(shared ? "pdf.promotion.sharedTitle" : "pdf.promotion.createTitle");
        String description = messages.get(shared ? "pdf.promotion.sharedDescription" : "pdf.promotion.createDescription");

        PDPage page = new PDPage(PAGE_SIZE);
        document.addPage(page);
        try (PDPageContentStream canvas = new PDPageContentStream(document, page)) {
            fillPage(canvas, BACKGROUND);
            drawText(canvas, font, 9, "PHOTOGRAPHY ARCHIVE", 42, 800, TEXT);
            drawText(canvas, font, 8, eyebrow, 42, 706, MUTED);
            drawText(canvas, font, 30, title, 42, 657, TEXT);
            float descriptionY = 610;
            for (String line : wrap(font, 12, description, 500, 3)) {
                drawText(canvas, font, 12, line, 42, descriptionY, MUTED);
                descriptionY -= 21;
            }

            BufferedImage qrCode = createQrCode(destination, 520);
            PDImageXObject qrImage = LosslessFactory.createFromImage(document, qrCode);
            canvas.setNonStrokingColor(TEXT);
            canvas.addRect(42, 285, 244, 244);
            canvas.fill();
            canvas.drawImage(qrImage, 54, 297, 220, 220);

            drawText(canvas, font, 8, shared ? "PUBLIC ALBUM LINK" : "WEBSITE", 318, 501, MUTED);
            List<String> urlLines = wrap(font, 11, destination, 235, 5);
            float urlY = 476;
            for (String line : urlLines) {
                drawText(canvas, font, 11, line, 318, urlY, TEXT);
                urlY -= 18;
            }
            drawText(canvas, font, 8, messages.get("pdf.scan"), 318, 332, MUTED);
            drawText(canvas, font, 7, "Generated by Photography Archive", 42, 34, FAINT);
        }
    }

    private BufferedImage createQrCode(String value, int size) throws IOException {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, size, size);
            BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_BYTE_BINARY);
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    image.setRGB(x, y, matrix.get(x, y) ? Color.BLACK.getRGB() : Color.WHITE.getRGB());
                }
            }
            return image;
        } catch (WriterException e) {
            throw new IOException("无法生成分享二维码", e);
        }
    }

    private List<PhotoAsset> loadAssets(Long userId, List<PhotoVO> photos) throws IOException {
        List<PhotoAsset> assets = new ArrayList<>();
        for (PhotoVO photo : photos) {
            PhotoService.PhotoContent content = photoService.loadContent(userId, photo.id(), "thumbnail");
            Resource resource = content.resource();
            try (InputStream input = resource.getInputStream()) {
                assets.add(new PhotoAsset(photo, input.readAllBytes()));
            }
        }
        return assets;
    }

    private PhotoAsset coverAsset(Long coverPhotoId, List<PhotoAsset> assets) {
        if (assets.isEmpty()) return null;
        if (coverPhotoId != null) {
            for (PhotoAsset asset : assets) {
                if (coverPhotoId.equals(asset.photo().id())) return asset;
            }
        }
        return assets.getFirst();
    }

    private void drawCover(
            PDDocument document,
            PDFont font,
            PortfolioVO portfolio,
            PhotoAsset cover
    ) throws IOException {
        PDPage page = new PDPage(PAGE_SIZE);
        document.addPage(page);
        try (PDPageContentStream canvas = new PDPageContentStream(document, page)) {
            fillPage(canvas, BACKGROUND);
            drawText(canvas, font, 10, "PHOTOGRAPHY ARCHIVE", 42, 799, TEXT);
            drawText(canvas, font, 8, "PERSONAL PHOTOGRAPHY PORTFOLIO", 42, 754, MUTED);
            drawText(canvas, font, 8,
                    String.format(Locale.ROOT, "%02d PHOTOGRAPHS", portfolio.photos().size()),
                    472, 754, MUTED);

            float titleY = 695;
            List<String> titleLines = wrap(font, 35, valueOr(portfolio.title(), messages.get("pdf.untitledPortfolio")), 505, 3);
            for (String line : titleLines) {
                drawText(canvas, font, 35, line, 42, titleY, TEXT);
                titleY -= 47;
            }

            if (hasText(portfolio.subtitle())) {
                List<String> subtitleLines = wrap(font, 14, portfolio.subtitle(), 500, 2);
                for (String line : subtitleLines) {
                    drawText(canvas, font, 14, line, 43, titleY - 3, MUTED);
                    titleY -= 22;
                }
            }

            if (cover != null) {
                PDImageXObject image = PDImageXObject.createFromByteArray(document, cover.bytes(), "cover");
                drawImageContained(canvas, image, 42, 80, 511, 365);
            } else {
                canvas.setNonStrokingColor(PANEL);
                canvas.addRect(42, 80, 511, 365);
                canvas.fill();
            }

            if (hasText(portfolio.description())) {
                float descriptionY = 58;
                String description = wrap(font, 9, portfolio.description(), 500, 1).getFirst();
                drawText(canvas, font, 9, description, 42, descriptionY, MUTED);
            }
            drawText(canvas, font, 7, "Generated by Photography Archive", 422, 32, FAINT);
        }
    }

    private void drawPhotoPage(
            PDDocument document,
            PDFont font,
            PhotoAsset asset,
            int index,
            int total
    ) throws IOException {
        PhotoVO photo = asset.photo();
        PDPage page = new PDPage(PAGE_SIZE);
        document.addPage(page);
        try (PDPageContentStream canvas = new PDPageContentStream(document, page)) {
            fillPage(canvas, BACKGROUND);
            drawText(canvas, font, 8, "PHOTOGRAPHY ARCHIVE", 42, 804, TEXT);
            drawText(canvas, font, 8,
                    String.format(Locale.ROOT, "%02d / %02d", index + 1, total),
                    506, 804, MUTED);

            String localizedDescription = localizedDescription(photo);
            String heading = firstText(photo.locationName(), localizedDescription, photo.originalFilename());
            drawText(canvas, font, 16, ellipsize(font, 16, heading, 500), 42, 766, TEXT);

            PDImageXObject image = PDImageXObject.createFromByteArray(document, asset.bytes(), "photo-" + photo.id());
            canvas.setNonStrokingColor(PANEL);
            canvas.addRect(42, 250, 511, 490);
            canvas.fill();
            drawImageContained(canvas, image, 42, 250, 511, 490);

            float left = 42;
            float right = 304;
            drawMeta(canvas, font, messages.get("pdf.camera"), cameraName(photo), left, 215, 230);
            drawMeta(canvas, font, messages.get("pdf.lens"), valueOr(photo.lensModel(), "—"), right, 215, 249);
            drawMeta(canvas, font, messages.get("pdf.focal"), focalLength(photo), left, 169, 230);
            drawMeta(canvas, font, messages.get("pdf.exposure"), exposure(photo), right, 169, 249);
            drawMeta(canvas, font, messages.get("pdf.takenAt"), takenAt(photo), left, 123, 230);
            drawMeta(canvas, font, messages.get("pdf.location"), location(photo), right, 123, 249);

            if (hasText(localizedDescription)) {
                drawText(canvas, font, 7, "AI DESCRIPTION", 42, 76, MUTED);
                List<String> descriptionLines = wrap(font, 9, localizedDescription, 510, 2);
                float y = 58;
                for (String line : descriptionLines) {
                    drawText(canvas, font, 9, line, 42, y, TEXT);
                    y -= 14;
                }
            } else {
                drawText(canvas, font, 7, "ORIGINAL FILE", 42, 76, MUTED);
                drawText(canvas, font, 9,
                        ellipsize(font, 9, valueOr(photo.originalFilename(), "—"), 500),
                        42, 58, TEXT);
            }
        }
    }

    private void drawMeta(
            PDPageContentStream canvas,
            PDFont font,
            String label,
            String value,
            float x,
            float y,
            float maxWidth
    ) throws IOException {
        drawText(canvas, font, 7, label, x, y, MUTED);
        drawText(canvas, font, 10, ellipsize(font, 10, value, maxWidth), x, y - 18, TEXT);
    }

    private void fillPage(PDPageContentStream canvas, Color color) throws IOException {
        canvas.setNonStrokingColor(color);
        canvas.addRect(0, 0, PAGE_SIZE.getWidth(), PAGE_SIZE.getHeight());
        canvas.fill();
    }

    private void drawText(
            PDPageContentStream canvas,
            PDFont font,
            float fontSize,
            String text,
            float x,
            float y,
            Color color
    ) throws IOException {
        canvas.beginText();
        canvas.setFont(font, fontSize);
        canvas.setNonStrokingColor(color);
        canvas.newLineAtOffset(x, y);
        canvas.showText(encodableText(font, text));
        canvas.endText();
    }

    private void drawImageContained(
            PDPageContentStream canvas,
            PDImageXObject image,
            float x,
            float y,
            float width,
            float height
    ) throws IOException {
        float scale = Math.min(width / image.getWidth(), height / image.getHeight());
        float drawWidth = image.getWidth() * scale;
        float drawHeight = image.getHeight() * scale;
        float drawX = x + (width - drawWidth) / 2;
        float drawY = y + (height - drawHeight) / 2;
        canvas.drawImage(image, drawX, drawY, drawWidth, drawHeight);
    }

    private List<String> wrap(PDFont font, float fontSize, String text, float maxWidth, int maxLines)
            throws IOException {
        String normalized = safeText(text).replaceAll("\\s+", " ").trim();
        if (normalized.isEmpty()) return List.of("—");
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int[] codePoints = normalized.codePoints().toArray();
        for (int codePoint : codePoints) {
            String character = new String(Character.toChars(codePoint));
            String candidate = current + character;
            if (textWidth(font, fontSize, candidate) <= maxWidth || current.isEmpty()) {
                current.append(character);
                continue;
            }
            lines.add(current.toString().strip());
            current = new StringBuilder(character.stripLeading());
            if (lines.size() == maxLines) break;
        }
        if (lines.size() < maxLines && !current.isEmpty()) lines.add(current.toString().strip());
        if (lines.size() > maxLines) lines = new ArrayList<>(lines.subList(0, maxLines));
        int consumed = lines.stream().mapToInt(line -> line.codePointCount(0, line.length())).sum();
        if (consumed < normalized.codePointCount(0, normalized.length()) && !lines.isEmpty()) {
            int last = lines.size() - 1;
            lines.set(last, ellipsize(font, fontSize, lines.get(last) + "…", maxWidth));
        }
        return lines;
    }

    private String ellipsize(PDFont font, float fontSize, String text, float maxWidth) throws IOException {
        String normalized = safeText(text).replaceAll("\\s+", " ").trim();
        if (textWidth(font, fontSize, normalized) <= maxWidth) return normalized;
        String suffix = "…";
        StringBuilder result = new StringBuilder();
        for (int codePoint : normalized.codePoints().toArray()) {
            String next = result + new String(Character.toChars(codePoint)) + suffix;
            if (textWidth(font, fontSize, next) > maxWidth) break;
            result.appendCodePoint(codePoint);
        }
        return result + suffix;
    }

    private float textWidth(PDFont font, float fontSize, String text) throws IOException {
        return font.getStringWidth(encodableText(font, text)) / 1000f * fontSize;
    }

    private String cameraName(PhotoVO photo) {
        String make = valueOr(photo.cameraMake(), "");
        String model = valueOr(photo.cameraModel(), "");
        String joined = (make + " " + model).trim();
        return joined.isEmpty() ? "—" : joined;
    }

    private String focalLength(PhotoVO photo) {
        String focal = photo.focalLength() == null ? "—" : decimal(photo.focalLength()) + " mm";
        if (photo.focalLength35mm() != null) focal += "  ·  " + messages.get("pdf.equivalent") + " " + photo.focalLength35mm() + " mm";
        return focal;
    }

    private String exposure(PhotoVO photo) {
        List<String> values = new ArrayList<>();
        if (hasText(photo.shutterSpeed())) values.add(photo.shutterSpeed());
        if (photo.aperture() != null) values.add("f/" + decimal(photo.aperture()));
        if (photo.isoValue() != null) values.add("ISO " + photo.isoValue());
        return values.isEmpty() ? "—" : String.join("  ·  ", values);
    }

    private String takenAt(PhotoVO photo) {
        return photo.takenAt() == null ? "—" : DATE_TIME.format(photo.takenAt());
    }

    private String location(PhotoVO photo) {
        if (hasText(photo.locationName())) return photo.locationName();
        if (photo.gpsLatitude() != null && photo.gpsLongitude() != null) {
            return decimal(photo.gpsLatitude()) + ", " + decimal(photo.gpsLongitude());
        }
        return "—";
    }

    private String decimal(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private String firstText(String... values) {
        for (String value : values) if (hasText(value)) return value;
        return messages.get("pdf.untitledPhoto");
    }

    private String valueOr(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String safeText(String value) {
        if (value == null) return "";
        return value.replace('\u0000', ' ').replace('\r', ' ').replace('\n', ' ');
    }

    private String encodableText(PDFont font, String value) throws IOException {
        StringBuilder result = new StringBuilder();
        for (int codePoint : safeText(value).codePoints().toArray()) {
            String character = new String(Character.toChars(codePoint));
            try {
                font.encode(character);
                result.append(character);
            } catch (IllegalArgumentException | IOException unsupported) {
                if (!Character.isISOControl(codePoint)) result.append('□');
            }
        }
        return result.toString();
    }

    private String localizedDescription(PhotoVO photo) {
        if (!messages.isJapanese()) return photo.aiDescription();
        return photo.localizedAiDescription();
    }

    private record PhotoAsset(PhotoVO photo, byte[] bytes) {
    }
}
