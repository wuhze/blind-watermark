package com.blindwatermark.util;

import java.util.prefs.Preferences;

/**
 * 用户偏好持久化工具
 * 使用 java.util.prefs.Preferences 存储用户习惯到注册表
 */
public class UserPreferences {

    private static final Preferences prefs = Preferences.userNodeForPackage(UserPreferences.class);

    private static final String KEY_THEME = "last_theme";
    private static final String KEY_EMBED_SOURCE_DIR = "embed_source_dir";
    private static final String KEY_EMBED_OUTPUT_DIR = "embed_output_dir";
    private static final String KEY_EXTRACT_SOURCE_DIR = "extract_source_dir";
    private static final String KEY_BATCH_SOURCE_DIR = "batch_source_dir";
    private static final String KEY_BATCH_OUTPUT_DIR = "batch_output_dir";

    public static String getLastTheme() {
        return prefs.get(KEY_THEME, null);
    }

    public static void saveLastTheme(String theme) {
        prefs.put(KEY_THEME, theme);
    }

    public static String getEmbedSourceDir() {
        return prefs.get(KEY_EMBED_SOURCE_DIR, null);
    }

    public static void saveEmbedSourceDir(String dirPath) {
        prefs.put(KEY_EMBED_SOURCE_DIR, dirPath);
    }

    public static String getEmbedOutputDir() {
        return prefs.get(KEY_EMBED_OUTPUT_DIR, null);
    }

    public static void saveEmbedOutputDir(String dirPath) {
        prefs.put(KEY_EMBED_OUTPUT_DIR, dirPath);
    }

    public static String getExtractSourceDir() {
        return prefs.get(KEY_EXTRACT_SOURCE_DIR, null);
    }

    public static void saveExtractSourceDir(String dirPath) {
        prefs.put(KEY_EXTRACT_SOURCE_DIR, dirPath);
    }

    public static String getBatchSourceDir() {
        return prefs.get(KEY_BATCH_SOURCE_DIR, null);
    }

    public static void saveBatchSourceDir(String dirPath) {
        prefs.put(KEY_BATCH_SOURCE_DIR, dirPath);
    }

    public static String getBatchOutputDir() {
        return prefs.get(KEY_BATCH_OUTPUT_DIR, null);
    }

    public static void saveBatchOutputDir(String dirPath) {
        prefs.put(KEY_BATCH_OUTPUT_DIR, dirPath);
    }
}
