package com.blindwatermark;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot配置类
 * 不启动Web服务器，仅提供IoC容器管理Service/DAO等Bean
 * 扫描com.blindwatermark下所有组件
 */
@SpringBootApplication
public class BlindWatermarkGuardConfig {
}
