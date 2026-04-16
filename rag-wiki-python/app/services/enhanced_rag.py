"""
增强型RAG引擎
支持多路召回、重排序、查询理解、上下文构建
"""
import logging
from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from typing import List, Dict, Any, Optional, Tuple
import json

logger = logging.getLogger(__name__)


@dataclass
class SearchResult:
    """检索结果"""
    chunk_id: str
    document_id: str
    content: str
    score: float
    metadata: Dict[str, Any] = field(default_factory=dict)
    rerank_score: Optional[float] = None


@dataclass
class QueryUnderstanding:
    """查询理解结果"""
    original_query: str
    expanded_queries: List[str]
    intent: str
    entities: List[Dict[str, str]]
    keywords: List[str]


class BaseRetriever(ABC):
    """检索器基类"""
    
    @abstractmethod
    def retrieve(self, query: str, top_k: int = 10, 
                 filters: Optional[Dict] = None) -> List[SearchResult]:
        """执行检索"""
        pass


class VectorRetriever(BaseRetriever):
    """向量语义检索器"""
    
    def __init__(self, embedding_service=None, vector_store=None):
        self.embedding_service = embedding_service
        self.vector_store = vector_store
    
    def retrieve(self, query: str, top_k: int = 10, 
                 filters: Optional[Dict] = None) -> List[SearchResult]:
        """向量语义检索"""
        try:
            # 生成查询向量
            query_embedding = self.embedding_service.embed_query(query)
            
            # 向量库检索
            results = self.vector_store.search(
                query_embedding, 
                top_k=top_k * 2,  # 多召回一些供后续重排序
                filters=filters
            )
            
            search_results = []
            for r in results:
                search_results.append(SearchResult(
                    chunk_id=r.get("chunk_id", ""),
                    document_id=r.get("document_id", ""),
                    content=r.get("content", ""),
                    score=r.get("score", 0.0),
                    metadata=r.get("metadata", {})
                ))
            
            logger.info(f"向量检索完成: query='{query[:30]}...', 召回{len(search_results)}条")
            return search_results
            
        except Exception as e:
            logger.error(f"向量检索失败: {e}")
            return []


class BM25Retriever(BaseRetriever):
    """BM25关键词检索器"""
    
    def __init__(self, elasticsearch_client=None):
        self.es = elasticsearch_client
    
    def retrieve(self, query: str, top_k: int = 10, 
                 filters: Optional[Dict] = None) -> List[SearchResult]:
        """BM25关键词检索"""
        try:
            if not self.es:
                logger.warning("Elasticsearch未配置，跳过BM25检索")
                return []
            
            # 构建ES查询
            es_query = {
                "bool": {
                    "must": [
                        {"match": {"content": query}}
                    ]
                }
            }
            
            if filters:
                for key, value in filters.items():
                    es_query["bool"]["filter"] = es_query["bool"].get("filter", [])
                    es_query["bool"]["filter"].append({"term": {key: value}})
            
            response = self.es.search(
                index="document_chunks",
                body={
                    "query": es_query,
                    "size": top_k * 2,
                    "_source": ["chunk_id", "document_id", "content", "metadata"]
                }
            )
            
            search_results = []
            for hit in response["hits"]["hits"]:
                source = hit["_source"]
                search_results.append(SearchResult(
                    chunk_id=source.get("chunk_id", ""),
                    document_id=source.get("document_id", ""),
                    content=source.get("content", ""),
                    score=hit["_score"],
                    metadata=source.get("metadata", {})
                ))
            
            logger.info(f"BM25检索完成: query='{query[:30]}...', 召回{len(search_results)}条")
            return search_results
            
        except Exception as e:
            logger.error(f"BM25检索失败: {e}")
            return []


class Reranker:
    """重排序器 - 基于Cross-Encoder"""
    
    def __init__(self, model_name: str = "BAAI/bge-reranker-base"):
        self.model_name = model_name
        self.model = None
        self.tokenizer = None
        self._load_model()
    
    def _load_model(self):
        """加载重排序模型"""
        try:
            from transformers import AutoTokenizer, AutoModelForSequenceClassification
            import torch
            
            self.tokenizer = AutoTokenizer.from_pretrained(self.model_name)
            self.model = AutoModelForSequenceClassification.from_pretrained(self.model_name)
            self.model.eval()
            logger.info(f"重排序模型加载成功: {self.model_name}")
        except Exception as e:
            logger.warning(f"重排序模型加载失败: {e}，将使用原始分数")
    
    def rerank(self, query: str, results: List[SearchResult], 
               top_k: int = 5) -> List[SearchResult]:
        """对检索结果重排序"""
        if not self.model or not results:
            # 按原始分数排序
            results.sort(key=lambda x: x.score, reverse=True)
            return results[:top_k]
        
        try:
            import torch
            
            # 准备输入
            pairs = [[query, r.content] for r in results]
            
            # 批量编码
            with torch.no_grad():
                inputs = self.tokenizer(
                    pairs, 
                    padding=True, 
                    truncation=True, 
                    return_tensors="pt", 
                    max_length=512
                )
                scores = self.model(**inputs).logits.squeeze(-1)
                
                if scores.dim() == 0:
                    scores = scores.unsqueeze(0)
                
                rerank_scores = torch.sigmoid(scores).tolist()
            
            # 更新重排序分数
            for i, result in enumerate(results):
                result.rerank_score = rerank_scores[i] if i < len(rerank_scores) else 0.0
            
            # 按重排序分数排序
            results.sort(key=lambda x: x.rerank_score or 0, reverse=True)
            
            logger.info(f"重排序完成: 输入{len(results)}条，输出{min(top_k, len(results))}条")
            return results[:top_k]
            
        except Exception as e:
            logger.error(f"重排序失败: {e}")
            results.sort(key=lambda x: x.score, reverse=True)
            return results[:top_k]


class QueryUnderstandingService:
    """查询理解服务"""
    
    def __init__(self, llm_service=None):
        self.llm_service = llm_service
    
    def understand(self, query: str) -> QueryUnderstanding:
        """理解用户查询"""
        # 扩展查询
        expanded_queries = self._expand_query(query)
        
        # 识别意图
        intent = self._detect_intent(query)
        
        # 提取实体
        entities = self._extract_entities(query)
        
        # 提取关键词
        keywords = self._extract_keywords(query)
        
        return QueryUnderstanding(
            original_query=query,
            expanded_queries=expanded_queries,
            intent=intent,
            entities=entities,
            keywords=keywords
        )
    
    def _expand_query(self, query: str) -> List[str]:
        """查询扩展 - 生成同义/相关查询"""
        expansions = [query]
        
        # 简单的同义词扩展
        synonyms = {
            "如何": ["怎么", "怎样", "用什么方法"],
            "安装": ["部署", "配置", "搭建"],
            "问题": ["故障", "错误", "异常"],
            "文档": ["资料", "手册", "说明"],
        }
        
        for word, alts in synonyms.items():
            if word in query:
                for alt in alts:
                    new_query = query.replace(word, alt)
                    if new_query not in expansions:
                        expansions.append(new_query)
        
        return expansions[:3]  # 限制扩展数量
    
    def _detect_intent(self, query: str) -> str:
        """识别查询意图"""
        # 简单规则匹配
        if any(w in query for w in ["如何", "怎么", "怎样", "步骤"]):
            return "how_to"
        elif any(w in query for w in ["是什么", "什么是", "介绍"]):
            return "what_is"
        elif any(w in query for w in ["为什么", "原因"]):
            return "why"
        elif any(w in query for w in ["对比", "区别", "差异"]):
            return "compare"
        elif any(w in query for w in ["故障", "错误", "异常", "问题"]):
            return "troubleshooting"
        else:
            return "general"
    
    def _extract_entities(self, query: str) -> List[Dict[str, str]]:
        """提取命名实体"""
        entities = []
        
        # 简单规则提取
        # 产品/系统名称（大写字母或特定词汇）
        import re
        
        # 提取可能的产品名（大写缩写或包含数字的词）
        product_pattern = r'\b[A-Z]{2,}\d*\b'
        for match in re.finditer(product_pattern, query):
            entities.append({
                "text": match.group(),
                "type": "product",
                "start": match.start(),
                "end": match.end()
            })
        
        # 提取版本号
        version_pattern = r'v?\d+\.\d+(?:\.\d+)?'
        for match in re.finditer(version_pattern, query):
            entities.append({
                "text": match.group(),
                "type": "version",
                "start": match.start(),
                "end": match.end()
            })
        
        return entities
    
    def _extract_keywords(self, query: str) -> List[str]:
        """提取关键词"""
        # 简单的关键词提取 - 去除停用词
        stopwords = {"的", "了", "是", "在", "我", "有", "和", "就", "不", "人", "都", "一", "一个", "上", "也", "很", "到", "说", "要", "去", "你", "会", "着", "没有", "看", "好", "自己", "这"}
        
        words = query.split()
        keywords = [w for w in words if w not in stopwords and len(w) > 1]
        
        return keywords[:5]


class EnhancedRAGService:
    """增强型RAG服务"""
    
    def __init__(self, 
                 embedding_service=None,
                 vector_store=None,
                 elasticsearch_client=None,
                 llm_service=None):
        self.embedding_service = embedding_service
        self.vector_store = vector_store
        self.llm_service = llm_service
        
        # 初始化检索器
        self.vector_retriever = VectorRetriever(embedding_service, vector_store)
        self.bm25_retriever = BM25Retriever(elasticsearch_client)
        
        # 初始化重排序器
        self.reranker = Reranker()
        
        # 初始化查询理解
        self.query_understanding = QueryUnderstandingService(llm_service)
    
    def multi_recall(self, query: str, top_k: int = 10, 
                     filters: Optional[Dict] = None,
                     vector_weight: float = 0.7,
                     bm25_weight: float = 0.3) -> List[SearchResult]:
        """多路召回 - 向量+关键词混合检索"""
        logger.info(f"开始多路召回: query='{query[:50]}...'")
        
        # 1. 查询理解
        understood = self.query_understanding.understand(query)
        logger.info(f"查询理解: intent={understood.intent}, entities={len(understood.entities)}")
        
        # 2. 并行检索
        vector_results = self.vector_retriever.retrieve(
            understood.original_query, top_k, filters
        )
        
        # 使用扩展查询进行补充检索
        for expanded_query in understood.expanded_queries[1:]:  # 跳过原始查询
            more_results = self.vector_retriever.retrieve(expanded_query, top_k // 2, filters)
            vector_results.extend(more_results)
        
        bm25_results = self.bm25_retriever.retrieve(
            understood.original_query, top_k, filters
        )
        
        # 3. 结果融合
        merged_results = self._merge_results(
            vector_results, bm25_results, 
            vector_weight, bm25_weight
        )
        
        logger.info(f"多路召回完成: 共{len(merged_results)}条结果")
        return merged_results
    
    def _merge_results(self, vector_results: List[SearchResult],
                       bm25_results: List[SearchResult],
                       vector_weight: float,
                       bm25_weight: float) -> List[SearchResult]:
        """融合多路检索结果"""
        # 使用RRF (Reciprocal Rank Fusion) 融合
        k = 60  # RRF常数
        
        scores = {}
        
        # 向量检索分数
        for rank, result in enumerate(vector_results):
            key = result.chunk_id
            if key not in scores:
                scores[key] = {"result": result, "score": 0}
            scores[key]["score"] += vector_weight * (1.0 / (k + rank + 1))
        
        # BM25检索分数
        for rank, result in enumerate(bm25_results):
            key = result.chunk_id
            if key not in scores:
                scores[key] = {"result": result, "score": 0}
            scores[key]["score"] += bm25_weight * (1.0 / (k + rank + 1))
        
        # 排序
        merged = sorted(scores.values(), key=lambda x: x["score"], reverse=True)
        
        # 更新分数
        for item in merged:
            item["result"].score = item["score"]
        
        return [item["result"] for item in merged]
    
    def retrieve_and_rerank(self, query: str, final_top_k: int = 5,
                           recall_top_k: int = 20,
                           filters: Optional[Dict] = None) -> List[SearchResult]:
        """检索并重排序"""
        # 1. 多路召回
        recalled = self.multi_recall(query, recall_top_k, filters)
        
        if not recalled:
            return []
        
        # 2. 去重
        seen_chunks = set()
        unique_results = []
        for r in recalled:
            if r.chunk_id not in seen_chunks:
                seen_chunks.add(r.chunk_id)
                unique_results.append(r)
        
        # 3. 重排序
        reranked = self.reranker.rerank(query, unique_results, final_top_k)
        
        return reranked
    
    def build_context(self, results: List[SearchResult], 
                      max_tokens: int = 3000) -> str:
        """构建上下文"""
        context_parts = []
        current_tokens = 0
        
        for i, result in enumerate(results, 1):
            content = result.content
            # 简单估算token数 (中文约1字=1token)
            estimated_tokens = len(content)
            
            if current_tokens + estimated_tokens > max_tokens:
                break
            
            context_parts.append(f"[文档{i}] {content}")
            current_tokens += estimated_tokens
        
        return "\n\n".join(context_parts)
    
    def answer_with_rag(self, query: str, user_id: str = None,
                       session_id: str = None,
                       filters: Optional[Dict] = None) -> Dict[str, Any]:
        """完整的RAG问答流程"""
        logger.info(f"开始RAG问答: query='{query[:50]}...'")
        
        # 1. 检索相关文档
        results = self.retrieve_and_rerank(query, filters=filters)
        
        if not results:
            return {
                "answer": "抱歉，未找到相关知识。",
                "sources": [],
                "confidence": 0.0
            }
        
        # 2. 构建上下文
        context = self.build_context(results)
        
        # 3. 构建Prompt
        prompt = self._build_rag_prompt(query, context)
        
        # 4. 调用LLM生成答案
        answer = self.llm_service.generate(prompt) if self.llm_service else "LLM服务未配置"
        
        # 5. 计算置信度
        confidence = self._calculate_confidence(results)
        
        # 6. 构建来源引用
        sources = [
            {
                "chunk_id": r.chunk_id,
                "document_id": r.document_id,
                "content_preview": r.content[:200] + "..." if len(r.content) > 200 else r.content,
                "score": r.rerank_score or r.score
            }
            for r in results[:3]
        ]
        
        return {
            "answer": answer,
            "sources": sources,
            "confidence": confidence,
            "retrieved_count": len(results)
        }
    
    def _build_rag_prompt(self, query: str, context: str) -> str:
        """构建RAG Prompt"""
        prompt = f"""基于以下参考文档回答问题。如果参考文档中没有相关信息，请明确说明。

参考文档：
{context}

用户问题：{query}

请基于上述参考文档提供准确、简洁的回答。如果引用了参考文档中的信息，请在回答中标注来源[文档X]。

回答："""
        return prompt
    
    def _calculate_confidence(self, results: List[SearchResult]) -> float:
        """计算答案置信度"""
        if not results:
            return 0.0
        
        # 基于重排序分数计算
        scores = [r.rerank_score or r.score for r in results[:3]]
        avg_score = sum(scores) / len(scores) if scores else 0
        
        return round(min(1.0, avg_score), 2)


# 全局RAG服务实例
rag_service = None

def init_rag_service(embedding_service, vector_store, llm_service):
    """初始化RAG服务"""
    global rag_service
    rag_service = EnhancedRAGService(
        embedding_service=embedding_service,
        vector_store=vector_store,
        elasticsearch_client=None,  # 可选
        llm_service=llm_service
    )
    return rag_service