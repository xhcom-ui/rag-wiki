"""
记忆冲突检测服务

功能：
1. 语义去重 - 检测语义相同/相近的记忆条目
2. 矛盾冲突检测 - 发现相互矛盾的知识条目
3. 记忆合并建议 - 提供合并/覆盖建议
"""
import asyncio
import logging
from typing import List, Dict, Any, Optional, Tuple

from app.services.memory import memory_service
from app.services.llm_provider import llm_provider

logger = logging.getLogger(__name__)


class MemoryConflictDetector:
    """记忆冲突检测器"""

    def __init__(self):
        self._similarity_threshold = 0.85  # 语义相似度阈值
        self._contradiction_prompt = """你是一个知识冲突检测专家。请分析以下两条知识是否存在矛盾冲突。

知识A: {memory_a}
知识B: {memory_b}

请回答：
1. 是否存在矛盾？(YES/NO/PARTIAL)
2. 矛盾类型(如果存在): 事实矛盾/时间矛盾/逻辑矛盾/部分矛盾
3. 矛盾说明: 简要说明冲突点
4. 建议处理方式: 保留A/保留B/合并/需人工确认

请以JSON格式回答。"""

    async def detect_semantic_duplicates(
        self,
        user_id: str,
        space_id: Optional[str] = None,
        threshold: float = 0.85
    ) -> List[Dict[str, Any]]:
        """
        检测语义重复的记忆

        Args:
            user_id: 用户ID
            space_id: 空间ID（可选，为空则检测全部）
            threshold: 相似度阈值

        Returns:
            重复组列表
        """
        try:
            # 获取用户所有记忆
            memories = await memory_service.search(
                query="",  # 空查询获取全部
                user_id=user_id,
                space_id=space_id,
                top_k=200
            )

            if len(memories) < 2:
                return []

            duplicate_groups = []
            checked = set()

            for i, mem_a in enumerate(memories):
                if mem_a.get("memory_id") in checked:
                    continue

                group = [mem_a]
                for j, mem_b in enumerate(memories):
                    if i >= j or mem_b.get("memory_id") in checked:
                        continue

                    # 计算语义相似度
                    similarity = await self._compute_similarity(
                        mem_a.get("content", ""),
                        mem_b.get("content", "")
                    )

                    if similarity >= threshold:
                        group.append(mem_b)
                        checked.add(mem_b.get("memory_id"))

                if len(group) > 1:
                    checked.add(mem_a.get("memory_id"))
                    duplicate_groups.append({
                        "type": "semantic_duplicate",
                        "similarity": threshold,
                        "memories": group,
                        "suggestion": "merge",
                        "message": f"发现{len(group)}条语义重复的记忆",
                    })

            logger.info(f"用户 {user_id} 记忆去重检测完成: 发现 {len(duplicate_groups)} 组重复")
            return duplicate_groups

        except Exception as e:
            logger.error(f"语义去重检测失败: {e}")
            return []

    async def detect_contradictions(
        self,
        user_id: str,
        space_id: Optional[str] = None
    ) -> List[Dict[str, Any]]:
        """
        检测矛盾冲突的记忆

        Args:
            user_id: 用户ID
            space_id: 空间ID

        Returns:
            矛盾组列表
        """
        try:
            memories = await memory_service.search(
                query="",
                user_id=user_id,
                space_id=space_id,
                top_k=200
            )

            if len(memories) < 2:
                return []

            contradiction_groups = []

            # 先通过语义相似度筛选可能矛盾的候选对
            # 矛盾的知识通常在主题上相关但结论相反
            candidate_pairs = []
            for i, mem_a in enumerate(memories):
                for j, mem_b in enumerate(memories):
                    if i >= j:
                        continue
                    # 中等相似度（主题相关但不完全相同）可能是矛盾
                    sim = await self._compute_similarity(
                        mem_a.get("content", ""),
                        mem_b.get("content", "")
                    )
                    if 0.4 <= sim <= 0.8:  # 主题相关但不是重复
                        candidate_pairs.append((mem_a, mem_b, sim))

            # 对候选对使用LLM判断是否存在矛盾
            for mem_a, mem_b, sim in candidate_pairs[:20]:  # 限制检测数量
                try:
                    result = await self._llm_detect_contradiction(
                        mem_a.get("content", ""),
                        mem_b.get("content", "")
                    )
                    if result.get("has_contradiction", False):
                        contradiction_groups.append({
                            "type": "contradiction",
                            "contradiction_type": result.get("contradiction_type", "unknown"),
                            "description": result.get("description", ""),
                            "suggestion": result.get("suggestion", "needs_review"),
                            "memories": [mem_a, mem_b],
                            "similarity": sim,
                        })
                except Exception as e:
                    logger.warning(f"矛盾检测LLM调用失败: {e}")
                    continue

            logger.info(f"用户 {user_id} 矛盾检测完成: 发现 {len(contradiction_groups)} 组矛盾")
            return contradiction_groups

        except Exception as e:
            logger.error(f"矛盾检测失败: {e}")
            return []

    async def full_conflict_analysis(
        self,
        user_id: str,
        space_id: Optional[str] = None
    ) -> Dict[str, Any]:
        """完整的冲突分析：语义去重 + 矛盾检测"""
        duplicates = await self.detect_semantic_duplicates(user_id, space_id)
        contradictions = await self.detect_contradictions(user_id, space_id)

        return {
            "user_id": user_id,
            "space_id": space_id,
            "duplicate_groups": duplicates,
            "contradiction_groups": contradictions,
            "total_duplicates": len(duplicates),
            "total_contradictions": len(contradictions),
            "summary": f"检测到 {len(duplicates)} 组语义重复，{len(contradictions)} 组矛盾冲突",
        }

    async def _compute_similarity(self, text_a: str, text_b: str) -> float:
        """计算两段文本的语义相似度"""
        try:
            # 使用embedding计算余弦相似度
            from app.services.embedding import embedding_service
            import numpy as np

            emb_a = await embedding_service.get_embedding(text_a)
            emb_b = await embedding_service.get_embedding(text_b)

            if emb_a is None or emb_b is None:
                return 0.0

            vec_a = np.array(emb_a)
            vec_b = np.array(emb_b)
            similarity = float(np.dot(vec_a, vec_b) / (np.linalg.norm(vec_a) * np.linalg.norm(vec_b)))
            return max(0.0, min(1.0, similarity))

        except Exception as e:
            logger.warning(f"计算语义相似度失败: {e}")
            return 0.0

    async def _llm_detect_contradiction(self, text_a: str, text_b: str) -> Dict[str, Any]:
        """使用LLM检测两条知识是否矛盾"""
        prompt = self._contradiction_prompt.format(memory_a=text_a, memory_b=text_b)

        try:
            response = await llm_provider.chat(
                messages=[{"role": "user", "content": prompt}],
                temperature=0.1,
                max_tokens=300,
            )

            # 解析LLM返回的JSON
            import json
            content = response.get("content", "")

            # 简单解析
            has_contradiction = "YES" in content.upper() or "PARTIAL" in content.upper()
            contradiction_type = "unknown"
            description = ""
            suggestion = "needs_review"

            if "事实矛盾" in content:
                contradiction_type = "factual"
            elif "时间矛盾" in content:
                contradiction_type = "temporal"
            elif "逻辑矛盾" in content:
                contradiction_type = "logical"
            elif "部分矛盾" in content:
                contradiction_type = "partial"

            if "保留A" in content:
                suggestion = "keep_a"
            elif "保留B" in content:
                suggestion = "keep_b"
            elif "合并" in content:
                suggestion = "merge"

            # 提取说明部分
            for line in content.split("\n"):
                if "矛盾说明" in line or "说明" in line:
                    description = line.split(":", 1)[-1].strip() if ":" in line else line.strip()
                    break

            return {
                "has_contradiction": has_contradiction,
                "contradiction_type": contradiction_type,
                "description": description,
                "suggestion": suggestion,
            }

        except Exception as e:
            logger.error(f"LLM矛盾检测失败: {e}")
            return {"has_contradiction": False, "error": str(e)}


# 全局实例
memory_conflict_detector = MemoryConflictDetector()
