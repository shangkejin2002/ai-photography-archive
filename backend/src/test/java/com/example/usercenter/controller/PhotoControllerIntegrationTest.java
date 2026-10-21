package com.example.usercenter.controller;

import com.example.usercenter.analysis.DeepSeekPhotoDescriptionTranslator;
import com.example.usercenter.metadata.ExifPhotoMetadataServiceTest;
import com.example.usercenter.mapper.PhotoMapper;
import com.example.usercenter.model.entity.Photo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Base64;
import java.util.UUID;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:photo-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver"
})
@AutoConfigureMockMvc
class PhotoControllerIntegrationTest {

    private static final Path STORAGE_ROOT = Path.of(
            System.getProperty("java.io.tmpdir"),
            "user-center-photo-test-" + UUID.randomUUID()
    );

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("app.storage.root", () -> STORAGE_ROOT.toString());
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PhotoMapper photoMapper;

    @MockBean
    private DeepSeekPhotoDescriptionTranslator descriptionTranslator;

    @Test
    void completePhotoLibraryFlowAndEnforceOwnership() throws Exception {
        HttpSession firstUser = registerAndLogin("photo_owner");
        HttpSession secondUser = registerAndLogin("other_user");

        MockMultipartFile image = new MockMultipartFile(
                "file",
                "kyoto.png",
                "image/png",
                createPng()
        );

        MvcResult uploadResult = mockMvc.perform(multipart("/photo/upload")
                        .file(image)
                        .session((org.springframework.mock.web.MockHttpSession) firstUser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.originalFilename").value("kyoto.png"))
                .andExpect(jsonPath("$.data.width").value(1200))
                .andExpect(jsonPath("$.data.height").value(800))
                .andExpect(jsonPath("$.data.metadataStatus").value("MISSING"))
                .andReturn();

        JsonNode uploaded = objectMapper.readTree(uploadResult.getResponse().getContentAsString()).path("data");
        long photoId = uploaded.path("id").asLong();

        mockMvc.perform(get("/photo").session((org.springframework.mock.web.MockHttpSession) firstUser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(photoId));

        mockMvc.perform(get("/photo/{id}/content", photoId)
                        .param("variant", "thumbnail")
                        .session((org.springframework.mock.web.MockHttpSession) firstUser))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"));

        mockMvc.perform(get("/photo/{id}", photoId)
                        .session((org.springframework.mock.web.MockHttpSession) secondUser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40400));

        mockMvc.perform(patch("/photo/{id}/favorite", photoId)
                        .session((org.springframework.mock.web.MockHttpSession) firstUser)
                        .contentType("application/json")
                        .content("{\"favorite\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.favorite").value(true));

        mockMvc.perform(patch("/photo/{id}/location", photoId)
                        .session((org.springframework.mock.web.MockHttpSession) firstUser)
                        .contentType("application/json")
                        .content("{\"locationName\":\"Kyoto Studio\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.locationName").value("Kyoto Studio"));

        Photo photoWithExif = photoMapper.selectById(photoId);
        photoWithExif.setCameraMake("OLYMPUS CORPORATION");
        photoWithExif.setCameraModel("E-P7");
        photoWithExif.setLensModel("LUMIX G VARIO 35-100/F4.0-5.6");
        photoWithExif.setFocalLength(new BigDecimal("35"));
        photoWithExif.setFocalLength35mm(70);
        photoWithExif.setAperture(new BigDecimal("5.6"));
        photoWithExif.setShutterSpeed("1/500s");
        photoWithExif.setIsoValue(200);
        photoWithExif.setTakenAt(LocalDateTime.of(2026, 10, 2, 10, 47, 30));
        photoWithExif.setGpsLatitude(new BigDecimal("35.0395139"));
        photoWithExif.setGpsLongitude(new BigDecimal("135.7284417"));
        photoWithExif.setAiDescription("秋日光线落在京都的金色建筑与远山之间。这里是一段用于检验中文排版的摄影描述。");
        photoMapper.updateById(photoWithExif);

        when(descriptionTranslator.translate(anyMap())).thenReturn(Map.of(
                photoId, "秋の光が京都の金色の建物と遠くの山々を照らしています。"
        ));
        mockMvc.perform(post("/photo/localizations/ja")
                        .header("Accept-Language", "ja-JP")
                        .session((org.springframework.mock.web.MockHttpSession) firstUser)
                        .contentType("application/json")
                        .content("{\"photoIds\":[%d]}".formatted(photoId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].localizedAiDescription")
                        .value("秋の光が京都の金色の建物と遠くの山々を照らしています。"));
        mockMvc.perform(post("/photo/localizations/ja")
                        .header("Accept-Language", "ja-JP")
                        .session((org.springframework.mock.web.MockHttpSession) firstUser)
                        .contentType("application/json")
                        .content("{\"photoIds\":[%d]}".formatted(photoId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].localizedAiDescription").isNotEmpty());
        verify(descriptionTranslator, times(1)).translate(anyMap());

        MvcResult portfolioResult = mockMvc.perform(post("/portfolios")
                        .session((org.springframework.mock.web.MockHttpSession) firstUser)
                        .contentType("application/json")
                        .content("""
                                {"title":"京都影像集","subtitle":"一份安静的摄影记录","description":"光线、建筑与季节，共同构成这组私人摄影档案。","coverPhotoId":%d,"photoIds":[%d]}
                                """.formatted(photoId, photoId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.shareEnabled").value(false))
                .andExpect(jsonPath("$.data.photos.length()").value(1))
                .andExpect(jsonPath("$.data.photos[0].id").value(photoId))
                .andReturn();

        long portfolioId = objectMapper.readTree(portfolioResult.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        mockMvc.perform(post("/portfolios/{id}/share", portfolioId)
                        .session((org.springframework.mock.web.MockHttpSession) secondUser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40400));

        MvcResult shareResult = mockMvc.perform(post("/portfolios/{id}/share", portfolioId)
                        .session((org.springframework.mock.web.MockHttpSession) firstUser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.shareEnabled").value(true))
                .andExpect(jsonPath("$.data.shareToken").isNotEmpty())
                .andExpect(jsonPath("$.data.shareUrl").value(org.hamcrest.Matchers.startsWith("http://localhost:8000/shared/")))
                .andReturn();
        String shareToken = objectMapper.readTree(shareResult.getResponse().getContentAsString())
                .path("data").path("shareToken").asText();

        mockMvc.perform(get("/public/portfolios/{token}", shareToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(portfolioId))
                .andExpect(jsonPath("$.data.photos[0].id").value(photoId))
                .andExpect(jsonPath("$.data.photos[0].gpsLatitude").doesNotExist())
                .andExpect(jsonPath("$.data.photos[0].gpsLongitude").doesNotExist())
                .andExpect(jsonPath("$.data.photos[0].originalUrl").value(org.hamcrest.Matchers.containsString("/api/public/portfolios/")));

        mockMvc.perform(get("/public/portfolios/{token}/photos/{photoId}/content", shareToken, photoId)
                        .param("variant", "thumbnail"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"));

        MvcResult pdfResult = mockMvc.perform(get("/portfolios/{id}/pdf", portfolioId)
                        .session((org.springframework.mock.web.MockHttpSession) firstUser))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andReturn();
        byte[] pdf = pdfResult.getResponse().getContentAsByteArray();
        assertTrue(pdf.length > 1_000);
        assertEquals("%PDF", new String(pdf, 0, 4));
        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertEquals(3, document.getNumberOfPages());
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("PHOTOGRAPHY ARCHIVE"));
            assertTrue(text.contains("京都影像集"));
            assertTrue(text.contains("相机"));
            assertTrue(text.contains("ISO 200"));
            assertTrue(text.contains("继续浏览这个相册"));
            assertTrue(text.contains("http://localhost:8000/shared/"));
        }

        MvcResult japanesePdfResult = mockMvc.perform(get("/portfolios/{id}/pdf", portfolioId)
                        .header("Accept-Language", "ja-JP")
                        .session((org.springframework.mock.web.MockHttpSession) firstUser))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andReturn();
        try (PDDocument document = Loader.loadPDF(japanesePdfResult.getResponse().getContentAsByteArray())) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("カメラ"));
            assertTrue(text.contains("このアルバム"));
            assertTrue(text.contains("?lang=ja"));
            assertTrue(!text.contains("相机"));
        }
        String sampleOutput = System.getProperty("portfolio.pdf.sample-output");
        if (sampleOutput != null && !sampleOutput.isBlank()) {
            Path samplePath = Path.of(sampleOutput);
            Files.createDirectories(samplePath.getParent());
            Files.write(samplePath, pdf);
        }

        mockMvc.perform(get("/portfolios/{id}", portfolioId)
                        .session((org.springframework.mock.web.MockHttpSession) secondUser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40400));

        mockMvc.perform(get("/portfolios/{id}", portfolioId)
                        .header("Accept-Language", "ja-JP")
                        .session((org.springframework.mock.web.MockHttpSession) secondUser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40400))
                .andExpect(jsonPath("$.errorKey").value("error.portfolio.notFound"))
                .andExpect(jsonPath("$.description").value("Portfolio が見つかりません"));

        mockMvc.perform(delete("/portfolios/{id}/share", portfolioId)
                        .session((org.springframework.mock.web.MockHttpSession) firstUser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shareEnabled").value(false));

        mockMvc.perform(get("/public/portfolios/{token}", shareToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40400));

        mockMvc.perform(get("/insights")
                        .session((org.springframework.mock.web.MockHttpSession) firstUser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.totalPhotos").value(1))
                .andExpect(jsonPath("$.data.favoritePhotos").value(1))
                .andExpect(jsonPath("$.data.namedLocationPhotos").value(1))
                .andExpect(jsonPath("$.data.locations[0].label").value("Kyoto Studio"));

        mockMvc.perform(get("/insights")
                        .header("Accept-Language", "ja-JP")
                        .session((org.springframework.mock.web.MockHttpSession) secondUser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.totalPhotos").value(0))
                .andExpect(jsonPath("$.data.locations.length()").value(0))
                .andExpect(jsonPath("$.data.observation").value("写真をアップロードすると、撮影傾向が少しずつ表示されます。"));

        mockMvc.perform(delete("/photo/{id}", photoId)
                        .session((org.springframework.mock.web.MockHttpSession) firstUser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));

        mockMvc.perform(get("/photo").session((org.springframework.mock.web.MockHttpSession) firstUser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        MockMultipartFile gpsImage = new MockMultipartFile(
                "file",
                "kinkakuji.jpg",
                "image/jpeg",
                Base64.getDecoder().decode(ExifPhotoMetadataServiceTest.GPS_EXIF_JPEG)
        );

        MvcResult gpsUpload = mockMvc.perform(multipart("/photo/upload")
                        .file(gpsImage)
                        .session((org.springframework.mock.web.MockHttpSession) firstUser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.metadataStatus").value("EXTRACTED"))
                .andExpect(jsonPath("$.data.cameraModel").value("E-P7"))
                .andExpect(jsonPath("$.data.lensModel").value("LUMIX G VARIO 35-100/F4.0-5.6"))
                .andExpect(jsonPath("$.data.shutterSpeed").value("1/640s"))
                .andExpect(jsonPath("$.data.isoValue").value(200))
                .andExpect(jsonPath("$.data.gpsLatitude").value(35.0395139))
                .andExpect(jsonPath("$.data.gpsLongitude").value(135.7284417))
                .andReturn();

        long gpsPhotoId = objectMapper.readTree(gpsUpload.getResponse().getContentAsString())
                .path("data").path("id").asLong();
        mockMvc.perform(delete("/photo/{id}", gpsPhotoId)
                        .session((org.springframework.mock.web.MockHttpSession) firstUser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    void rejectsNonImagesAndOversizedFilesWithoutCreatingPhotoRecords() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        HttpSession user = registerAndLogin("invalid_" + suffix);

        MockMultipartFile disguisedText = new MockMultipartFile(
                "file", "not-a-photo.jpg", "image/jpeg", "plain text".getBytes()
        );
        mockMvc.perform(multipart("/photo/upload")
                        .file(disguisedText)
                        .header("Accept-Language", "ja-JP")
                        .session((org.springframework.mock.web.MockHttpSession) user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.errorKey").value("error.photo.type"))
                .andExpect(jsonPath("$.description").value("JPEG または PNG のみ対応しています"));

        MockMultipartFile oversized = new MockMultipartFile(
                "file", "too-large.jpg", "image/jpeg", new byte[20 * 1024 * 1024 + 1]
        );
        mockMvc.perform(multipart("/photo/upload")
                        .file(oversized)
                        .session((org.springframework.mock.web.MockHttpSession) user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.description").value("照片不能超过 20MB"));

        mockMvc.perform(get("/photo").session((org.springframework.mock.web.MockHttpSession) user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    private HttpSession registerAndLogin(String account) throws Exception {
        String password = "photo-test-password";
        mockMvc.perform(post("/user/register")
                        .contentType("application/json")
                        .content("""
                                {"userAccount":"%s","userPassword":"%s","checkPassword":"%s"}
                                """.formatted(account, password, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        MvcResult login = mockMvc.perform(post("/user/login")
                        .contentType("application/json")
                        .content("""
                                {"userAccount":"%s","userPassword":"%s"}
                                """.formatted(account, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return login.getRequest().getSession(false);
    }

    private byte[] createPng() throws Exception {
        BufferedImage image = new BufferedImage(1200, 800, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            for (int y = 0; y < 800; y++) {
                float ratio = y / 800f;
                graphics.setColor(new Color(
                        (int) (42 + 55 * ratio),
                        (int) (65 + 55 * ratio),
                        (int) (88 + 35 * ratio)
                ));
                graphics.drawLine(0, y, 1200, y);
            }
            graphics.setColor(new Color(31, 47, 48));
            graphics.fillPolygon(new int[]{0, 330, 590, 900, 1200}, new int[]{600, 300, 570, 250, 590}, 5);
            graphics.setColor(new Color(47, 67, 57));
            graphics.fillPolygon(new int[]{0, 250, 520, 840, 1200}, new int[]{680, 430, 650, 390, 680}, 5);
            graphics.setColor(new Color(189, 143, 52));
            graphics.fillRect(460, 380, 290, 230);
            graphics.setColor(new Color(79, 48, 30));
            graphics.fillPolygon(new int[]{410, 605, 800}, new int[]{400, 285, 400}, 3);
            graphics.setColor(new Color(14, 20, 20));
            graphics.fillRect(0, 680, 1200, 120);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    @AfterAll
    static void cleanStorage() throws Exception {
        if (!Files.exists(STORAGE_ROOT)) {
            return;
        }
        try (var paths = Files.walk(STORAGE_ROOT)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
