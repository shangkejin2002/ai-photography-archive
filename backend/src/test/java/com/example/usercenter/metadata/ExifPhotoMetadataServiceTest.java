package com.example.usercenter.metadata;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ExifPhotoMetadataServiceTest {

    public static final String GPS_EXIF_JPEG = "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";

    private final PhotoMetadataService service = new ExifPhotoMetadataService();

    @Test
    void extractsCameraExposureLensAndGpsFromJpeg() {
        byte[] jpeg = Base64.getDecoder().decode(GPS_EXIF_JPEG);
        PhotoMetadata metadata = service.extract(new ByteArrayInputStream(jpeg));

        assertEquals("EXTRACTED", metadata.status());
        assertEquals("OLYMPUS CORPORATION", metadata.cameraMake());
        assertEquals("E-P7", metadata.cameraModel());
        assertEquals("LUMIX G VARIO 35-100/F4.0-5.6", metadata.lensModel());
        assertEquals(new BigDecimal("35"), metadata.focalLength());
        assertEquals(new BigDecimal("5.6"), metadata.aperture());
        assertEquals("1/640s", metadata.shutterSpeed());
        assertEquals(200, metadata.isoValue());
        assertEquals(LocalDateTime.of(2026, 9, 22, 10, 47, 30), metadata.takenAt());
        assertEquals(new BigDecimal("35.0395139"), metadata.gpsLatitude());
        assertEquals(new BigDecimal("135.7284417"), metadata.gpsLongitude());
        assertNotNull(metadata.meteringMode());
        assertNotNull(metadata.whiteBalance());
        assertNotNull(metadata.flashStatus());
    }
}
