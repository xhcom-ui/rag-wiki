"""
文档解析质量评估服务
评估解析结果的质量，决定是否需要增强解析
"""
import logging
import re
from typing import Dict, Any, List
from dataclasses import dataclass

logger = logging.getLogger(__name__)


@dataclass
class QualityReport:
    """质量评估报告"""
    overall_score: float  # 0-1 总体质量分
    text_quality: float   # 0-1 文本质量
    structure_quality: float  # 0-1 结构质量
    completeness: float  # 0-1 完整性
    issues: List[str]    # 发现的问题
    need_enhanced_parsing: bool  # 是否需要增强解析
    recommendation: str  # 建议

    def to_dict(self) -> Dict[str, Any]:
        return {
            "overall_score": self.overall_score,
            "text_quality": self.text_quality,
            "structure_quality": self.structure_quality,
            "completeness": self.completeness,
            "issues": self.issues,
            "need_enhanced_parsing": self.need_enhanced_parsing,
            "recommendation": self.recommendation,
        }


class QualityEvaluator:
    """文档解析质量评估器"""

    # 乱码检测模式
    GARBLED_PATTERN = re.compile(r'[\ufffd\u0000-\u0008\u000b\u000e-\u001f]{3,}')
    # 连续单字符（可能是OCR噪声）
    NOISE_PATTERN = re.compile(r'(?:\w\s){5,}')
    # 重复内容
    REPEAT_PATTERN = re.compile(r'(.{20,}?)\1{2,}')

    def evaluate(self, content: str, chunks: List[Dict], metadata: Dict = None) -> QualityReport:
        """
        评估解析质量

        Args:
            content: 解析出的全文内容
            chunks: 分块列表
            metadata: 解析元数据
        """
        issues = []

        # 1. 文本质量评估
        text_quality = self._evaluate_text_quality(content, issues)

        # 2. 结构质量评估
        structure_quality = self._evaluate_structure_quality(chunks, issues)

        # 3. 完整性评估
        completeness = self._evaluate_completeness(content, chunks, metadata, issues)

        # 4. 计算总体分数
        overall_score = text_quality * 0.4 + structure_quality * 0.3 + completeness * 0.3

        # 5. 判断是否需要增强解析
        need_enhanced = overall_score < 0.6 or len(issues) >= 3

        # 6. 生成建议
        recommendation = self._generate_recommendation(overall_score, issues)

        return QualityReport(
            overall_score=round(overall_score, 2),
            text_quality=round(text_quality, 2),
            structure_quality=round(structure_quality, 2),
            completeness=round(completeness, 2),
            issues=issues,
            need_enhanced_parsing=need_enhanced,
            recommendation=recommendation,
        )

    def _evaluate_text_quality(self, content: str, issues: List[str]) -> float:
        """评估文本质量"""
        if not content or len(content.strip()) == 0:
            issues.append("解析结果为空")
            return 0.0

        score = 1.0

        # 检测乱码
        garbled_matches = self.GARBLED_PATTERN.findall(content)
        if garbled_matches:
            garbled_ratio = len("".join(garbled_matches)) / len(content)
            score -= garbled_ratio * 5
            issues.append(f"检测到乱码字符，占比{garbled_ratio:.2%}")

        # 检测OCR噪声
        noise_matches = self.NOISE_PATTERN.findall(content)
        if noise_matches:
            score -= 0.1
            issues.append("检测到OCR噪声（连续单字符）")

        # 检测重复内容
        repeat_matches = self.REPEAT_PATTERN.findall(content)
        if repeat_matches:
            score -= 0.15
            issues.append("检测到重复内容块")

        # 中文字符比例检测（如果预期是中文文档）
        chinese_chars = len(re.findall(r'[\u4e00-\u9fff]', content))
        total_chars = len(content.replace(" ", "").replace("\n", ""))
        if total_chars > 100:
            chinese_ratio = chinese_chars / total_chars
            if 0.1 < chinese_ratio < 0.3:
                # 可能是中英混合但中文字太少
                score -= 0.05

        # 空行过多检测
        lines = content.split("\n")
        empty_lines = sum(1 for l in lines if not l.strip())
        if len(lines) > 10:
            empty_ratio = empty_lines / len(lines)
            if empty_ratio > 0.5:
                score -= 0.2
                issues.append(f"空行占比过高({empty_ratio:.0%})，解析可能不完整")

        return max(score, 0.0)

    def _evaluate_structure_quality(self, chunks: List[Dict], issues: List[str]) -> float:
        """评估结构质量"""
        if not chunks:
            issues.append("没有生成任何分块")
            return 0.0

        score = 1.0

        # 分块长度分布
        chunk_lengths = [len(c.get("content", "")) for c in chunks]
        avg_len = sum(chunk_lengths) / len(chunk_lengths) if chunk_lengths else 0

        if avg_len < 20:
            score -= 0.3
            issues.append(f"分块平均长度过短({avg_len:.0f}字符)，语义可能不完整")
        elif avg_len < 50:
            score -= 0.1

        # 过短分块比例
        short_chunks = sum(1 for l in chunk_lengths if l < 30)
        short_ratio = short_chunks / len(chunks)
        if short_ratio > 0.3:
            score -= 0.15
            issues.append(f"过短分块占比{short_ratio:.0%}，建议调整分块策略")

        # 过长分块比例
        long_chunks = sum(1 for l in chunk_lengths if l > 2000)
        long_ratio = long_chunks / len(chunks)
        if long_ratio > 0.2:
            score -= 0.1
            issues.append(f"过长分块占比{long_ratio:.0%}，可能影响检索精度")

        return max(score, 0.0)

    def _evaluate_completeness(
        self, content: str, chunks: List[Dict], metadata: Dict, issues: List[str]
    ) -> float:
        """评估完整性"""
        score = 1.0

        # 内容总量评估
        if len(content) < 100:
            score -= 0.3
            issues.append("解析内容过少，文档可能未被完整解析")
        elif len(content) < 500:
            score -= 0.1

        # 分块数量与内容量匹配度
        if content and chunks:
            expected_chunks = max(1, len(content) // 300)
            actual_chunks = len(chunks)
            if actual_chunks < expected_chunks * 0.5:
                score -= 0.2
                issues.append("分块数量偏少，可能遗漏了内容")

        # 元数据检查
        if metadata:
            page_count = metadata.get("page_count", 0)
            if page_count > 0:
                # 检查是否有空页
                chunk_pages = set(c.get("page_num") for c in chunks if c.get("page_num"))
                if len(chunk_pages) < page_count * 0.5:
                    score -= 0.2
                    issues.append(f"有{page_count - len(chunk_pages)}页无解析内容")

        return max(score, 0.0)

    @staticmethod
    def _generate_recommendation(score: float, issues: List[str]) -> str:
        """生成优化建议"""
        if score >= 0.8:
            return "解析质量良好，可直接使用"
        elif score >= 0.6:
            return "解析质量一般，建议使用增强解析器重新解析"
        elif score >= 0.4:
            return "解析质量较差，建议切换到OCR或MinerU解析器"
        else:
            return "解析质量很差，可能为扫描件或加密文件，需使用OCR解析"


# 全局评估器实例
quality_evaluator = QualityEvaluator()
