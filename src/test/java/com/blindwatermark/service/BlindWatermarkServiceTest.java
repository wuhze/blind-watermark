package com.blindwatermark.service;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.*;

class BlindWatermarkServiceTest {

    private static BlindWatermarkService service;

    @BeforeAll
    static void setUp() {
        service = new BlindWatermarkService();
    }

    private static BufferedImage createTestImage(int w, int h, int type) {
        BufferedImage img = new BufferedImage(w, h, type);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int r = (x * 7 + y * 3) % 256;
                int g = (x * 11 + y * 5) % 256;
                int b = (x * 13 + y * 7) % 256;
                img.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        return img;
    }

    private static void saveJpeg(BufferedImage image, File file, float quality) throws Exception {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        ImageWriter writer = writers.next();
        try {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            BufferedImage rgbImage = image;
            if (image.getType() != BufferedImage.TYPE_INT_RGB) {
                rgbImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
                rgbImage.getGraphics().drawImage(image, 0, 0, null);
            }
            try (OutputStream os = new FileOutputStream(file);
                 ImageOutputStream ios = ImageIO.createImageOutputStream(os)) {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(rgbImage, null, null), param);
            }
        } finally {
            writer.dispose();
        }
    }

    @Test
    void testEmbedExtract_PNG_InMemory() {
        BufferedImage src = createTestImage(256, 256, BufferedImage.TYPE_INT_RGB);
        String text = "hello";
        BufferedImage wm = service.embedTextWatermark(src, text, 50, null);
        String extracted = service.extractTextWatermark(wm, null);
        assertEquals(text, extracted);
    }

    @Test
    void testEmbedExtract_JPEG_Quality85() throws Exception {
        BufferedImage src = createTestImage(512, 512, BufferedImage.TYPE_INT_RGB);
        String text = "hello";
        BufferedImage wm = service.embedTextWatermark(src, text, 60, null);

        File tmp = File.createTempFile("wm_test_q85_", ".jpg");
        tmp.deleteOnExit();
        saveJpeg(wm, tmp, 0.85f);

        BufferedImage readBack = ImageIO.read(tmp);
        String extracted = service.extractTextWatermark(readBack, null);
        assertEquals(text, extracted);
    }

    @Test
    void testEmbedExtract_JPEG_Quality70() throws Exception {
        BufferedImage src = createTestImage(512, 512, BufferedImage.TYPE_INT_RGB);
        String text = "hello";
        BufferedImage wm = service.embedTextWatermark(src, text, 60, null);

        File tmp = File.createTempFile("wm_test_q70_", ".jpg");
        tmp.deleteOnExit();
        saveJpeg(wm, tmp, 0.70f);

        BufferedImage readBack = ImageIO.read(tmp);
        String extracted = service.extractTextWatermark(readBack, null);
        assertEquals(text, extracted);
    }

    @Test
    void testEmbedExtract_PNG_TypeIntARGB() {
        BufferedImage src = createTestImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        String text = "hello";
        BufferedImage wm = service.embedTextWatermark(src, text, 50, null);
        String extracted = service.extractTextWatermark(wm, null);
        assertEquals(text, extracted);
    }

    @Test
    void testEmbedExtract_PNG_Type3ByteBGR() {
        BufferedImage src = createTestImage(256, 256, BufferedImage.TYPE_3BYTE_BGR);
        String text = "hello";
        BufferedImage wm = service.embedTextWatermark(src, text, 50, null);
        String extracted = service.extractTextWatermark(wm, null);
        assertEquals(text, extracted);
    }

    @Test
    void testEmbedExtract_ChineseText() {
        BufferedImage src = createTestImage(256, 256, BufferedImage.TYPE_INT_RGB);
        String text = "测试水印";
        BufferedImage wm = service.embedTextWatermark(src, text, 60, null);
        String extracted = service.extractTextWatermark(wm, null);
        assertEquals(text, extracted);
    }

    @Test
    void testEmbedExtract_WithPassword() {
        BufferedImage src = createTestImage(256, 256, BufferedImage.TYPE_INT_RGB);
        String text = "secret";
        String password = "mypass";
        BufferedImage wm = service.embedTextWatermark(src, text, 60, password);
        String extracted = service.extractTextWatermark(wm, password);
        assertEquals(text, extracted);
    }

    @Test
    void testEmbedExtract_Chinese_JPEG_Quality85() throws Exception {
        BufferedImage src = createTestImage(512, 512, BufferedImage.TYPE_INT_RGB);
        String text = "测试水印";
        BufferedImage wm = service.embedTextWatermark(src, text, 60, null);

        File tmp = File.createTempFile("wm_test_cn_", ".jpg");
        tmp.deleteOnExit();
        saveJpeg(wm, tmp, 0.85f);

        BufferedImage readBack = ImageIO.read(tmp);
        String extracted = service.extractTextWatermark(readBack, null);
        assertEquals(text, extracted);
    }

    @Test
    void testEmbedExtract_SaveAndRead_PNG() throws Exception {
        BufferedImage src = createTestImage(256, 256, BufferedImage.TYPE_INT_RGB);
        String text = "hello";
        BufferedImage wm = service.embedTextWatermark(src, text, 50, null);

        File tmp = File.createTempFile("wm_test_", ".png");
        tmp.deleteOnExit();
        ImageIO.write(wm, "png", tmp);

        BufferedImage readBack = ImageIO.read(tmp);
        String extracted = service.extractTextWatermark(readBack, null);
        assertEquals(text, extracted);
    }

    @Test
    void sweep_JpegQuality_Threshold() throws Exception {
        float[] qualities = {0.50f, 0.55f, 0.60f, 0.65f, 0.70f, 0.75f, 0.80f, 0.85f, 0.88f, 0.90f, 0.92f, 0.95f};
        int[] strengths = {40, 50, 60, 70, 80};
        String[] texts = {"hi", "测试水印", "hello world 1234567890 abcdefghij"};

        BufferedImage src = createTestImage(512, 512, BufferedImage.TYPE_INT_RGB);

        System.out.println();
        System.out.println("===== JPEG Quality Sweep (512x512 image) =====");
        System.out.printf("%-10s %-10s %-8s %-6s%n", "Quality", "Strength", "TextLen", "Result");
        System.out.println("-".repeat(45));

        for (String text : texts) {
            for (int strength : strengths) {
                BufferedImage wm = service.embedTextWatermark(src, text, strength, null);

                for (float quality : qualities) {
                    File tmp = File.createTempFile("sweep_", ".jpg");
                    tmp.deleteOnExit();
                    saveJpeg(wm, tmp, quality);

                    BufferedImage readBack = ImageIO.read(tmp);
                    String extracted = service.extractTextWatermark(readBack, null);
                    boolean ok = text.equals(extracted);

                    System.out.printf("%-10.2f %-10d %-8d %-6s%n",
                            quality, strength, text.length(), ok ? "OK" : "FAIL");
                }
            }
        }
    }

    @Test
    void testWrongPassword_ReturnsEmpty() {
        BufferedImage src = createTestImage(256, 256, BufferedImage.TYPE_INT_RGB);
        String text = "secret";
        BufferedImage wm = service.embedTextWatermark(src, text, 60, "mypass");

        String extractedNoPwd = service.extractTextWatermark(wm, null);
        assertEquals("", extractedNoPwd, "空密码应提取失败");

        String extractedWrongPwd = service.extractTextWatermark(wm, "wrong");
        assertEquals("", extractedWrongPwd, "错误密码应提取失败");
    }

    @Test
    void testNoPassword_EmbedExtract_Correct() {
        BufferedImage src = createTestImage(256, 256, BufferedImage.TYPE_INT_RGB);
        String text = "nopwd";
        BufferedImage wm = service.embedTextWatermark(src, text, 50, null);

        String extracted = service.extractTextWatermark(wm, null);
        assertEquals(text, extracted);

        String extractedWithPwd = service.extractTextWatermark(wm, "wrong");
        assertEquals("", extractedWithPwd, "未加密图片不应被密码提取");
    }
}
