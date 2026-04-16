package com.zhiwiki.audit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志实体
 */
@Data
@TableName("sys_operation_log")
public class OperationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 日志ID
     */
    private String logId;

    /**
     * 操作模块
     */
    private String module;

    /**
     * 操作类型
     */
    private String operationType;

    /**
     * 操作描述
     */
    private String description;

    /**
     * 请求方法
     */
    private String method;

    /**
     * 请求URL
     */
    private String requestUrl;

    /**
     * 请求方式 GET/POST/PUT/DELETE
     */
    private String requestMethod;

    /**
     * 请求参数
     */
    private String requestParams;

    /**
     * 返回结果
     */
    private String responseResult;

    /**
     * 操作用户ID
     */
    private String userId;

    /**
     * 操作用户名
     */
    private String username;

    /**
     * 操作用户姓名
     */
    private String realName;

    /**
     * 用户部门ID
     */
    private String deptId;

    /**
     * 用户部门名称
     */
    private String deptName;

    /**
     * 客户端IP
     */
    private String clientIp;

    /**
     * 请求地理位置
     */
    private String location;

    /**
     * 浏览器类型
     */
    private String browser;

    /**
     * 操作系统
     */
    private String os;

    /**
     * 操作状态: 0-失败 1-成功
     */
    private Integer status;

    /**
     * 错误信息
     */
    private String errorMsg;

    /**
     * 执行时间(毫秒)
     */
    private Long executionTime;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
