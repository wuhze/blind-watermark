package com.blindwatermark.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * 应用配置属性
 * 对应 application.yml 中的 blind-watermark 前缀
 * 管理文件存储路径、支持格式等全局配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "blind-watermark")
public class AppProperties {

    /** 授权服务器地址 */
    private String serverUrl = "http://localhost:9999";

    /** 开发模式: 跳过激活验证，方便本地开发调试 */
    private boolean devMode = false;

    /** 应用标题 */
    private String appTitle = "BlindWatermark";

    /** 版权作者 */
    private String copyrightAuthor = "玄陵";

    /** 版权起始年 */
    private int copyrightStartYear = 2026;

    /** 数据根目录（激活文件等存储于此） */
    private String dataDir = "./data";

    /** 水印输出目录 */
    private String outputDir = "./data/outputs";

    /** 支持的图片格式列表 */
    private String supportedImageFormats = "jpg,jpeg,png";

    /** 支持的视频格式列表 */
    private String supportedVideoFormats = "mp4";

    /** 默认水印强度 (1-100) */
    private int defaultStrength = 50;

    /** 应用版本号 */
    private String appVersion = "v1.0.0";

    /**
     * 应用启动时自动创建必要的目录结构
     */
    @PostConstruct
    public void init() {
        mkdir(dataDir);
        mkdir(outputDir);
        mkdir(outputDir + File.separator + "images");
        mkdir(outputDir + File.separator + "videos");
        mkdir(outputDir + File.separator + "batch");
    }

    /**
     * 安全创建目录，已存在则跳过
     */
    private void mkdir(String path) {
        File dir = new File(path);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }
}
