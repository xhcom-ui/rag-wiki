package com.zhiwiki.auth.service;

import cn.dev33.satoken.secure.BCrypt;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiwiki.auth.dto.UserCreateRequest;
import com.zhiwiki.auth.entity.User;
import com.zhiwiki.auth.mapper.UserMapper;
import com.zhiwiki.common.exception.BusinessException;
import com.zhiwiki.common.model.PageRequest;
import com.zhiwiki.common.model.PageResult;
import com.zhiwiki.common.model.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 用户服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;

    /**
     * 创建用户（DTO方式，含数据校验）
     */
    public User createUser(UserCreateRequest request) {
        // 检查用户名唯一
        User exists = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername())
        );
        if (exists != null) {
            throw new BusinessException(ResultCode.USER_ALREADY_EXISTS);
        }
        
        User user = new User();
        user.setUserId(IdUtil.fastSimpleUUID());
        user.setUsername(request.getUsername());
        user.setPassword(BCrypt.hashpw(request.getPassword()));
        user.setRealName(request.getRealName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setDeptId(request.getDeptId());
        user.setSecurityLevel(request.getSecurityLevel());
        user.setTenantId(request.getTenantId());
        user.setStatus(1);
        userMapper.insert(user);
        return user;
    }
    
    /**
     * 创建用户（Entity方式，兼容旧接口）
     */
    public User createUser(User user) {
        // 检查用户名唯一
        User exists = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, user.getUsername())
        );
        if (exists != null) {
            throw new BusinessException(ResultCode.USER_ALREADY_EXISTS);
        }
        user.setUserId(IdUtil.fastSimpleUUID());
        user.setPassword(BCrypt.hashpw(user.getPassword()));
        userMapper.insert(user);
        return user;
    }

    /**
     * 根据userId查询用户
     */
    public User getUserByUserId(String userId) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUserId, userId)
        );
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        return user;
    }

    /**
     * 更新用户
     */
    public User updateUser(User user) {
        userMapper.updateById(user);
        return user;
    }

    /**
     * 删除用户（逻辑删除）
     */
    public void deleteUser(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        userMapper.deleteById(id);
    }

    /**
     * 分页查询用户
     */
    public PageResult<User> pageUsers(PageRequest pageRequest, String username, Integer status) {
        Page<User> page = new Page<>(pageRequest.getPageNum(), pageRequest.getPageSize());
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (username != null && !username.isEmpty()) {
            wrapper.like(User::getUsername, username);
        }
        if (status != null) {
            wrapper.eq(User::getStatus, status);
        }
        wrapper.orderByDesc(User::getCreatedAt);
        Page<User> result = userMapper.selectPage(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), pageRequest.getPageNum(), pageRequest.getPageSize());
    }

    /**
     * 启用/禁用用户
     */
    public void toggleUserStatus(Long id, Integer status) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        user.setStatus(status);
        userMapper.updateById(user);
    }

    /**
     * 修改密码
     */
    public void changePassword(String userId, String oldPassword, String newPassword) {
        User user = getUserByUserId(userId);
        if (!BCrypt.checkpw(oldPassword, user.getPassword())) {
            throw new BusinessException(ResultCode.USER_PASSWORD_ERROR);
        }
        user.setPassword(BCrypt.hashpw(newPassword));
        userMapper.updateById(user);
    }
}
