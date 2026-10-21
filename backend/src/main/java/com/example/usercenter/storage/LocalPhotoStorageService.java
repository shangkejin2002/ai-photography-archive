package com.example.usercenter.storage;

import com.example.usercenter.common.ErrorCode;
import com.example.usercenter.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;

@Service
public class LocalPhotoStorageService implements PhotoStorageService {

    private static final int THUMBNAIL_MAX_EDGE = 1200;

    private final Path storageRoot;
    private final long maxFileSizeBytes;
    private final long maxImagePixels;

    public LocalPhotoStorageService(
            @Value("${app.storage.root}") String storageRoot,
            @Value("${app.storage.max-file-size-bytes}") long maxFileSizeBytes,
            @Value("${app.storage.max-image-pixels}") long maxImagePixels
    ) {
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
        this.maxFileSizeBytes = maxFileSizeBytes;
        this.maxImagePixels = maxImagePixels;
    }

    @Override
    public StoredPhoto store(Long userId, MultipartFile file, int orientation) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请选择照片文件");
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "照片不能超过 20MB");
        }

        ImageInfo imageInfo = inspectImage(file);
        long pixels = (long) imageInfo.image().getWidth() * imageInfo.image().getHeight();
        if (pixels > maxImagePixels) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "照片像素尺寸过大");
        }

        String fileId = UUID.randomUUID().toString();
        String userDirectory = "user-" + userId;
        String originalKey = userDirectory + "/originals/" + fileId + imageInfo.extension();
        String thumbnailKey = userDirectory + "/thumbnails/" + fileId + ".jpg";
        Path originalPath = resolveForWrite(originalKey);
        Path thumbnailPath = resolveForWrite(thumbnailKey);

        try {
            Files.createDirectories(originalPath.getParent());
            Files.createDirectories(thumbnailPath.getParent());
            writeOriginalAtomically(file, originalPath);
            writeThumbnailAtomically(imageInfo.image(), thumbnailPath, orientation);
        } catch (IOException e) {
            deleteQuietly(originalPath);
            deleteQuietly(thumbnailPath);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "照片保存失败");
        }

        String originalFilename = sanitizeOriginalFilename(file.getOriginalFilename());
        ImageDimensions dimensions = orientedDimensions(
                imageInfo.image().getWidth(),
                imageInfo.image().getHeight(),
                orientation
        );
        return new StoredPhoto(
                originalFilename,
                originalKey,
                thumbnailKey,
                imageInfo.contentType(),
                file.getSize(),
                dimensions.width(),
                dimensions.height()
        );
    }

    @Override
    public ImageDimensions refreshThumbnail(String originalStorageKey, String thumbnailStorageKey, int orientation) {
        Path originalPath = resolveForRead(originalStorageKey);
        Path thumbnailPath = resolveForWrite(thumbnailStorageKey);
        try {
            BufferedImage image = ImageIO.read(originalPath.toFile());
            if (image == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片内容无效");
            }
            Files.createDirectories(thumbnailPath.getParent());
            writeThumbnailAtomically(image, thumbnailPath, orientation);
            return orientedDimensions(image.getWidth(), image.getHeight(), orientation);
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "缩略图更新失败");
        }
    }

    @Override
    public Resource load(String storageKey) {
        Path path = resolveForRead(storageKey);
        Resource resource = new PathResource(path);
        if (!resource.exists() || !resource.isReadable()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "照片文件不存在");
        }
        return resource;
    }

    @Override
    public void delete(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(resolveForRead(storageKey));
        } catch (IOException ignored) {
            // 数据库逻辑删除优先；残留文件可由后续清理任务回收。
        }
    }

    private ImageInfo inspectImage(MultipartFile file) {
        try (InputStream input = file.getInputStream();
             ImageInputStream imageInput = ImageIO.createImageInputStream(input)) {
            if (imageInput == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "无法识别图片文件");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "仅支持 JPEG 和 PNG 图片");
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!format.equals("jpeg") && !format.equals("jpg") && !format.equals("png")) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "仅支持 JPEG 和 PNG 图片");
                }
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > maxImagePixels) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "照片像素尺寸过大");
                }
                BufferedImage image = reader.read(0);
                if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片内容无效");
                }
                boolean png = format.equals("png");
                return new ImageInfo(image, png ? ".png" : ".jpg", png ? "image/png" : "image/jpeg");
            } finally {
                reader.dispose();
            }
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片读取失败");
        }
    }

    private void writeOriginalAtomically(MultipartFile file, Path destination) throws IOException {
        Path temporary = Files.createTempFile(destination.getParent(), ".upload-", ".tmp");
        try (InputStream input = file.getInputStream()) {
            Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
            moveAtomically(temporary, destination);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void writeThumbnailAtomically(BufferedImage original, Path destination, int orientation) throws IOException {
        double scale = Math.min(1.0, (double) THUMBNAIL_MAX_EDGE /
                Math.max(original.getWidth(), original.getHeight()));
        int width = Math.max(1, (int) Math.round(original.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(original.getHeight() * scale));

        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = scaled.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(original, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }

        BufferedImage thumbnail = applyOrientation(scaled, orientation);
        Path temporary = Files.createTempFile(destination.getParent(), ".thumbnail-", ".tmp");
        try {
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
            if (!writers.hasNext()) {
                throw new IOException("JPEG writer is unavailable");
            }
            ImageWriter writer = writers.next();
            try (ImageOutputStream output = ImageIO.createImageOutputStream(temporary.toFile())) {
                writer.setOutput(output);
                ImageWriteParam params = writer.getDefaultWriteParam();
                if (params.canWriteCompressed()) {
                    params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    params.setCompressionQuality(0.86f);
                }
                writer.write(null, new IIOImage(thumbnail, null, null), params);
            } finally {
                writer.dispose();
            }
            moveAtomically(temporary, destination);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private BufferedImage applyOrientation(BufferedImage source, int orientation) {
        if (orientation <= 1 || orientation > 8) {
            return source;
        }
        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();
        boolean swapsDimensions = orientation >= 5;
        int targetWidth = swapsDimensions ? sourceHeight : sourceWidth;
        int targetHeight = swapsDimensions ? sourceWidth : sourceHeight;
        BufferedImage target = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < sourceHeight; y++) {
            for (int x = 0; x < sourceWidth; x++) {
                int targetX;
                int targetY;
                switch (orientation) {
                    case 2 -> { targetX = sourceWidth - 1 - x; targetY = y; }
                    case 3 -> { targetX = sourceWidth - 1 - x; targetY = sourceHeight - 1 - y; }
                    case 4 -> { targetX = x; targetY = sourceHeight - 1 - y; }
                    case 5 -> { targetX = y; targetY = x; }
                    case 6 -> { targetX = sourceHeight - 1 - y; targetY = x; }
                    case 7 -> { targetX = sourceHeight - 1 - y; targetY = sourceWidth - 1 - x; }
                    case 8 -> { targetX = y; targetY = sourceWidth - 1 - x; }
                    default -> { targetX = x; targetY = y; }
                }
                target.setRGB(targetX, targetY, source.getRGB(x, y));
            }
        }
        return target;
    }

    private ImageDimensions orientedDimensions(int width, int height, int orientation) {
        return orientation >= 5 && orientation <= 8
                ? new ImageDimensions(height, width)
                : new ImageDimensions(width, height);
    }

    private void moveAtomically(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Path resolveForWrite(String storageKey) {
        Path path = storageRoot.resolve(storageKey).normalize();
        if (!path.startsWith(storageRoot)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "无效的照片存储路径");
        }
        return path;
    }

    private Path resolveForRead(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "照片文件不存在");
        }
        return resolveForWrite(storageKey);
    }

    private String sanitizeOriginalFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "photo";
        }
        String normalized = filename.replace('\\', '/');
        String basename = normalized.substring(normalized.lastIndexOf('/') + 1);
        String sanitized = basename.replaceAll("[\\r\\n\\t]", "_");
        if (sanitized.isBlank()) {
            return "photo";
        }
        return sanitized.length() > 512 ? sanitized.substring(0, 512) : sanitized;
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    private record ImageInfo(BufferedImage image, String extension, String contentType) {
    }
}
