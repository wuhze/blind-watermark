package com.blindwatermark.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 应用日志工具
 * 统一封装SLF4J日志调用，所有日志同时输出到控制台和本地文件
 * 日志文件位于 ./logs/ 目录下，按日期滚动
 */
public class AppLogger {

    private static final Logger logger = LoggerFactory.getLogger("BlindWatermarkGuard");

    /**
     * 记录信息级别日志
     */
    public static void info(String msg, Object... args) {
        logger.info(msg, args);
    }

    /**
     * 记录警告级别日志
     */
    public static void warn(String msg, Object... args) {
        logger.warn(msg, args);
    }

    /**
     * 记录错误级别日志
     */
    public static void error(String msg, Object... args) {
        logger.error(msg, args);
    }

    /**
     * 记录调试级别日志
     */
    public static void debug(String msg, Object... args) {
        logger.debug(msg, args);
    }

    /**
     * 记录错误级别日志（带异常堆栈）
     */
    public static void error(String msg, Throwable throwable) {
        logger.error(msg, throwable);
    }
}
