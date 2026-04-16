"""
查询理解服务
实现: 查询改写、意图识别、实体提取、查询扩展
参考: 大厂面试最佳实践 - 查询理解提高检索召回率
"""
import json
import logging
import re
from typing import List, Dict, Any, Optional
from dataclasses import dataclass
from enum import Enum

from app.core.config import settings

logger = logging.getLogger(__name__)


class QueryIntent(str, Enum):
    """查询意图"""
    FACTUAL = "factual"           # 事实性问答
    PROCEDURAL = "procedural"     # 操作流程
    COMPARISON = "comparison"     # 对比分析
    EXPLORATORY = "exploratory"   # 探索性搜索
    CONVERSATIONAL = "conversational"  # 对话式
    UNKNOWN = "unknown"


@dataclass
class QueryUnderstanding:
    """查询理解结果"""
    original_query: str
    rewritten_query: str
    intent: QueryIntent
    entities: List[str]
    keywords: List[str]
    expanded_queries: List[str]
    confidence: float


class QueryUnderstandingService:
    """
    查询理解服务
    
    功能:
    1. 查询改写 - 使查询更适合检索
    2. 意图识别 - 识别用户真实意图
    3. 实体提取 - 提取关键实体
    4. 查询扩展 - 生成同义查询
    """
    
    def __init__(self):
        self._llm = None
    
    def _get_llm(self):
        """获取LLM服务"""
        if self._llm is None:
            from app.services.llm_provider import multi_llm_service
            self._llm = multi_llm_service
        return self._llm
    
    async def understand(
        self,
        query: str,
        use_llm: bool = True,
    ) -> QueryUnderstanding:
        """
        理解查询
        
        Args:
            query: 原始查询
            use_llm: 是否使用LLM增强
            
        Returns:
            QueryUnderstanding对象
        """
        # 基础处理
        keywords = self._extract_keywords(query)
        entities = self._extract_entities(query)
        intent = self._classify_intent(query)
        
        # LLM增强处理
        rewritten_query = query
        expanded_queries = []
        confidence = 0.7
        
        if use_llm:
            try:
                llm_result = await self._llm_understand(query)
                if llm_result:
                    rewritten_query = llm_result.get("rewritten_query", query)
                    expanded_queries = llm_result.get("expanded_queries", [])
                    confidence = 0.9
            except Exception as e:
                logger.warning(f"LLM查询理解失败: {e}")
        
        return QueryUnderstanding(
            original_query=query,
            rewritten_query=rewritten_query,
            intent=intent,
            entities=entities,
            keywords=keywords,
            expanded_queries=expanded_queries,
            confidence=confidence,
        )
    
    def _extract_keywords(self, query: str) -> List[str]:
        """提取关键词（简化实现，实际应使用jieba等分词工具）"""
        # 简单规则：提取中文词语和英文单词
        keywords = []
        
        # 提取中文词语（2-4字）
        chinese_pattern = re.compile(r'[\u4e00-\u9fa5]{2,4}')
        keywords.extend(chinese_pattern.findall(query))
        
        # 提取英文单词
        english_pattern = re.compile(r'[a-zA-Z]{3,}')
        keywords.extend([w.lower() for w in english_pattern.findall(query)])
        
        return list(set(keywords))[:10]
    
    def _extract_entities(self, query: str) -> List[str]:
        """提取实体（简化实现）"""
        entities = []
        
        # 提取引号内的内容作为精确匹配实体
        quoted = re.findall(r'["\']([^"\']+)["\']', query)
        entities.extend(quoted)
        
        # 提取专有名词（大写字母开头）
        proper_nouns = re.findall(r'\b[A-Z][a-z]+(?:\s+[A-Z][a-z]+)*\b', query)
        entities.extend(proper_nouns)
        
        return list(set(entities))[:5]
    
    def _classify_intent(self, query: str) -> QueryIntent:
        """分类查询意图"""
        query_lower = query.lower()
        
        # 操作流程类
        procedural_keywords = ["如何", "怎么", "怎样", "步骤", "流程", "方法", "how to"]
        if any(kw in query_lower for kw in procedural_keywords):
            return QueryIntent.PROCEDURAL
        
        # 对比分析类
        comparison_keywords = ["区别", "对比", "比较", "哪个更好", "difference", "compare"]
        if any(kw in query_lower for kw in comparison_keywords):
            return QueryIntent.COMPARISON
        
        # 探索性搜索
        exploratory_keywords = ["有哪些", "是什么", "列出", "归纳", "what are"]
        if any(kw in query_lower for kw in exploratory_keywords):
            return QueryIntent.EXPLORATORY
        
        # 对话式
        conversational_keywords = ["你好", "谢谢", "帮我", "请问", "谢谢", "thanks"]
        if any(kw in query_lower for kw in conversational_keywords):
            return QueryIntent.CONVERSATIONAL
        
        # 默认为事实性问答
        return QueryIntent.FACTUAL
    
    async def _llm_understand(self, query: str) -> Optional[Dict]:
        """使用LLM进行查询理解"""
        llm = self._get_llm()
        
        prompt = f"""请分析以下用户查询，并输出JSON格式的分析结果：

用户查询: {query}

请输出:
1. rewritten_query: 改写后的查询，更适合检索（保留核心意图，补充必要上下文）
2. expanded_queries: 2-3个同义查询扩展
3. intent: 查询意图 (factual/procedural/comparison/exploratory)

输出格式:
```json
{{
  "rewritten_query": "改写后的查询",
  "expanded_queries": ["扩展查询1", "扩展查询2"],
  "intent": "意图类型"
}}
```"""
        
        try:
            response = await llm.chat_completion(
                messages=[{"role": "user", "content": prompt}],
                temperature=0.3,
                max_tokens=500,
            )
            
            # 解析JSON
            json_match = re.search(r'```json\s*(.*?)\s*```', response, re.DOTALL)
            if json_match:
                return json.loads(json_match.group(1))
            else:
                return json.loads(response)
                
        except Exception as e:
            logger.error(f"LLM查询理解失败: {e}")
            return None
    
    async def rewrite_query(self, query: str) -> str:
        """查询改写"""
        understanding = await self.understand(query)
        return understanding.rewritten_query
    
    async def expand_query(self, query: str) -> List[str]:
        """查询扩展"""
        understanding = await self.understand(query)
        return [query] + understanding.expanded_queries


class QueryCache:
    """
    查询缓存
    缓存高频查询的结果，提升响应速度
    """
    
    def __init__(self, max_size: int = 1000, ttl: int = 3600):
        self.max_size = max_size
        self.ttl = ttl
        self._cache: Dict[str, tuple] = {}  # query -> (result, timestamp)
    
    def get(self, query: str) -> Optional[Any]:
        """获取缓存"""
        import time
        if query in self._cache:
            result, timestamp = self._cache[query]
            if time.time() - timestamp < self.ttl:
                return result
            else:
                del self._cache[query]
        return None
    
    def set(self, query: str, result: Any) -> None:
        """设置缓存"""
        import time
        if len(self._cache) >= self.max_size:
            # 删除最旧的条目
            oldest_key = min(self._cache.keys(), 
                            key=lambda k: self._cache[k][1])
            del self._cache[oldest_key]
        
        self._cache[query] = (result, time.time())


# 全局实例
query_understanding_service = QueryUnderstandingService()
query_cache = QueryCache()
