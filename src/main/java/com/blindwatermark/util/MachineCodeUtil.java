package com.blindwatermark.util;

import com.blindwatermark.common.AppLogger;
import cn.hutool.crypto.SecureUtil;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * 机器码生成工具
 * 采集CPU ID、主板序列号、硬盘序列号、MAC地址等硬件信息
 * 组合后进行SHA-256哈希，生成32位大写十六进制机器码
 *
 * 设计原则:
 * - 多硬件组合哈希，换一个硬件就换机器码，防止简单克隆
 * - 取前32位，足够区分不同设备
 * - 命令执行超时3秒，避免卡死
 */
public final class MachineCodeUtil {

    private MachineCodeUtil() {
    }

    /**
     * 生成当前设备的唯一机器码
     * 综合CPU+主板+硬盘+MAC地址信息
     *
     * @return 32位大写十六进制机器码
     */
    public static String generate() {
        StringBuilder raw = new StringBuilder();

        raw.append("CPU:").append(getCpuId());
        raw.append("|MB:").append(getMotherboardSerial());
        raw.append("|DISK:").append(getDiskSerial());
        raw.append("|MAC:").append(getMacAddress());
        raw.append("|USER:").append(System.getProperty("user.name"));
        raw.append("|OS:").append(System.getProperty("os.name"));

        String hash = SecureUtil.sha256(raw.toString());
        return hash.substring(0, 32).toUpperCase();
    }

    /**
     * 获取CPU序列号（Windows: wmic cpu ProcessorId）
     */
    private static String getCpuId() {
        return executeCommand("wmic cpu get ProcessorId");
    }

    /**
     * 获取主板序列号（Windows: wmic baseboard get SerialNumber）
     */
    private static String getMotherboardSerial() {
        return executeCommand("wmic baseboard get SerialNumber");
    }

    /**
     * 获取硬盘序列号（Windows: wmic diskdrive get SerialNumber）
     */
    private static String getDiskSerial() {
        return executeCommand("wmic diskdrive get SerialNumber");
    }

    /**
     * 获取第一个有硬件地址的网卡MAC地址
     */
    private static String getMacAddress() {
        try {
            Enumeration<NetworkInterface> networks = NetworkInterface.getNetworkInterfaces();
            while (networks.hasMoreElements()) {
                NetworkInterface ni = networks.nextElement();
                byte[] mac = ni.getHardwareAddress();
                if (mac != null && mac.length > 0 && !ni.isLoopback()) {
                    StringBuilder sb = new StringBuilder();
                    for (byte b : mac) {
                        sb.append(String.format("%02X", b));
                    }
                    return sb.toString();
                }
            }
        } catch (Exception e) {
            AppLogger.warn("获取MAC地址失败: {}", e.getMessage());
        }
        return "UNKNOWN";
    }

    /**
     * 执行系统命令并返回输出（取第二行，即实际值）
     * 超时3秒自动终止，防止卡死
     */
    private static String executeCommand(String command) {
        try {
            ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            List<String> lines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty()) {
                        lines.add(trimmed);
                    }
                }
            }

            process.waitFor();

            /* 第一行是标题，第二行是值 */
            if (lines.size() >= 2) {
                return lines.get(1).replaceAll("\\s+", "");
            }
            return "UNKNOWN";
        } catch (Exception e) {
            AppLogger.warn("执行命令失败 [{}]: {}", command, e.getMessage());
            return "UNKNOWN";
        }
    }
}
