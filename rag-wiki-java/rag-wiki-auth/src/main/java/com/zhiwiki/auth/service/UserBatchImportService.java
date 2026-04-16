package com.zhiwiki.auth.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.digest.BCrypt;
import cn.hutool.poi.excel.ExcelReader;
import cn.hutool.poi.excel.ExcelUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiwiki.auth.entity.User;
import com.zhiwiki.auth.entity.UserRole;
import com.zhiwiki.auth.mapper.UserMapper;
import com.zhiwiki.auth.mapper.UserRoleMapper;
import com.zhiwiki.common.exception.BusinessException;
import com.zhiwiki.common.model.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.*;

/**
 * 批量用户导入服务
 * 
 * 支持Excel格式导入，包含字段：
 * username, realName, email, phone, deptId, securityLevel
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserBatchImportService {

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;

    /**
     * 批量导入用户
     * @param file Excel文件
     * @param defaultPassword 默认密码
     * @param defaultRoleId 默认角色ID
     * @return 导入结果
     */
    @Transactional
    public BatchImportResult batchImport(MultipartFile file, String defaultPassword, Long defaultRoleId) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "导入文件不能为空");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || (!filename.endsWith(".xlsx") && !filename.endsWith(".xls"))) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "仅支持Excel格式文件(.xlsx/.xls)");
        }

        BatchImportResult result = new BatchImportResult();

        try (InputStream is = file.getInputStream()) {
            ExcelReader reader = ExcelUtil.getReader(is, 0);
            // 映射Excel列名到字段
            reader.addHeaderAlias("用户名", "username");
            reader.addHeaderAlias("真实姓名", "realName");
            reader.addHeaderAlias("邮箱", "email");
            reader.addHeaderAlias("手机号", "phone");
            reader.addHeaderAlias("部门ID", "deptId");
            reader.addHeaderAlias("安全等级", "securityLevel");

            List<Map<String, Object>> rows = reader.readAll();
            String hashedPassword = BCrypt.hashpw(defaultPassword != null ? defaultPassword : "RagWiki@2024");

            for (int i = 0; i < rows.size(); i++) {
                int rowNum = i + 2; // Excel行号（跳过表头）
                try {
                    Map<String, Object> row = rows.get(i);
                    String username = String.valueOf(row.get("username"));
                    if (username == null || "null".equals(username) || username.trim().isEmpty()) {
                        result.addFailed(rowNum, "用户名不能为空");
                        continue;
                    }

                    // 检查用户名是否已存在
                    User existing = userMapper.selectOne(
                            new LambdaQueryWrapper<User>().eq(User::getUsername, username.trim()));
                    if (existing != null) {
                        result.addFailed(rowNum, "用户名已存在: " + username);
                        continue;
                    }

                    // 创建用户
                    User user = new User();
                    user.setUserId(IdUtil.fastSimpleUUID());
                    user.setUsername(username.trim());
                    user.setPassword(hashedPassword);
                    user.setRealName(getStrValue(row, "realName"));
                    user.setEmail(getStrValue(row, "email"));
                    user.setPhone(getStrValue(row, "phone"));
                    user.setDeptId(getLongValue(row, "deptId"));
                    user.setSecurityLevel(getIntValue(row, "securityLevel", 1));
                    user.setStatus(1);
                    user.setIsDeleted(0);

                    userMapper.insert(user);

                    // 分配默认角色
                    if (defaultRoleId != null) {
                        UserRole userRole = new UserRole();
                        userRole.setUserId(user.getUserId());
                        userRole.setRoleId(defaultRoleId);
                        userRoleMapper.insert(userRole);
                    }

                    result.incrementSuccess();
                } catch (Exception e) {
                    result.addFailed(rowNum, e.getMessage());
                    log.warn("导入第{}行失败: {}", rowNum, e.getMessage());
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("批量导入用户失败", e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "导入文件解析失败: " + e.getMessage());
        }

        log.info("批量导入用户完成: 成功={}, 失败={}", result.successCount, result.failedRows.size());
        return result;
    }

    /**
     * 下载导入模板
     */
    public List<Map<String, String>> getImportTemplate() {
        List<Map<String, String>> template = new ArrayList<>();
        Map<String, String> row = new LinkedHashMap<>();
        row.put("用户名", "zhangsan");
        row.put("真实姓名", "张三");
        row.put("邮箱", "zhangsan@example.com");
        row.put("手机号", "13800138000");
        row.put("部门ID", "1");
        row.put("安全等级", "1");
        template.add(row);
        return template;
    }

    private String getStrValue(Map<String, Object> row, String key) {
        Object val = row.get(key);
        if (val == null || "null".equals(String.valueOf(val))) return null;
        return String.valueOf(val).trim();
    }

    private Long getLongValue(Map<String, Object> row, String key) {
        Object val = row.get(key);
        if (val == null || "null".equals(String.valueOf(val))) return null;
        try {
            return Long.parseLong(String.valueOf(val).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer getIntValue(Map<String, Object> row, String key, int defaultValue) {
        Object val = row.get(key);
        if (val == null || "null".equals(String.valueOf(val))) return defaultValue;
        try {
            return Integer.parseInt(String.valueOf(val).trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static class BatchImportResult {
        public int successCount = 0;
        public List<ImportFailedRow> failedRows = new ArrayList<>();

        public void incrementSuccess() { successCount++; }
        public void addFailed(int rowNum, String reason) {
            failedRows.add(new ImportFailedRow(rowNum, reason));
        }

        public int getFailedCount() { return failedRows.size(); }
    }

    public record ImportFailedRow(int rowNum, String reason) {}
}
