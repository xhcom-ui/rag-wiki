"""
异常检测与实时告警服务
支持：
1. 高频查询检测
2. 越权访问检测
3. 非工作时间异常访问
4. 跨部门越权访问
5. 实时告警与多渠道通知
"""

from typing import Dict, Any, List, Optional
from dataclasses import dataclass, field
from enum import Enum
from datetime import datetime, time
import logging
import time as time_module
from collections import defaultdict

logger = logging.getLogger(__name__)


class AlertLevel(Enum):
    """告警级别"""
    LOW = "LOW"              # 低：信息记录
    MEDIUM = "MEDIUM"        # 中：需要关注
    HIGH = "HIGH"            # 高：需要处理
    CRITICAL = "CRITICAL"    # 严重：立即处理


class AlertType(Enum):
    """告警类型"""
    HIGH_FREQUENCY_QUERY = "HIGH_FREQUENCY_QUERY"          # 高频查询
    UNAUTHORIZED_ACCESS = "UNAUTHORIZED_ACCESS"            # 越权访问
    OFF_HOURS_ACCESS = "OFF_HOURS_ACCESS"                  # 非工作时间访问
    CROSS_DEPT_ACCESS = "CROSS_DEPT_ACCESS"                # 跨部门访问
    SENSITIVE_DOC_ACCESS = "SENSITIVE_DOC_ACCESS"          # 敏感文档访问
    SYSTEM_ERROR = "SYSTEM_ERROR"                          # 系统错误
    PERMISSION_VIOLATION = "PERMISSION_VIOLATION"          # 权限违规


@dataclass
class AlertRule:
    """告警规则"""
    rule_id: str
    name: str
    alert_type: AlertType
    alert_level: AlertLevel
    description: str
    threshold: int  # 阈值
    time_window: int  # 时间窗口（秒）
    enabled: bool = True
    notification_channels: List[str] = field(default_factory=list)  # 通知渠道


@dataclass
class SecurityAlert:
    """安全告警"""
    alert_id: str
    alert_type: AlertType
    alert_level: AlertLevel
    user_id: str
    description: str
    details: Dict[str, Any]
    timestamp: float
    handled: bool = False
    handler_id: Optional[str] = None
    handle_comment: Optional[str] = None


class AnomalyDetector:
    """异常检测器"""
    
    def __init__(self):
        # 用户查询记录：{user_id: [timestamp, ...]}
        self.user_query_history: Dict[str, List[float]] = defaultdict(list)
        # 用户访问记录：{user_id: [details, ...]}
        self.user_access_history: Dict[str, List[Dict]] = defaultdict(list)
        # 违规记录计数
        self.violation_counts: Dict[str, int] = defaultdict(int)
        
        # 告警规则
        self.rules: Dict[str, AlertRule] = {}
        self._init_default_rules()
        
        # 告警回调
        self.alert_callbacks = []
    
    def _init_default_rules(self):
        """初始化默认告警规则"""
        self.rules["high_freq_query"] = AlertRule(
            rule_id="high_freq_query",
            name="高频查询检测",
            alert_type=AlertType.HIGH_FREQUENCY_QUERY,
            alert_level=AlertLevel.MEDIUM,
            description="用户在短时间内查询频率过高",
            threshold=100,  # 100次
            time_window=300,  # 5分钟
            enabled=True,
            notification_channels=["log", "webhook"],
        )
        
        self.rules["unauthorized_access"] = AlertRule(
            rule_id="unauthorized_access",
            name="越权访问检测",
            alert_type=AlertType.UNAUTHORIZED_ACCESS,
            alert_level=AlertLevel.HIGH,
            description="用户尝试访问无权限的资源",
            threshold=5,  # 5次
            time_window=600,  # 10分钟
            enabled=True,
            notification_channels=["log", "webhook", "email"],
        )
        
        self.rules["off_hours_access"] = AlertRule(
            rule_id="off_hours_access",
            name="非工作时间访问",
            alert_type=AlertType.OFF_HOURS_ACCESS,
            alert_level=AlertLevel.LOW,
            description="用户在非工作时间访问敏感资源",
            threshold=10,
            time_window=3600,
            enabled=True,
            notification_channels=["log"],
        )
        
        self.rules["cross_dept_access"] = AlertRule(
            rule_id="cross_dept_access",
            name="跨部门访问检测",
            alert_type=AlertType.CROSS_DEPT_ACCESS,
            alert_level=AlertLevel.MEDIUM,
            description="用户频繁访问其他部门资源",
            threshold=20,
            time_window=1800,
            enabled=True,
            notification_channels=["log", "webhook"],
        )
    
    def record_query(self, user_id: str, **kwargs):
        """记录用户查询"""
        current_time = time_module.time()
        self.user_query_history[user_id].append(current_time)
        
        # 清理过期记录（保留最近1小时）
        cutoff = current_time - 3600
        self.user_query_history[user_id] = [
            t for t in self.user_query_history[user_id] if t > cutoff
        ]
        
        # 检测高频查询
        self._check_high_frequency_query(user_id)
    
    def record_access(self, user_id: str, resource_type: str, resource_id: str, 
                      dept_id: str, is_authorized: bool, **kwargs):
        """记录资源访问"""
        access_record = {
            "timestamp": time_module.time(),
            "resource_type": resource_type,
            "resource_id": resource_id,
            "dept_id": dept_id,
            "is_authorized": is_authorized,
            **kwargs,
        }
        
        self.user_access_history[user_id].append(access_record)
        
        # 越权访问检测
        if not is_authorized:
            self.violation_counts[user_id] += 1
            self._check_unauthorized_access(user_id)
        
        # 非工作时间访问检测
        self._check_off_hours_access(user_id, access_record)
        
        # 跨部门访问检测
        self._check_cross_dept_access(user_id, dept_id)
    
    def _check_high_frequency_query(self, user_id: str):
        """检测高频查询"""
        rule = self.rules.get("high_freq_query")
        if not rule or not rule.enabled:
            return
        
        history = self.user_query_history.get(user_id, [])
        if not history:
            return
        
        # 统计时间窗口内的查询次数
        current_time = time_module.time()
        window_start = current_time - rule.time_window
        count = sum(1 for t in history if t > window_start)
        
        if count >= rule.threshold:
            self._trigger_alert(
                alert_type=rule.alert_type,
                alert_level=rule.alert_level,
                user_id=user_id,
                description=f"用户 {user_id} 在 {rule.time_window}秒内查询 {count} 次，超过阈值 {rule.threshold}",
                details={
                    "query_count": count,
                    "time_window": rule.time_window,
                    "threshold": rule.threshold,
                },
                channels=rule.notification_channels,
            )
    
    def _check_unauthorized_access(self, user_id: str):
        """检测越权访问"""
        rule = self.rules.get("unauthorized_access")
        if not rule or not rule.enabled:
            return
        
        violation_count = self.violation_counts.get(user_id, 0)
        
        if violation_count >= rule.threshold:
            self._trigger_alert(
                alert_type=rule.alert_type,
                alert_level=rule.alert_level,
                user_id=user_id,
                description=f"用户 {user_id} 越权访问 {violation_count} 次，超过阈值 {rule.threshold}",
                details={
                    "violation_count": violation_count,
                    "threshold": rule.threshold,
                },
                channels=rule.notification_channels,
            )
            
            # 重置计数（避免重复告警）
            self.violation_counts[user_id] = 0
    
    def _check_off_hours_access(self, user_id: str, access_record: Dict):
        """检测非工作时间访问"""
        rule = self.rules.get("off_hours_access")
        if not rule or not rule.enabled:
            return
        
        timestamp = access_record["timestamp"]
        access_time = datetime.fromtimestamp(timestamp)
        
        # 定义工作时间：9:00 - 18:00
        work_start = time(9, 0)
        work_end = time(18, 0)
        current_time = access_time.time()
        
        # 非工作时间
        if current_time < work_start or current_time > work_end:
            # 检查访问频率
            history = self.user_access_history.get(user_id, [])
            current_time_val = time_module.time()
            window_start = current_time_val - rule.time_window
            
            off_hours_count = sum(
                1 for acc in history
                if acc["timestamp"] > window_start and
                (datetime.fromtimestamp(acc["timestamp"]).time() < work_start or
                 datetime.fromtimestamp(acc["timestamp"]).time() > work_end)
            )
            
            if off_hours_count >= rule.threshold:
                self._trigger_alert(
                    alert_type=rule.alert_type,
                    alert_level=rule.alert_level,
                    user_id=user_id,
                    description=f"用户 {user_id} 在非工作时间访问 {off_hours_count} 次",
                    details={
                        "access_time": access_time.isoformat(),
                        "access_count": off_hours_count,
                    },
                    channels=rule.notification_channels,
                )
    
    def _check_cross_dept_access(self, user_id: str, target_dept_id: str):
        """检测跨部门访问"""
        rule = self.rules.get("cross_dept_access")
        if not rule or not rule.enabled:
            return
        
        # 简化实现：统计访问不同部门的数量
        history = self.user_access_history.get(user_id, [])
        current_time_val = time_module.time()
        window_start = current_time_val - rule.time_window
        
        accessed_depts = set()
        for acc in history:
            if acc["timestamp"] > window_start:
                accessed_depts.add(acc.get("dept_id", ""))
        
        if len(accessed_depts) >= rule.threshold:
            self._trigger_alert(
                alert_type=rule.alert_type,
                alert_level=rule.alert_level,
                user_id=user_id,
                description=f"用户 {user_id} 访问 {len(accessed_depts)} 个部门资源",
                details={
                    "accessed_depts": list(accessed_depts),
                    "threshold": rule.threshold,
                },
                channels=rule.notification_channels,
            )
    
    def _trigger_alert(
        self,
        alert_type: AlertType,
        alert_level: AlertLevel,
        user_id: str,
        description: str,
        details: Dict[str, Any],
        channels: List[str],
    ):
        """触发告警"""
        alert_id = f"alert_{int(time_module.time())}_{user_id[:8]}"
        
        alert = SecurityAlert(
            alert_id=alert_id,
            alert_type=alert_type,
            alert_level=alert_level,
            user_id=user_id,
            description=description,
            details=details,
            timestamp=time_module.time(),
        )
        
        # 记录日志
        logger.warning(
            f"安全告警 [{alert_level.value}]: {description}"
        )
        
        # 发送通知
        for channel in channels:
            self._send_notification(channel, alert)
        
        # 调用回调
        for callback in self.alert_callbacks:
            try:
                callback(alert)
            except Exception as e:
                logger.error(f"告警回调执行失败: {e}")
    
    def _send_notification(self, channel: str, alert: SecurityAlert):
        """发送通知"""
        if channel == "log":
            logger.info(f"[日志通知] 告警: {alert.description}")
        elif channel == "webhook":
            # 实际应调用Webhook
            logger.info(f"[Webhook通知] 告警: {alert.description}")
        elif channel == "email":
            # 实际应发送邮件
            logger.info(f"[邮件通知] 告警: {alert.description}")
        elif channel == "sms":
            # 实际应发送短信
            logger.info(f"[短信通知] 告警: {alert.description}")
    
    def register_callback(self, callback):
        """注册告警回调"""
        self.alert_callbacks.append(callback)
    
    def get_alert_stats(self) -> Dict[str, Any]:
        """获取告警统计"""
        return {
            "total_violations": sum(self.violation_counts.values()),
            "active_users": len(self.user_query_history),
            "rules_enabled": sum(1 for r in self.rules.values() if r.enabled),
        }


# 全局异常检测器实例
_anomaly_detector = None


def get_anomaly_detector() -> AnomalyDetector:
    """获取全局异常检测器实例"""
    global _anomaly_detector
    if _anomaly_detector is None:
        _anomaly_detector = AnomalyDetector()
        logger.info("异常检测器初始化完成")
    return _anomaly_detector
