package com.zhiwiki.auth.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiwiki.auth.entity.User;
import com.zhiwiki.auth.mapper.UserMapper;
import com.zhiwiki.common.exception.BusinessException;
import com.zhiwiki.common.model.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * 双因素认证(2FA/TOTP)服务
 * 
 * 基于TOTP (Time-based One-Time Password) 算法实现，
 * 兼容Google Authenticator、Microsoft Authenticator等验证器APP。
 * 
 * 流程：
 * 1. 用户启用2FA → 生成密钥 → 返回二维码URI
 * 2. 用户用验证器APP扫码 → 获取6位数字验证码
 * 3. 用户输入验证码验证 → 启用成功
 * 4. 登录时如果启用了2FA → 额外要求输入验证码
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TwoFactorAuthService {

    private final UserMapper userMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String TOTP_SECRET_PREFIX = "2fa:secret:";
    private static final String TOTP_BACKUP_PREFIX = "2fa:backup:";
    private static final String ISSUER = "RagWiki";
    private static final int SECRET_LENGTH = 20; // 160 bits
    private static final int TIME_STEP = 30; // 30秒时间窗口
    private static final int CODE_DIGITS = 6;
    private static final int WINDOW_SIZE = 1; // 允许前后1个时间窗口的偏差

    /**
     * 生成2FA密钥和二维码URI
     */
    public TwoFactorSetupResponse enable2FA(String userId) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUserId, userId));
        if (user == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "用户不存在");
        }

        // 生成随机密钥
        byte[] secretBytes = new byte[SECRET_LENGTH];
        new SecureRandom().nextBytes(secretBytes);
        String secret = Base64.getEncoder().encodeToString(secretBytes);

        // 临时存储密钥（验证通过后才真正启用）
        redisTemplate.opsForValue().set(TOTP_SECRET_PREFIX + userId, secret, 10, TimeUnit.MINUTES);

        // 生成otpauth URI
        String otpauthUri = String.format(
                "otpauth://totp/%s:%s?secret=%s&issuer=%s&algorithm=SHA1&digits=%d&period=%d",
                ISSUER, user.getUsername(), secret, ISSUER, CODE_DIGITS, TIME_STEP);

        // 生成备用码
        String[] backupCodes = generateBackupCodes();
        redisTemplate.opsForValue().set(TOTP_BACKUP_PREFIX + userId, String.join(",", backupCodes), 10, TimeUnit.MINUTES);

        log.info("用户 {} 请求启用2FA", userId);
        return new TwoFactorSetupResponse(secret, otpauthUri, backupCodes);
    }

    /**
     * 验证2FA验证码并正式启用
     */
    public boolean verifyAndEnable2FA(String userId, String code) {
        String secret = (String) redisTemplate.opsForValue().get(TOTP_SECRET_PREFIX + userId);
        if (secret == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "2FA设置已过期，请重新生成");
        }

        if (!verifyCode(secret, code)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "验证码错误");
        }

        // 验证通过，正式保存密钥到用户记录
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUserId, userId));
        String extInfo = user.getExtInfo() != null ? user.getExtInfo() : "{}";
        // 在extInfo中标记2FA已启用并保存密钥
        extInfo = extInfo.replace("}", String.format(",\"2faEnabled\":true,\"2faSecret\":\"%s\"}", secret));
        if (!extInfo.startsWith("{")) extInfo = String.format("{\"2faEnabled\":true,\"2faSecret\":\"%s\"}", secret);
        user.setExtInfo(extInfo);
        userMapper.updateById(user);

        // 保存备用码
        String backupCodesStr = (String) redisTemplate.opsForValue().get(TOTP_BACKUP_PREFIX + userId);
        if (backupCodesStr != null) {
            redisTemplate.opsForValue().set(TOTP_BACKUP_PREFIX + "saved:" + userId, backupCodesStr);
        }

        // 清理临时数据
        redisTemplate.delete(TOTP_SECRET_PREFIX + userId);
        redisTemplate.delete(TOTP_BACKUP_PREFIX + userId);

        log.info("用户 {} 2FA启用成功", userId);
        return true;
    }

    /**
     * 禁用2FA
     */
    public boolean disable2FA(String userId, String code) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUserId, userId));
        if (user == null || user.getExtInfo() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "2FA未启用");
        }

        String secret = extractSecret(user.getExtInfo());
        if (secret == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "2FA未启用");
        }

        if (!verifyCode(secret, code)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "验证码错误");
        }

        // 从extInfo中移除2FA标记
        String extInfo = user.getExtInfo()
                .replace("\"2faEnabled\":true,", "")
                .replace(",\"2faEnabled\":true", "")
                .replace("\"2faEnabled\":true", "")
                .replace("\"2faSecret\":\"" + secret + "\",", "")
                .replace(",\"2faSecret\":\"" + secret + "\"", "")
                .replace("\"2faSecret\":\"" + secret + "\"", "");
        user.setExtInfo(extInfo);
        userMapper.updateById(user);

        redisTemplate.delete(TOTP_BACKUP_PREFIX + "saved:" + userId);
        log.info("用户 {} 2FA已禁用", userId);
        return true;
    }

    /**
     * 登录时验证2FA
     */
    public boolean verify2FAForLogin(String userId, String code) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUserId, userId));
        if (user == null || user.getExtInfo() == null || !user.getExtInfo().contains("\"2faEnabled\":true")) {
            return true; // 未启用2FA，直接通过
        }

        String secret = extractSecret(user.getExtInfo());
        if (secret == null) return true;

        // 先验证TOTP码
        if (verifyCode(secret, code)) return true;

        // 再验证备用码
        String backupCodesStr = (String) redisTemplate.opsForValue().get(TOTP_BACKUP_PREFIX + "saved:" + userId);
        if (backupCodesStr != null) {
            String[] codes = backupCodesStr.split(",");
            for (int i = 0; i < codes.length; i++) {
                if (codes[i].equals(code)) {
                    // 使用过的备用码移除
                    codes[i] = "";
                    redisTemplate.opsForValue().set(TOTP_BACKUP_PREFIX + "saved:" + userId, String.join(",", codes));
                    log.info("用户 {} 使用了备用码登录", userId);
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * 检查用户是否启用了2FA
     */
    public boolean is2FAEnabled(String userId) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUserId, userId));
        return user != null && user.getExtInfo() != null && user.getExtInfo().contains("\"2faEnabled\":true");
    }

    // ==================== TOTP算法 ====================

    private boolean verifyCode(String secret, String code) {
        try {
            byte[] key = Base64.getDecoder().decode(secret);
            long currentTimeStep = System.currentTimeMillis() / 1000 / TIME_STEP;

            // 允许前后WINDOW_SIZE个时间窗口
            for (int i = -WINDOW_SIZE; i <= WINDOW_SIZE; i++) {
                long timeStep = currentTimeStep + i;
                String expectedCode = generateTOTP(key, timeStep);
                if (expectedCode.equals(code)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            log.error("TOTP验证失败", e);
            return false;
        }
    }

    private String generateTOTP(byte[] key, long timeStep) throws Exception {
        byte[] timeBytes = ByteBuffer.allocate(8).putLong(timeStep).array();
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(key, "HmacSHA1"));
        byte[] hash = mac.doFinal(timeBytes);

        int offset = hash[hash.length - 1] & 0xf;
        int binary = ((hash[offset] & 0x7f) << 24)
                | ((hash[offset + 1] & 0xff) << 16)
                | ((hash[offset + 2] & 0xff) << 8)
                | (hash[offset + 3] & 0xff);

        int otp = binary % (int) Math.pow(10, CODE_DIGITS);
        return String.format("%0" + CODE_DIGITS + "d", otp);
    }

    private String[] generateBackupCodes() {
        String[] codes = new String[8];
        SecureRandom random = new SecureRandom();
        for (int i = 0; i < codes.length; i++) {
            codes[i] = String.format("%06d", random.nextInt(1000000));
        }
        return codes;
    }

    private String extractSecret(String extInfo) {
        if (extInfo == null) return null;
        int start = extInfo.indexOf("\"2faSecret\":\"");
        if (start < 0) return null;
        start += "\"2faSecret\":\"".length();
        int end = extInfo.indexOf("\"", start);
        return end > start ? extInfo.substring(start, end) : null;
    }

    /**
     * 2FA设置响应
     */
    public record TwoFactorSetupResponse(
            String secret,
            String otpauthUri,
            String[] backupCodes
    ) {}
}
