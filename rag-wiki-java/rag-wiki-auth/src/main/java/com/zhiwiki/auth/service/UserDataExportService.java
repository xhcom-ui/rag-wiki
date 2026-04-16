package com.zhiwiki.auth.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiwiki.auth.entity.User;
import com.zhiwiki.auth.entity.UserRole;
import com.zhiwiki.auth.mapper.UserMapper;
import com.zhiwiki.auth.mapper.UserRoleMapper;
import com.zhiwiki.common.exception.BusinessException;
import com.zhiwiki.common.model.ResultCode;
import com.zhiwiki.common.security.DataEncryptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 用户数据导出/销毁服务
 * 
 * GDPR被遗忘权实现：
 * 1. 用户数据批量导出 - 将用户所有数据打包导出
 * 2. 用户数据永久销毁 - 物理删除用户所有数据
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserDataExportService {

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final DataEncryptor dataEncryptor;

    /**
     * 导出用户全部数据
     * @param userId 用户ID
     * @return 用户数据Map
     */
    public Map<String, Object> exportUserData(String userId) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUserId, userId));
        if (user == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "用户不存在");
        }

        Map<String, Object> exportData = new LinkedHashMap<>();

        // 基本信息（脱敏处理）
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("userId", user.getUserId());
        profile.put("username", user.getUsername());
        profile.put("realName", user.getRealName());
        profile.put("email", maskEmail(user.getEmail()));
        profile.put("phone", maskPhone(user.getPhone()));
        profile.put("securityLevel", user.getSecurityLevel());
        profile.put("status", user.getStatus());
        profile.put("createdAt", user.getCreatedAt());
        exportData.put("profile", profile);

        // 角色信息
        List<UserRole> userRoles = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId));
        List<Map<String, Object>> roles = new ArrayList<>();
        for (UserRole ur : userRoles) {
            Map<String, Object> roleMap = new LinkedHashMap<>();
            roleMap.put("roleId", ur.getRoleId());
            roles.add(roleMap);
        }
        exportData.put("roles", roles);

        // 扩展信息
        if (user.getExtInfo() != null) {
            exportData.put("extInfo", user.getExtInfo());
        }

        exportData.put("exportTime", new Date());
        exportData.put("exportVersion", "1.0");

        log.info("用户数据导出完成: userId={}", userId);
        return exportData;
    }

    /**
     * 永久销毁用户数据（GDPR被遗忘权）
     * 
     * 注意：此操作不可逆！
     * 策略：
     * 1. 物理删除用户角色关联
     * 2. 物理删除用户记录
     * 3. 登出当前会话
     */
    @Transactional
    public void destroyUserData(String userId, String confirmation) {
        if (!"DELETE_MY_DATA".equals(confirmation)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请输入确认码 DELETE_MY_DATA 以确认永久删除");
        }

        // 验证是用户本人操作
        String currentUserId = StpUtil.getLoginIdAsString();
        if (!currentUserId.equals(userId)) {
            throw new BusinessException(ResultCode.AUTH_ACCESS_DENIED, "只能销毁自己的数据");
        }

        log.warn("开始永久销毁用户数据: userId={}", userId);

        // 1. 删除用户角色关联
        userRoleMapper.delete(
                new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId));

        // 2. 物理删除用户记录
        userMapper.delete(
                new LambdaQueryWrapper<User>().eq(User::getUserId, userId));

        // 3. 强制登出
        StpUtil.logout(userId);

        log.warn("用户数据已永久销毁: userId={}", userId);
    }

    /**
     * 匿名化用户数据（软删除替代方案）
     * 保留记录但替换所有可识别信息
     */
    @Transactional
    public void anonymizeUserData(String userId) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUserId, userId));
        if (user == null) return;

        // 替换为匿名数据
        user.setUsername("anonymized_" + userId.substring(0, 8));
        user.setRealName("已注销用户");
        user.setEmail(null);
        user.setPhone(null);
        user.setExtInfo(null);
        user.setStatus(0);
        user.setIsDeleted(1);
        userMapper.updateById(user);

        // 删除角色关联
        userRoleMapper.delete(
                new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId));

        // 强制登出
        StpUtil.logout(userId);

        log.info("用户数据已匿名化: userId={}", userId);
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return email;
        String[] parts = email.split("@");
        String local = parts[0];
        if (local.length() <= 2) return "**@" + parts[1];
        return local.charAt(0) + "***" + local.charAt(local.length() - 1) + "@" + parts[1];
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
