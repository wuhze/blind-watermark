package com.blindwatermark.service;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import com.blindwatermark.common.AppLogger;
import com.blindwatermark.common.BusinessException;
import com.blindwatermark.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * 文件处理服务
 * 负责图片文件的读取、保存、格式校验等
 */
@Service
@RequiredArgsConstructor
public class FileService {

    private final AppProperties properties;

    /** 支持的图片格式扩展名 */
    private static final List<String> IMAGE_FORMATS = Arrays.asList("jpg", "jpeg", "png");

    /** 支持的视频格式扩展名 */
    private static final List<String> VIDEO_FORMATS = Arrays.asList("mp4");

    /**
     * 读取图片文件为BufferedImage
     *
     * @param file 图片文件
     * @return BufferedImage对象
     * @throws BusinessException 文件不存在或格式不支持时抛出
     */
    public BufferedImage readImage(File file) {
        try {
            ImageIO.setUseCache(false);
            BufferedImage image = ImageIO.read(file);
            if (image == null) {
                throw new BusinessException(400, "无法解析图片文件，格式可能不支持: " + file.getName());
            }
            return image;
        } catch (IOException e) {
            throw new BusinessException(500, "读取图片失败: " + e.getMessage());
        }
    }

    /**
     * 保存BufferedImage到文件
     * JPEG格式使用0.85质量保存，配合增强的gap/冗余参数保证水印可提取
     *
     * @param image  BufferedImage对象
     * @param format 输出格式 (jpg/png)
     * @param subDir 输出子目录
     * @param prefix 文件名前缀
     * @return 保存后的文件对象
     */
    public File saveImage(BufferedImage image, String format, String subDir, String prefix) {
        return saveImage(image, format, subDir, prefix, null);
    }

    public File saveImage(BufferedImage image, String format, String subDir, String prefix, String overrideDir) {
        String uniqueName = prefix + "_" + IdUtil.fastSimpleUUID() + "." + format;
        String baseDir = (overrideDir != null && !overrideDir.isEmpty()) ? overrideDir : properties.getOutputDir();
        String targetPath = baseDir + File.separator + subDir + File.separator + uniqueName;
        File targetFile = new File(targetPath);
        FileUtil.mkParentDirs(targetFile);

        if ("jpg".equalsIgnoreCase(format) || "jpeg".equalsIgnoreCase(format)) {
            saveJpegWithQuality(image, targetFile, 0.85f);
        } else {
            try {
                ImageIO.write(image, format, targetFile);
            } catch (IOException e) {
                throw new BusinessException(500, "保存图片失败: " + e.getMessage());
            }
        }
        AppLogger.info("图片保存成功: {} (格式={}, 大小={})", targetPath, format, targetFile.length());
        return targetFile;
    }

    public File saveImageAs(BufferedImage image, String fileName, String subDir, String overrideDir) {
        String baseDir = (overrideDir != null && !overrideDir.isEmpty()) ? overrideDir : properties.getOutputDir();
        String targetPath = baseDir + File.separator + subDir + File.separator + fileName;
        File targetFile = new File(targetPath);
        FileUtil.mkParentDirs(targetFile);

        String format = getExtension(fileName);
        if ("jpg".equalsIgnoreCase(format) || "jpeg".equalsIgnoreCase(format)) {
            saveJpegWithQuality(image, targetFile, 0.85f);
        } else {
            try {
                ImageIO.write(image, format, targetFile);
            } catch (IOException e) {
                throw new BusinessException(500, "保存图片失败: " + e.getMessage());
            }
        }
        AppLogger.info("图片保存成功: {} (大小={})", targetPath, targetFile.length());
        return targetFile;
    }

    /**
     * 以指定JPEG质量保存图片
     * quality=0.95可在保留水印信息的同时控制文件大小
     *
     * @param image   图像对象
     * @param file    输出文件
     * @param quality JPEG压缩质量 (0.0~1.0)
     */
    public void saveJpegWithQuality(BufferedImage image, File file, float quality) {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new BusinessException(500, "未找到JPEG编码器");
        }
        ImageWriter writer = writers.next();
        try {
            ImageIO.setUseCache(false);
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
        } catch (IOException e) {
            throw new BusinessException(500, "保存JPEG失败: " + e.getMessage());
        } finally {
            writer.dispose();
        }
    }

    /**
     * 校验文件格式是否受支持
     *
     * @param fileName 文件名
     * @return 文件类型: "IMAGE" 或 "VIDEO"
     * @throws BusinessException 格式不支持时抛出
     */
    public String validateFileFormat(String fileName) {
        String ext = getExtension(fileName).toLowerCase();
        if (IMAGE_FORMATS.contains(ext)) return "IMAGE";
        if (VIDEO_FORMATS.contains(ext)) return "VIDEO";
        throw new BusinessException(400, "不支持的文件格式: " + ext);
    }

    /** 获取文件扩展名（小写） */
    public String getExtension(String fileName) {
        return FileUtil.extName(fileName).toLowerCase();
    }
}
