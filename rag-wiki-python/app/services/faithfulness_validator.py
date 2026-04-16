"""
答案忠实度校验服务

验证RAG生成的答案是否忠实于检索到的上下文文档，
防止出现幻觉(hallucination)或无根据的推论。

核心策略：
1. 声明提取 - 从答案中提取所有事实声明
2. 声明验证 - 每个声明与原文进行匹配验证
3. 忠实度评分 - 计算整体忠实度分数
4. 无依据标注 - 标注哪些声明缺乏原文支撑
"""
import asyncio
import logging
import re
from typing import List, Dict, Any, Optional
from dataclasses import dataclass, field

from app.services.llm_provider import llm_provider

logger = logging.getLogger(__name__)


@dataclass
class Claim:
    """事实声明"""
    claim_id: int
    claim_text: str
    is_supported: Optional[bool] = None
    supporting_evidence: str = ""
    confidence: float = 0.0


@dataclass
class FaithfulnessResult:
    """忠实度校验结果"""
    faithfulness_score: float = 0.0  # 0~1，1=完全忠实
    total_claims: int = 0
    supported_claims: int = 0
    unsupported_claims: int = 0
    claims: List[Claim] = field(default_factory=list)
    has_hallucination: bool = False
    risk_level: str = "low"  # low/medium/high
    summary: str = ""


class FaithfulnessValidator:
    """答案忠实度校验器"""

    def __init__(self):
        self._claim_extraction_prompt = """请从以下答案中提取所有事实性声明。

答案:
{answer}

请逐条列出答案中的事实性声明，每行一条，格式如下：
1. [声明内容]
2. [声明内容]
...

仅提取可验证的事实声明，不要包含主观意见或推测。"""

        self._claim_verification_prompt = """请验证以下声明是否被给定的上下文所支撑。

上下文:
{context}

声明:
{claim}

请判断：
1. 该声明是否被上下文直接支撑？(SUPPORTED/NOT_SUPPORTED/PARTIALLY_SUPPORTED)
2. 支撑证据(如有): 引用上下文中的原文
3. 置信度: 0~1

请以JSON格式回答:
{{"verdict": "SUPPORTED/NOT_SUPPORTED/PARTIALLY_SUPPORTED", "evidence": "...", "confidence": 0.0}}"""

    async def validate(
        self,
        answer: str,
        context: str,
        min_faithfulness: float = 0.7
    ) -> FaithfulnessResult:
        """
        验证答案对上下文的忠实度

        Args:
            answer: 生成的答案
            context: 检索到的上下文文档
            min_faithfulness: 最低忠实度阈值

        Returns:
            FaithfulnessResult
        """
        if not answer or not answer.strip():
            return FaithfulnessResult(
                faithfulness_score=1.0,
                summary="答案为空，无需校验"
            )

        if not context or not context.strip():
            return FaithfulnessResult(
                faithfulness_score=0.0,
                total_claims=0,
                has_hallucination=True,
                risk_level="high",
                summary="无上下文参考，无法验证忠实度"
            )

        try:
            # Step 1: 提取声明
            claims = await self._extract_claims(answer)
            if not claims:
                return FaithfulnessResult(
                    faithfulness_score=1.0,
                    summary="未提取到可验证的事实声明"
                )

            # Step 2: 逐条验证声明
            for claim in claims:
                result = await self._verify_claim(claim.claim_text, context)
                claim.is_supported = result.get("verdict") == "SUPPORTED"
                claim.supporting_evidence = result.get("evidence", "")
                claim.confidence = result.get("confidence", 0.0)

                # 部分支撑视为不支撑
                if result.get("verdict") == "PARTIALLY_SUPPORTED":
                    claim.is_supported = False
                    claim.confidence *= 0.5

            # Step 3: 计算忠实度分数
            supported = sum(1 for c in claims if c.is_supported)
            total = len(claims)
            faithfulness_score = supported / total if total > 0 else 1.0

            # Step 4: 风险评估
            unsupported_count = total - supported
            has_hallucination = unsupported_count > 0
            risk_level = "low"
            if faithfulness_score < 0.5:
                risk_level = "high"
            elif faithfulness_score < min_faithfulness:
                risk_level = "medium"

            result = FaithfulnessResult(
                faithfulness_score=round(faithfulness_score, 3),
                total_claims=total,
                supported_claims=supported,
                unsupported_claims=unsupported_count,
                claims=claims,
                has_hallucination=has_hallucination,
                risk_level=risk_level,
                summary=f"忠实度 {faithfulness_score:.1%}，{total}条声明中{supported}条有原文支撑，{unsupported_count}条缺乏依据"
            )

            logger.info(f"忠实度校验完成: score={faithfulness_score:.3f}, risk={risk_level}")
            return result

        except Exception as e:
            logger.error(f"忠实度校验失败: {e}")
            return FaithfulnessResult(
                faithfulness_score=0.0,
                has_hallucination=True,
                risk_level="high",
                summary=f"校验过程异常: {str(e)}"
            )

    async def quick_validate(self, answer: str, context: str) -> Dict[str, Any]:
        """快速忠实度校验（使用关键词匹配而非LLM）"""
        if not answer or not context:
            return {"score": 0.0, "risk": "high"}

        # 简单的关键词/短语匹配
        answer_sentences = re.split(r'[。！？\n]', answer)
        answer_sentences = [s.strip() for s in answer_sentences if len(s.strip()) > 5]

        if not answer_sentences:
            return {"score": 1.0, "risk": "low"}

        supported = 0
        for sentence in answer_sentences:
            # 检查句子中的关键短语是否出现在上下文中
            keywords = self._extract_keywords(sentence)
            matched = sum(1 for kw in keywords if kw in context)
            if matched >= len(keywords) * 0.5:  # 50%以上的关键词匹配
                supported += 1

        score = supported / len(answer_sentences) if answer_sentences else 1.0
        return {
            "score": round(score, 3),
            "risk": "high" if score < 0.5 else "medium" if score < 0.7 else "low",
            "total_sentences": len(answer_sentences),
            "supported_sentences": supported,
        }

    async def _extract_claims(self, answer: str) -> List[Claim]:
        """使用LLM从答案中提取事实声明"""
        try:
            prompt = self._claim_extraction_prompt.format(answer=answer)
            response = await llm_provider.chat(
                messages=[{"role": "user", "content": prompt}],
                temperature=0.1,
                max_tokens=500,
            )

            content = response.get("content", "")
            claims = []

            for line in content.split("\n"):
                line = line.strip()
                # 匹配数字编号的声明
                match = re.match(r'^\d+[\.\)、]\s*(.+)', line)
                if match:
                    claim_text = match.group(1).strip()
                    if len(claim_text) > 3:
                        claims.append(Claim(
                            claim_id=len(claims) + 1,
                            claim_text=claim_text
                        ))

            return claims

        except Exception as e:
            logger.error(f"声明提取失败: {e}")
            return []

    async def _verify_claim(self, claim: str, context: str) -> Dict[str, Any]:
        """使用LLM验证单个声明"""
        try:
            prompt = self._claim_verification_prompt.format(
                context=context[:2000],  # 限制上下文长度
                claim=claim
            )
            response = await llm_provider.chat(
                messages=[{"role": "user", "content": prompt}],
                temperature=0.1,
                max_tokens=200,
            )

            content = response.get("content", "").upper()
            verdict = "NOT_SUPPORTED"
            if "SUPPORTED" in content and "NOT_SUPPORTED" not in content:
                verdict = "SUPPORTED"
            elif "PARTIALLY" in content:
                verdict = "PARTIALLY_SUPPORTED"

            # 提取置信度
            confidence = 0.5
            conf_match = re.search(r'"confidence"[:\s]+([0-9.]+)', response.get("content", ""))
            if conf_match:
                confidence = float(conf_match.group(1))

            # 提取证据
            evidence = ""
            ev_match = re.search(r'"evidence"[:\s]+"([^"]+)"', response.get("content", ""))
            if ev_match:
                evidence = ev_match.group(1)

            return {
                "verdict": verdict,
                "evidence": evidence,
                "confidence": confidence,
            }

        except Exception as e:
            logger.error(f"声明验证失败: {e}")
            return {"verdict": "NOT_SUPPORTED", "evidence": "", "confidence": 0.0}

    def _extract_keywords(self, text: str) -> List[str]:
        """提取文本中的关键词（简单分词）"""
        # 移除常见停用词
        stopwords = {"的", "了", "在", "是", "和", "与", "或", "也", "都", "就", "而",
                     "被", "把", "从", "到", "对", "为", "以", "上", "下", "中", "等",
                     "这", "那", "其", "它", "该", "此", "不", "有", "没", "会", "能",
                     "可", "要", "将", "已", "所", "之", "而", "且", "但", "如"}

        # 简单中文分词（2~4字）
        words = []
        # 提取连续中文字符
        segments = re.findall(r'[\u4e00-\u9fff]{2,4}', text)
        for seg in segments:
            if seg not in stopwords and len(seg) >= 2:
                words.append(seg)

        # 提取英文单词
        eng_words = re.findall(r'[a-zA-Z]{2,}', text)
        words.extend(eng_words)

        # 提取数字
        numbers = re.findall(r'\d+\.?\d*', text)
        words.extend(numbers)

        return list(set(words))[:10]  # 去重，最多10个


# 全局实例
faithfulness_validator = FaithfulnessValidator()
