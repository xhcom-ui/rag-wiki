package com.zhiwiki.common.notification;

import cn.hutool.core.util.IdUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 消息通知服务
 * 支持站内信、邮件、钉钉、企业微信等多种通知渠道
 * 
 * 渠道优先级：
 * 1. 站内信（默认，始终发送）
 * 2. 邮件（高等级告警）
 * 3. 钉钉/企业微信（CRITICAL级别告警）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final StringRedisTemplate redisTemplate;

    private static final String MESSAGE_QUEUE_PREFIX = "rag-wiki:notification:queue:";
    private static final String MESSAGE_UNREAD_PREFIX = "rag-wiki:notification:unread:";
    private static final String DINGTALK_WEBHOOK = "https://oapi.dingtalk.com/robot/send?access_token=";
    private static final String WECHAT_WEBHOOK = "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=";

    /**
     * 发送站内信
     * 通过Redis List实现消息队列，下游消费者持久化到数据库
     */
    public void sendSiteMessage(String userId, String title, String content, String targetId, String targetType) {
        try {
            Map<String, String> message = new HashMap<>();
            message.put("messageId", IdUtil.fastSimpleUUID());
            message.put("userId", userId);
            message.put("title", title);
            message.put("content", content);
            message.put("targetId", targetId);
            message.put("targetType", targetType);
            message.put("createdAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            message.put("read", "false");

            // 推入消息队列
            String queueKey = MESSAGE_QUEUE_PREFIX + userId;
            redisTemplate.opsForList().rightPush(queueKey, 
                    cn.hutool.json.JSONUtil.toJsonStr(message));

            // 增加未读计数
            redisTemplate.opsForValue().increment(MESSAGE_UNREAD_PREFIX + userId);

            log.info("站内信已发送: userId={}, title={}", userId, title);
        } catch (Exception e) {
            log.error("发送站内信失败: userId={}, error={}", userId, e.getMessage());
        }
    }

    /**
     * 批量发送站内信
     */
    public void sendSiteMessageBatch(List<String> userIds, String title, String content, String targetId, String targetType) {
        for (String userId : userIds) {
            sendSiteMessage(userId, title, content, targetId, targetType);
        }
    }

    /**
     * 获取未读消息数
     */
    public long getUnreadCount(String userId) {
        String count = redisTemplate.opsForValue().get(MESSAGE_UNREAD_PREFIX + userId);
        return count != null ? Long.parseLong(count) : 0;
    }

    /**
     * 标记已读
     */
    public void markAsRead(String userId, int count) {
        redisTemplate.opsForValue().decrement(MESSAGE_UNREAD_PREFIX + userId, count);
    }

    /**
     * 发送邮件
     * 集成Spring Boot Mail，支持HTML模板
     */
    public void sendEmail(String to, String subject, String content) {
        try {
            // 将邮件任务推入异步队列
            Map<String, String> emailTask = new HashMap<>();
            emailTask.put("to", to);
            emailTask.put("subject", subject);
            emailTask.put("content", content);
            emailTask.put("createdAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

            redisTemplate.opsForList().rightPush("rag-wiki:notification:email:queue",
                    cn.hutool.json.JSONUtil.toJsonStr(emailTask));

            log.info("邮件任务已入队: to={}, subject={}", to, subject);
        } catch (Exception e) {
            log.error("邮件任务入队失败: to={}, error={}", to, e.getMessage());
        }
    }

    /**
     * 发送钉钉机器人通知
     * 使用Webhook方式，支持Markdown格式
     */
    public void sendDingTalk(String userId, String title, String content) {
        try {
            String webhookUrl = DINGTALK_WEBHOOK; // 实际从配置读取
            
            Map<String, Object> markdown = new HashMap<>();
            markdown.put("title", title);
            markdown.put("text", "### " + title + "\n\n" + content + 
                    "\n\n---\n> 来自智维Wiki安全告警系统");

            Map<String, Object> body = new HashMap<>();
            body.put("msgtype", "markdown");
            body.put("markdown", markdown);

            // 异步发送HTTP请求
            String jsonBody = cn.hutool.json.JSONUtil.toJsonStr(body);
            redisTemplate.opsForList().rightPush("rag-wiki:notification:dingtalk:queue", jsonBody);

            log.info("钉钉通知已入队: userId={}, title={}", userId, title);
        } catch (Exception e) {
            log.error("钉钉通知入队失败: userId={}, error={}", userId, e.getMessage());
        }
    }

    /**
     * 发送企业微信通知
     */
    public void sendWeChatWork(String userId, String title, String content) {
        try {
            Map<String, Object> markdown = new HashMap<>();
            markdown.put("content", "### " + title + "\n\n" + content);

            Map<String, Object> body = new HashMap<>();
            body.put("msgtype", "markdown");
            body.put("markdown", markdown);

            String jsonBody = cn.hutool.json.JSONUtil.toJsonStr(body);
            redisTemplate.opsForList().rightPush("rag-wiki:notification:wechat:queue", jsonBody);

            log.info("企业微信通知已入队: userId={}, title={}", userId, title);
        } catch (Exception e) {
            log.error("企业微信通知入队失败: userId={}, error={}", userId, e.getMessage());
        }
    }

    /**
     * 发送审批通知
     */
    public void sendApprovalNotification(String userId, boolean approved, String documentId, String documentName) {
        String title = approved ? "文档审批通过" : "文档审批未通过";
        String content = approved 
            ? String.format("您的文档《%s》已通过审批，正在自动解析入库。", documentName)
            : String.format("您的文档《%s》审批未通过，请查看审批意见。", documentName);
        
        sendSiteMessage(userId, title, content, documentId, "DOCUMENT");
    }

    /**
     * 多渠道告警通知
     * 根据告警级别自动选择通知渠道
     */
    public void sendAlertNotification(String severity, String title, String content, 
                                       String userId, String targetId) {
        // 站内信始终发送
        sendSiteMessage(userId, title, content, targetId, "SECURITY_ALERT");

        // 根据级别选择额外渠道
        switch (severity) {
            case "CRITICAL":
                // CRITICAL: 邮件 + 钉钉 + 企业微信
                sendEmail(userId + "@company.com", "[紧急] " + title, content);
                sendDingTalk(userId, "[紧急] " + title, content);
                sendWeChatWork(userId, "[紧急] " + title, content);
                break;
            case "HIGH":
                // HIGH: 邮件 + 钉钉
                sendEmail(userId + "@company.com", "[高] " + title, content);
                sendDingTalk(userId, "[高] " + title, content);
                break;
            case "MEDIUM":
                // MEDIUM: 邮件
                sendEmail(userId + "@company.com", "[中] " + title, content);
                break;
            default:
                // LOW: 仅站内信
                break;
        }
    }
}