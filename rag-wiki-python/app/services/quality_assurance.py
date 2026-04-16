"""
文档解析质量分层保障服务
三级处理机制：
1. 基础解析（快速、覆盖常见格式）
2. 增强解析（OCR、复杂版面、表格专项）
3. 人工复核（低质量文档触发）
"""

from typing import Dict, Any, List, Optional
from dataclasses import dataclass, field
from enum import Enum
import logging
from datetime import datetime

logger = logging.getLogger(__name__)


class ParseQualityLevel(Enum):
    """解析质量等级"""
    HIGH = "HIGH"          # 高质量：无需额外处理
    MEDIUM = "MEDIUM"      # 中等质量：需要增强解析
    LOW = "LOW"            # 低质量：需要人工复核
    FAILED = "FAILED"      # 解析失败


class ParseStage(Enum):
    """解析阶段"""
    BASIC = "BASIC"              # 基础解析
    ENHANCED = "ENHANCED"        # 增强解析
    MANUAL_REVIEW = "MANUAL_REVIEW"  # 人工复核


@dataclass
class QualityMetrics:
    """质量指标"""
    confidence_score: float = 0.0  # 置信度评分 0-1
    text_extraction_rate: float = 0.0  # 文本提取率
    table_count: int = 0  # 表格数量
    image_count: int = 0  # 图片数量
    has_ocr: bool = False  # 是否使用OCR
    page_count: int = 0  # 页数
    error_count: int = 0  # 错误数
    warning_count: int = 0  # 警告数


@dataclass
class ParseResult:
    """解析结果"""
    document_id: str
    quality_level: ParseQualityLevel
    current_stage: ParseStage
    chunks: List[Dict[str, Any]]
    metrics: QualityMetrics
    error_message: Optional[str] = None
    needs_review: bool = False
    review_reason: Optional[str] = None
    metadata: Dict[str, Any] = field(default_factory=dict)


class QualityAssuranceEngine:
    """质量保障引擎"""
    
    def __init__(self):
        # 质量阈值配置
        self.quality_thresholds = {
            "high_confidence": 0.85,      # 高质量置信度
            "medium_confidence": 0.65,    # 中等质量置信度
            "min_text_rate": 0.70,        # 最低文本提取率
            "max_error_rate": 0.05,       # 最大错误率
        }
        
        # 质量检查器
        self.quality_checkers = [
            self._check_confidence,
            self._check_text_extraction_rate,
            self._check_error_rate,
            self._check_content_completeness,
        ]
    
    def assess_quality(
        self,
        document_id: str,
        raw_result: Dict[str, Any],
        parse_metrics: QualityMetrics,
    ) -> ParseResult:
        """
        评估解析质量
        
        流程：
        1. 基础质量检查
        2. 质量评分
        3. 确定质量等级
        4. 判断是否需要增强解析或人工复核
        """
        logger.info(f"开始评估文档质量: {document_id}")
        
        # 执行质量检查
        check_results = []
        for checker in self.quality_checkers:
            result = checker(parse_metrics)
            check_results.append(result)
        
        # 计算综合评分
        confidence = self._calculate_confidence_score(parse_metrics, check_results)
        parse_metrics.confidence_score = confidence
        
        # 确定质量等级
        quality_level = self._determine_quality_level(confidence, parse_metrics)
        
        # 判断当前阶段
        current_stage = self._determine_stage(quality_level)
        
        # 判断是否需要人工复核
        needs_review = quality_level in [ParseQualityLevel.LOW, ParseQualityLevel.FAILED]
        review_reason = self._get_review_reason(quality_level, parse_metrics)
        
        # 构建解析结果
        result = ParseResult(
            document_id=document_id,
            quality_level=quality_level,
            current_stage=current_stage,
            chunks=raw_result.get("chunks", []),
            metrics=parse_metrics,
            needs_review=needs_review,
            review_reason=review_reason,
            metadata={
                "quality_check_results": check_results,
                "assessed_at": datetime.now().isoformat(),
            },
        )
        
        logger.info(
            f"质量评估完成: {document_id}, "
            f"等级={quality_level.value}, "
            f"评分={confidence:.2f}, "
            f"阶段={current_stage.value}"
        )
        
        return result
    
    def _check_confidence(self, metrics: QualityMetrics) -> Dict[str, Any]:
        """检查置信度"""
        passed = metrics.confidence_score >= self.quality_thresholds["high_confidence"]
        return {
            "check": "confidence",
            "passed": passed,
            "value": metrics.confidence_score,
            "threshold": self.quality_thresholds["high_confidence"],
        }
    
    def _check_text_extraction_rate(self, metrics: QualityMetrics) -> Dict[str, Any]:
        """检查文本提取率"""
        passed = metrics.text_extraction_rate >= self.quality_thresholds["min_text_rate"]
        return {
            "check": "text_extraction_rate",
            "passed": passed,
            "value": metrics.text_extraction_rate,
            "threshold": self.quality_thresholds["min_text_rate"],
        }
    
    def _check_error_rate(self, metrics: QualityMetrics) -> Dict[str, Any]:
        """检查错误率"""
        error_rate = metrics.error_count / max(metrics.page_count, 1)
        passed = error_rate <= self.quality_thresholds["max_error_rate"]
        return {
            "check": "error_rate",
            "passed": passed,
            "value": error_rate,
            "threshold": self.quality_thresholds["max_error_rate"],
        }
    
    def _check_content_completeness(self, metrics: QualityMetrics) -> Dict[str, Any]:
        """检查内容完整性"""
        # 简化检查：如果有表格或图片，需要额外验证
        has_complex_content = metrics.table_count > 0 or metrics.image_count > 0
        return {
            "check": "content_completeness",
            "passed": not has_complex_content or metrics.confidence_score > 0.7,
            "has_complex_content": has_complex_content,
        }
    
    def _calculate_confidence_score(
        self,
        metrics: QualityMetrics,
        check_results: List[Dict],
    ) -> float:
        """
        计算综合置信度评分
        
        权重：
        - 基础置信度：40%
        - 文本提取率：30%
        - 错误率：20%
        - 内容完整性：10%
        """
        score = 0.0
        
        # 基础置信度（如果已有）
        if metrics.confidence_score > 0:
            score += metrics.confidence_score * 0.4
        
        # 文本提取率
        score += metrics.text_extraction_rate * 0.3
        
        # 错误率（越低越好）
        error_rate = metrics.error_count / max(metrics.page_count, 1)
        score += (1 - error_rate) * 0.2
        
        # 内容完整性
        completeness_score = 1.0 if metrics.table_count == 0 and metrics.image_count == 0 else 0.8
        score += completeness_score * 0.1
        
        return min(score, 1.0)
    
    def _determine_quality_level(
        self,
        confidence: float,
        metrics: QualityMetrics,
    ) -> ParseQualityLevel:
        """确定质量等级"""
        if confidence >= self.quality_thresholds["high_confidence"]:
            return ParseQualityLevel.HIGH
        elif confidence >= self.quality_thresholds["medium_confidence"]:
            return ParseQualityLevel.MEDIUM
        elif confidence > 0.4:
            return ParseQualityLevel.LOW
        else:
            return ParseQualityLevel.FAILED
    
    def _determine_stage(self, quality_level: ParseQualityLevel) -> ParseStage:
        """确定当前解析阶段"""
        if quality_level == ParseQualityLevel.HIGH:
            return ParseStage.BASIC
        elif quality_level in [ParseQualityLevel.MEDIUM, ParseQualityLevel.LOW]:
            return ParseStage.ENHANCED
        else:
            return ParseStage.MANUAL_REVIEW
    
    def _get_review_reason(
        self,
        quality_level: ParseQualityLevel,
        metrics: QualityMetrics,
    ) -> Optional[str]:
        """获取需要复核的原因"""
        if quality_level == ParseQualityLevel.FAILED:
            return "解析失败或质量极低，需要人工处理"
        elif quality_level == ParseQualityLevel.LOW:
            reasons = []
            if metrics.confidence_score < 0.65:
                reasons.append("置信度低")
            if metrics.text_extraction_rate < 0.70:
                reasons.append("文本提取率低")
            if metrics.error_count > 5:
                reasons.append("错误数过多")
            return "，".join(reasons) if reasons else None
        return None
    
    def should_enhance(self, result: ParseResult) -> bool:
        """判断是否需要增强解析"""
        return result.quality_level in [ParseQualityLevel.MEDIUM, ParseQualityLevel.LOW]
    
    def should_manual_review(self, result: ParseResult) -> bool:
        """判断是否需要人工复核"""
        return result.quality_level in [ParseQualityLevel.LOW, ParseQualityLevel.FAILED]


class ReviewQueueManager:
    """人工复核队列管理"""
    
    def __init__(self):
        self.review_queue: List[ParseResult] = []
        self.completed_reviews: List[ParseResult] = []
    
    def add_to_review_queue(self, result: ParseResult):
        """添加到复核队列"""
        result.needs_review = True
        self.review_queue.append(result)
        logger.info(f"文档 {result.document_id} 已加入复核队列: {result.review_reason}")
    
    def get_next_review(self) -> Optional[ParseResult]:
        """获取下一个待复核文档"""
        if not self.review_queue:
            return None
        return self.review_queue.pop(0)
    
    def complete_review(self, document_id: str, reviewer_id: str, 
                        review_result: Dict[str, Any]):
        """完成复核"""
        # 查找文档
        for result in self.review_queue:
            if result.document_id == document_id:
                result.metadata["reviewer_id"] = reviewer_id
                result.metadata["review_result"] = review_result
                result.metadata["reviewed_at"] = datetime.now().isoformat()
                result.needs_review = False
                
                self.review_queue.remove(result)
                self.completed_reviews.append(result)
                
                logger.info(f"文档 {document_id} 复核完成")
                return
        
        logger.warning(f"未找到待复核文档: {document_id}")
    
    def get_queue_stats(self) -> Dict[str, Any]:
        """获取队列统计"""
        return {
            "pending_count": len(self.review_queue),
            "completed_count": len(self.completed_reviews),
            "queue": [
                {
                    "document_id": r.document_id,
                    "quality_level": r.quality_level.value,
                    "review_reason": r.review_reason,
                }
                for r in self.review_queue
            ],
        }


# 全局实例
_quality_engine = None
_review_queue = None


def get_quality_engine() -> QualityAssuranceEngine:
    """获取质量保障引擎实例"""
    global _quality_engine
    if _quality_engine is None:
        _quality_engine = QualityAssuranceEngine()
    return _quality_engine


def get_review_queue() -> ReviewQueueManager:
    """获取复核队列管理器"""
    global _review_queue
    if _review_queue is None:
        _review_queue = ReviewQueueManager()
    return _review_queue


def assess_document_quality(
    document_id: str,
    raw_result: Dict[str, Any],
    metrics: Dict[str, Any],
) -> ParseResult:
    """
    便捷函数：评估文档质量
    
    用法：
    from app.services.quality_assurance import assess_document_quality
    
    result = assess_document_quality(
        document_id="doc_001",
        raw_result={"chunks": [...]},
        metrics={
            "text_extraction_rate": 0.85,
            "table_count": 3,
            "page_count": 50,
        },
    )
    """
    quality_metrics = QualityMetrics(**metrics)
    engine = get_quality_engine()
    result = engine.assess_quality(document_id, raw_result, quality_metrics)
    
    # 如果需要复核，加入队列
    if result.needs_review:
        queue = get_review_queue()
        queue.add_to_review_queue(result)
    
    return result
