package com.example.usercenter.metadata;

import com.drew.imaging.ImageMetadataReader;
import com.drew.lang.GeoLocation;
import com.drew.lang.Rational;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Service
public class ExifPhotoMetadataService implements PhotoMetadataService {

    private static final DateTimeFormatter EXIF_DATE = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss");

    @Override
    public PhotoMetadata extract(InputStream inputStream) {
        try (InputStream input = inputStream) {
            Metadata metadata = ImageMetadataReader.readMetadata(input);
            ExifIFD0Directory root = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            ExifSubIFDDirectory exif = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
            GpsDirectory gps = metadata.getFirstDirectoryOfType(GpsDirectory.class);

            String cameraMake = stringValue(root, ExifIFD0Directory.TAG_MAKE);
            String cameraModel = stringValue(root, ExifIFD0Directory.TAG_MODEL);
            String lensModel = stringValue(exif, ExifSubIFDDirectory.TAG_LENS_MODEL);
            BigDecimal focalLength = rationalValue(exif, ExifSubIFDDirectory.TAG_FOCAL_LENGTH);
            Integer focalLength35mm = integerValue(exif, ExifSubIFDDirectory.TAG_35MM_FILM_EQUIV_FOCAL_LENGTH);
            BigDecimal aperture = rationalValue(exif, ExifSubIFDDirectory.TAG_FNUMBER);
            String shutterSpeed = shutterSpeed(exif);
            Integer isoValue = integerValue(exif, ExifSubIFDDirectory.TAG_ISO_EQUIVALENT);
            LocalDateTime takenAt = dateValue(exif, ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);
            Integer orientation = integerValue(root, ExifIFD0Directory.TAG_ORIENTATION);
            BigDecimal exposureBias = rationalValue(exif, ExifSubIFDDirectory.TAG_EXPOSURE_BIAS);
            String meteringMode = descriptionValue(exif, ExifSubIFDDirectory.TAG_METERING_MODE);
            String whiteBalance = descriptionValue(exif, ExifSubIFDDirectory.TAG_WHITE_BALANCE_MODE);
            String flashStatus = descriptionValue(exif, ExifSubIFDDirectory.TAG_FLASH);

            BigDecimal latitude = null;
            BigDecimal longitude = null;
            if (gps != null) {
                GeoLocation location = gps.getGeoLocation();
                if (location != null && !location.isZero()) {
                    latitude = coordinate(location.getLatitude());
                    longitude = coordinate(location.getLongitude());
                }
            }

            boolean hasMetadata = cameraMake != null || cameraModel != null || lensModel != null
                    || focalLength != null || aperture != null || shutterSpeed != null || isoValue != null
                    || takenAt != null || latitude != null || longitude != null;

            return new PhotoMetadata(
                    cameraMake,
                    cameraModel,
                    lensModel,
                    focalLength,
                    focalLength35mm,
                    aperture,
                    shutterSpeed,
                    isoValue,
                    takenAt,
                    normalizeOrientation(orientation),
                    exposureBias,
                    meteringMode,
                    whiteBalance,
                    flashStatus,
                    latitude,
                    longitude,
                    hasMetadata ? "EXTRACTED" : "MISSING",
                    null
            );
        } catch (Exception e) {
            return PhotoMetadata.failed("无法读取照片元数据");
        }
    }

    private String stringValue(com.drew.metadata.Directory directory, int tag) {
        if (directory == null) return null;
        String value = directory.getString(tag);
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private String descriptionValue(com.drew.metadata.Directory directory, int tag) {
        if (directory == null) return null;
        String value = directory.getDescription(tag);
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private Integer integerValue(com.drew.metadata.Directory directory, int tag) {
        return directory == null ? null : directory.getInteger(tag);
    }

    private BigDecimal rationalValue(com.drew.metadata.Directory directory, int tag) {
        if (directory == null) return null;
        Rational value = directory.getRational(tag);
        if (value == null) return null;
        return BigDecimal.valueOf(value.doubleValue()).stripTrailingZeros();
    }

    private LocalDateTime dateValue(com.drew.metadata.Directory directory, int tag) {
        String value = stringValue(directory, tag);
        if (value == null) return null;
        try {
            return LocalDateTime.parse(value, EXIF_DATE);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private String shutterSpeed(ExifSubIFDDirectory directory) {
        if (directory == null) return null;
        Rational value = directory.getRational(ExifSubIFDDirectory.TAG_EXPOSURE_TIME);
        if (value == null || value.doubleValue() <= 0) return null;
        double seconds = value.doubleValue();
        if (seconds < 1) {
            long denominator = Math.max(1, Math.round(1.0 / seconds));
            return "1/" + denominator + "s";
        }
        BigDecimal decimal = BigDecimal.valueOf(seconds).setScale(3, RoundingMode.HALF_UP).stripTrailingZeros();
        return decimal.toPlainString() + "s";
    }

    private BigDecimal coordinate(double coordinate) {
        return BigDecimal.valueOf(coordinate).setScale(7, RoundingMode.HALF_UP);
    }

    private int normalizeOrientation(Integer orientation) {
        return orientation != null && orientation >= 1 && orientation <= 8 ? orientation : 1;
    }
}
