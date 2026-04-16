"""
Reranker重排序服务
使用BGE-Reranker或其他重排序模型对检索结果进行精排
参考: 大厂面试最佳实践 - Cross-Encoder重排序提高检索精度
"""
import json
import logging
import asyncio
from typing import List, Dict, Any, Optional, Tuple
from dataclasses import dataclass

from app.core.config import settings

logger = logging.getLogger(__name__)


@dataclass
class RerankResult:
    """重排序结果"""
    chunk_id: str
    content: str
    score: float
    document_name: str
    page_num: int
    original_rank: int
    rerank_score: float


class BaseReranker:
    """重排序器抽象基类"""
    
    async def rerank(
        self,
        query: str,
        candidates: List[Dict[str, Any]],
        top_k: int = 5,
    ) -> List[RerankResult]:
        """对候选文档进行重排序（子类必须实现）"""
        raise NotImplementedError("子类必须实现rerank方法")


class BGEReranker(BaseReranker):
    """
    BGE-Reranker重排序器
    使用BAAI/bge-reranker-large模型
    
    优点:
    1. 中文效果优秀
    2. Cross-Encoder架构，精准计算query-doc相关性
    3. 支持长文本
    """
    
    def __init__(self, model_name: str = None):
        self.model_name = model_name or settings.RERANKER_MODEL
        self._model = None
        self._tokenizer = None
    
    def _load_model(self):
        """延迟加载模型"""
        if self._model is None:
            try:
                import torch
                from transformers import AutoModelForSequenceClassification, AutoTokenizer
                
                logger.info(f"加载Reranker模型: {self.model_name}")
                self._tokenizer = AutoTokenizer.from_pretrained(self.model_name)
                self._model = AutoModelForSequenceClassification.from_pretrained(self.model_name)
                self._model.eval()
                
                # 使用GPU如果可用
                if torch.cuda.is_available():
                    self._model = self._model.to("cuda")
                    logger.info("Reranker使用GPU加速")
                
            except ImportError as e:
                logger.warning(f"transformers库未安装，Reranker降级为相似度排序: {e}")
                self._model = None
    
    async def rerank(
        self,
        query: str,
        candidates: List[Dict[str, Any]],
        top_k: int = 5,
    ) -> List[RerankResult]:
        """重排序"""
        if not candidates:
            return []
        
        # 尝试加载模型
        self._load_model()
        
        # 如果模型加载失败，降级为基于原始分数排序
        if self._model is None:
            return self._fallback_rerank(query, candidates, top_k)
        
        try:
            return await self._model_rerank(query, candidates, top_k)
        except Exception as e:
            logger.error(f"Reranker模型调用失败: {e}")
            return self._fallback_rerank(query, candidates, top_k)
    
    async def _model_rerank(
        self,
        query: str,
        candidates: List[Dict[str, Any]],
        top_k: int,
    ) -> List[RerankResult]:
        """使用模型进行重排序"""
        import torch
        
        # 构建输入对
        pairs = []
        for c in candidates:
            content = c.get("content", "")[:512]  # 截断长文本
            pairs.append([query, content])
        
        # Tokenize
        with torch.no_grad():
            inputs = self._tokenizer(
                pairs,
                padding=True,
                truncation=True,
                return_tensors="pt",
                max_length=512,
            )
            
            if torch.cuda.is_available():
                inputs = {k: v.to("cuda") for k, v in inputs.items()}
            
            # 计算分数
            scores = self._model(**inputs).logits.squeeze(-1)
            scores = torch.sigmoid(scores).cpu().numpy().tolist()
        
        # 排序
        scored_candidates = list(zip(candidates, scores))
        scored_candidates.sort(key=lambda x: x[1], reverse=True)
        
        # 构建结果
        results = []
        for rank, (c, score) in enumerate(scored_candidates[:top_k]):
            results.append(RerankResult(
                chunk_id=c.get("chunk_id", ""),
                content=c.get("content", ""),
                score=score,
                document_name=c.get("document_name", ""),
                page_num=c.get("page_num", 0),
                original_rank=candidates.index(c),
                rerank_score=score,
            ))
        
        logger.info(f"BGE-Reranker重排序完成: candidates={len(candidates)}, top_k={top_k}")
        return results
    
    def _fallback_rerank(
        self,
        query: str,
        candidates: List[Dict[str, Any]],
        top_k: int,
    ) -> List[RerankResult]:
        """降级方案：使用原始分数排序"""
        sorted_candidates = sorted(
            candidates,
            key=lambda x: x.get("score", 0),
            reverse=True
        )[:top_k]
        
        results = []
        for rank, c in enumerate(sorted_candidates):
            results.append(RerankResult(
                chunk_id=c.get("chunk_id", ""),
                content=c.get("content", ""),
                score=c.get("score", 0),
                document_name=c.get("document_name", ""),
                page_num=c.get("page_num", 0),
                original_rank=rank,
                rerank_score=c.get("score", 0),
            ))
        
        logger.info(f"Reranker降级排序: top_k={top_k}")
        return results


class APIReranker(BaseReranker):
    """
    API重排序器
    调用远程API进行重排序（如Jina、Cohere等）
    """
    
    def __init__(self, api_base: str, api_key: str):
        self.api_base = api_base
        self.api_key = api_key
        self._client = None
    
    def _get_client(self):
        if self._client is None:
            import httpx
            self._client = httpx.AsyncClient(
                base_url=self.api_base,
                headers={"Authorization": f"Bearer {self.api_key}"},
                timeout=30.0,
            )
        return self._client
    
    async def rerank(
        self,
        query: str,
        candidates: List[Dict[str, Any]],
        top_k: int = 5,
    ) -> List[RerankResult]:
        """调用API重排序"""
        client = self._get_client()
        
        # 构建请求
        documents = [c.get("content", "")[:1024] for c in candidates]
        
        payload = {
            "model": "rerank",
            "query": query,
            "documents": documents,
            "top_n": top_k,
        }
        
        try:
            response = await client.post("/rerank", json=payload)
            response.raise_for_status()
            data = response.json()
            
            results = []
            for item in data.get("results", []):
                idx = item.get("index", 0)
                if idx < len(candidates):
                    c = candidates[idx]
                    results.append(RerankResult(
                        chunk_id=c.get("chunk_id", ""),
                        content=c.get("content", ""),
                        score=item.get("relevance_score", 0),
                        document_name=c.get("document_name", ""),
                        page_num=c.get("page_num", 0),
                        original_rank=idx,
                        rerank_score=item.get("relevance_score", 0),
                    ))
            
            return results
            
        except Exception as e:
            logger.error(f"API Reranker调用失败: {e}")
            # 降级
            return BGEReranker()._fallback_rerank(query, candidates, top_k)


class RerankerService:
    """
    重排序服务 - 统一入口
    支持:
    1. 本地模型重排序 (BGE-Reranker)
    2. API重排序 (Jina, Cohere)
    3. 失败降级
    """
    
    def __init__(self):
        self._reranker: BaseReranker = None
    
    def _get_reranker(self) -> BaseReranker:
        """获取重排序器实例"""
        if self._reranker is None:
            # 优先使用本地模型
            if settings.RERANKER_MODEL:
                self._reranker = BGEReranker(settings.RERANKER_MODEL)
            else:
                # 降级
                self._reranker = BGEReranker(None)
        return self._reranker
    
    async def rerank(
        self,
        query: str,
        candidates: List[Dict[str, Any]],
        top_k: int = None,
    ) -> List[RerankResult]:
        """
        重排序入口
        
        Args:
            query: 查询文本
            candidates: 候选文档列表
            top_k: 返回数量
            
        Returns:
            重排序后的结果列表
        """
        top_k = top_k or settings.RERANK_TOP_K
        
        if not candidates:
            return []
        
        reranker = self._get_reranker()
        return await reranker.rerank(query, candidates, top_k)
    
    async def rerank_and_filter(
        self,
        query: str,
        candidates: List[Dict[str, Any]],
        top_k: int = None,
        score_threshold: float = 0.5,
    ) -> List[RerankResult]:
        """
        重排序并过滤低相关性结果
        
        Args:
            query: 查询文本
            candidates: 候选文档列表
            top_k: 返回数量
            score_threshold: 相关性阈值
        """
        results = await self.rerank(query, candidates, top_k)
        
        # 过滤低相关性结果
        filtered = [r for r in results if r.rerank_score >= score_threshold]
        
        if len(filtered) < len(results):
            logger.info(f"Reranker过滤低相关性结果: {len(results)} -> {len(filtered)}")
        
        return filtered


# 全局实例
reranker_service = RerankerService()
