package com.blindwatermark.service;

import com.blindwatermark.common.AppLogger;
import com.blindwatermark.common.BusinessException;
import com.blindwatermark.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * 视频水印处理服务
 * 将视频拆分为帧，对每帧嵌入/提取水印后重新组装
 * 依赖系统已安装ffmpeg，属于高级功能（MP4支持优先级可降低）
 *
 * 处理流程:
 * 1. ffmpeg提取关键帧（每秒1帧）→ PNG图片
 * 2. 逐帧调用BlindWatermarkService嵌入/提取水印
 * 3. ffmpeg将水印帧重新编码为视频
 */
@Service
@RequiredArgsConstructor
public class VideoService {

    private final AppProperties properties;
    private final BlindWatermarkService watermarkService;

    /**
     * 检查系统是否已安装ffmpeg
     */
    public boolean isFfmpegAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder("ffmpeg", "-version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            AppLogger.warn("ffmpeg不可用: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 在视频中嵌入文字水印
     *
     * @param videoPath     视频文件路径
     * @param watermarkText 水印文本
     * @param strength      嵌入强度
     * @param password      可选密码
     * @return 嵌入水印后的视频文件路径
     */
    public String embedWatermarkInVideo(String videoPath, String watermarkText,
                                         int strength, String password) {
        checkFfmpeg();

        String tempDir = properties.getOutputDir() + "/temp/" + System.currentTimeMillis();
        new File(tempDir).mkdirs();
        String framesDir = tempDir + "/frames";
        new File(framesDir).mkdirs();

        try {
            extractFrames(videoPath, framesDir);

            File[] frameFiles = new File(framesDir).listFiles((dir, name) -> name.endsWith(".png"));
            if (frameFiles == null || frameFiles.length == 0) {
                throw new BusinessException(500, "视频帧提取失败");
            }

            String watermarkedFramesDir = tempDir + "/watermarked_frames";
            new File(watermarkedFramesDir).mkdirs();

            for (File frameFile : frameFiles) {
                BufferedImage frame = ImageIO.read(frameFile);
                if (frame != null) {
                    BufferedImage watermarked = watermarkService.embedTextWatermark(
                            frame, watermarkText, strength, password);
                    ImageIO.write(watermarked, "png",
                            new File(watermarkedFramesDir + "/" + frameFile.getName()));
                }
            }

            String outputPath = properties.getOutputDir() + "/videos/wm_" + System.currentTimeMillis() + ".mp4";
            assembleVideo(watermarkedFramesDir, videoPath, outputPath);

            AppLogger.info("视频水印嵌入完成: {}", outputPath);
            return outputPath;
        } catch (IOException e) {
            throw new BusinessException(500, "视频处理失败: " + e.getMessage());
        } finally {
            cn.hutool.core.io.FileUtil.del(tempDir);
        }
    }

    /**
     * 从视频中提取文字水印
     * 提取每帧的水印，返回第一个成功提取的结果
     */
    public String extractWatermarkFromVideo(String videoPath, String password) {
        checkFfmpeg();

        String tempDir = properties.getOutputDir() + "/temp/" + System.currentTimeMillis();
        new File(tempDir).mkdirs();
        String framesDir = tempDir + "/frames";
        new File(framesDir).mkdirs();

        try {
            extractFrames(videoPath, framesDir);

            File[] frameFiles = new File(framesDir).listFiles((dir, name) -> name.endsWith(".png"));
            if (frameFiles == null || frameFiles.length == 0) {
                throw new BusinessException(500, "视频帧提取失败");
            }

            for (File frameFile : frameFiles) {
                BufferedImage frame = ImageIO.read(frameFile);
                if (frame != null) {
                    String text = watermarkService.extractTextWatermark(frame, password);
                    if (text != null && !text.isEmpty()) {
                        AppLogger.info("视频水印提取成功: {}", text);
                        return text;
                    }
                }
            }
            return "";
        } catch (IOException e) {
            throw new BusinessException(500, "视频水印提取失败: " + e.getMessage());
        } finally {
            cn.hutool.core.io.FileUtil.del(tempDir);
        }
    }

    /** 检查ffmpeg是否可用，不可用则抛出异常 */
    private void checkFfmpeg() {
        if (!isFfmpegAvailable()) {
            throw new BusinessException(500, "视频处理需要ffmpeg，请确认系统已安装ffmpeg并加入PATH环境变量");
        }
    }

    /** 使用ffmpeg从视频中提取帧（每秒1帧） */
    private void extractFrames(String videoPath, String framesDir) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg", "-i", videoPath, "-vf", "fps=1", "-q:v", "2",
                framesDir + "/frame_%04d.png");
        pb.redirectErrorStream(true);
        Process process = pb.start();
        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("ffmpeg帧提取失败，退出码: " + exitCode);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("ffmpeg帧提取被中断");
        }
    }

    /** 使用ffmpeg将水印帧重新编码为视频 */
    private void assembleVideo(String framesDir, String originalVideo, String outputPath) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg", "-framerate", "1",
                "-i", framesDir + "/frame_%04d.png",
                "-i", originalVideo,
                "-map", "0:v", "-map", "1:a?",
                "-c:v", "libx264", "-pix_fmt", "yuv420p",
                "-shortest", outputPath);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("ffmpeg视频编码失败，退出码: " + exitCode);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("ffmpeg视频编码被中断");
        }
    }
}
