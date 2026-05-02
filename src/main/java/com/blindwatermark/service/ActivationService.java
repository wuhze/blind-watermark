package com.blindwatermark.service;

import com.blindwatermark.common.AppLogger;
import com.blindwatermark.common.BusinessException;
import com.blindwatermark.config.AppProperties;
import com.blindwatermark.util.MachineCodeUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 本地激活管理服务
 * 通过远程授权服务器验证激活码，激活状态持久化到本地文件
 *
 * 在线激活: 客户端 → 远程服务器 /api/activate → 验证通过 → 写入本地
 * 离线激活: 管理员已在服务端绑定机器码 → 客户端直接调用同一接口验证
 *
 * 开发模式: devMode=true 时 isActivated() 直接返回 true，无需真实授权
 */
@Service
@RequiredArgsConstructor
public class ActivationService {

    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;

    private static final String ACTIVATE_PATH = "/api/activate";
    private static final String VERIFY_PATH = "/api/activate/verify";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private ActivationInfo activationInfo;

    @Data
    public static class ActivationInfo {
        private String licenseKey;
        private String licenseType;
        private String machineCode;
        private String activateTime;
        private String expireTime;
        private boolean activated;
        private String remark;
    }

    @PostConstruct
    public void init() {
        loadActivation();
    }

    /**
     * 检查当前设备是否已激活且未过期
     * 开发模式下直接返回 true
     */
    public boolean isActivated() {
        if (appProperties.isDevMode()) {
            return true;
        }
        if (activationInfo == null || !activationInfo.isActivated()) {
            return false;
        }
        if (activationInfo.getExpireTime() != null && !activationInfo.getExpireTime().isEmpty()) {
            try {
                LocalDateTime expire = LocalDateTime.parse(activationInfo.getExpireTime());
                if (LocalDateTime.now().isAfter(expire)) {
                    activationInfo.setActivated(false);
                    saveActivation();
                    return false;
                }
            } catch (Exception e) {
                AppLogger.warn("过期时间解析失败: {}", activationInfo.getExpireTime());
            }
        }
        return true;
    }

    public ActivationInfo getActivationInfo() {
        if (appProperties.isDevMode() && (activationInfo == null || !activationInfo.isActivated())) {
            ActivationInfo devInfo = new ActivationInfo();
            devInfo.setLicenseKey("不可见");
            devInfo.setLicenseType("DEVELOPMENT");
            devInfo.setMachineCode(MachineCodeUtil.generate());
            devInfo.setActivateTime(LocalDateTime.now().toString());
            devInfo.setActivated(true);
            devInfo.setRemark("开发模式 - 自动激活");
            return devInfo;
        }
        if (activationInfo == null) {
            activationInfo = new ActivationInfo();
            activationInfo.setActivated(false);
            activationInfo.setMachineCode(MachineCodeUtil.generate());
        }
        return activationInfo;
    }

    /**
     * 在线激活: 调用远程授权服务器API验证
     */
    @SuppressWarnings("unchecked")
    public ActivationInfo activateOnline(String licenseKey) {
        String machineCode = MachineCodeUtil.generate();
        String serverUrl = appProperties.getServerUrl();

        try {
            Map<String, String> body = Map.of("licenseKey", licenseKey, "machineCode", machineCode);
            String requestBody = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serverUrl + ACTIVATE_PATH))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);

            int code = (int) result.get("code");
            if (code != 200) {
                throw new BusinessException(400, (String) result.get("message"));
            }

            Map<String, String> data = (Map<String, String>) result.get("data");
            return doActivate(licenseKey, "ONLINE", machineCode,
                    data.getOrDefault("expireTime", ""),
                    data.getOrDefault("remark", ""));

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            AppLogger.error("在线激活请求失败", e);
            throw new BusinessException(500, "无法连接授权服务器: " + e.getMessage());
        }
    }

    /**
     * 离线激活: 管理员已在服务端将授权码绑定到当前机器码
     * 客户端仍需联网验证（确保授权码有效且绑定正确）
     */
    public ActivationInfo activateOffline(String licenseKey, String machineCode) {
        return activateOnline(licenseKey);
    }

    private ActivationInfo doActivate(String licenseKey, String type, String machineCode,
                                       String expireTime, String remark) {
        ActivationInfo info = new ActivationInfo();
        info.setLicenseKey(licenseKey);
        info.setLicenseType(type);
        info.setMachineCode(machineCode);
        info.setActivateTime(LocalDateTime.now().toString());
        info.setExpireTime(expireTime != null && !expireTime.isEmpty() ? expireTime : null);
        info.setActivated(true);
        info.setRemark(remark);

        this.activationInfo = info;
        saveActivation();
        AppLogger.info("激活成功: type={}, machine={}", type, machineCode);
        return info;
    }

    private String getActivationFilePath() {
        return appProperties.getDataDir() + File.separator + "activation.json";
    }

    private void saveActivation() {
        try {
            File file = new File(getActivationFilePath());
            file.getParentFile().mkdirs();
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, activationInfo);
            AppLogger.debug("激活信息已保存");
        } catch (IOException e) {
            AppLogger.error("保存激活信息失败: {}", e.getMessage());
        }
    }

    private void loadActivation() {
        File file = new File(getActivationFilePath());
        if (!file.exists()) {
            AppLogger.info("未找到本地激活文件，需要激活");
            activationInfo = new ActivationInfo();
            activationInfo.setActivated(false);
            activationInfo.setMachineCode(MachineCodeUtil.generate());
            return;
        }
        try {
            activationInfo = objectMapper.readValue(file, ActivationInfo.class);
            AppLogger.info("已加载本地激活信息: activated={}", activationInfo.isActivated());
        } catch (IOException e) {
            AppLogger.warn("读取激活文件失败，视为未激活: {}", e.getMessage());
            activationInfo = new ActivationInfo();
            activationInfo.setActivated(false);
            activationInfo.setMachineCode(MachineCodeUtil.generate());
        }
    }
}
