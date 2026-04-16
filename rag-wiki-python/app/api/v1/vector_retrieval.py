"""
向量与检索服务API - 完整实现
"""
import logging
import asyncio
from typing import List, Optional
from fastapi import APIRouter, Request
from pydantic import BaseModel, Field, field_validator
from app.core.response import Result, BusinessException
from app.services.embedding import embedding_service
from app.services.chunking import chunking_service

logger = logging.getLogger(__name__)
router = APIRouter()

# BGE-Reranker 模型单例
_reranker_model = None
_reranker_lock = asyncio.Lock()


async def get_reranker():
    """
    获取 BGE-Reranker 模型实例 (懒加载)
    支持 BAAI/bge-reranker-base 和 bge-reranker-large
    """
    global _reranker_model
    if _reranker_model is not None:
        return _reranker_model

    async with _reranker_lock:
        if _reranker_model is not None:
            return _reranker_model

        try:
            from app.core.config import settings
            import torch
            from transformers import AutoModelForSequenceClassification, AutoTokenizer

            model_name = getattr(settings, 'RERANKER_MODEL', 'BAAI/bge-reranker-base')
            logger.info(f"正在加载 BGE-Reranker 模型: {model_name}")

            tokenizer = AutoTokenizer.from_pretrained(model_name)
            model = AutoModelForSequenceClassification.from_pretrained(model_name)
            model.eval()

            # 如果有 GPU，移到 GPU
            device = "cuda" if torch.cuda.is_available() else "cpu"
            model = model.to(device)

            _reranker_model = {
                "tokenizer": tokenizer,
                "model": model,
                "device": device,
            }
            logger.info(f"BGE-Reranker 模型加载完成，设备: {device}")
            return _reranker_model

        except ImportError:
            logger.warning("transformers 未安装，使用简化重排序")
            return None
        except Exception as e:
            logger.warning(f"BGE-Reranker 模型加载失败: {e}，使用简化重排序")
            return None


async def rerank_with_bge(query: str, chunks: List[dict], top_k: int = 5) -> List[dict]:
    """
    使用 BGE-Reranker 进行重排序
    Cross-Encoder 模型对 query-document 对进行精细评分
    """
    reranker = await get_reranker()
    if reranker is None:
        # 降级到简化重排序
        return _simple_rerank(chunks, top_k)

    try:
        import torch

        tokenizer = reranker["tokenizer"]
        model = reranker["model"]
        device = reranker["device"]

        # 构建查询-文档对
        pairs = [[query, chunk.get("content", "")] for chunk in chunks]

        # 批量编码
        with torch.no_grad():
            inputs = tokenizer(
                pairs,
                padding=True,
                truncation=True,
                max_length=512,
                return_tensors="pt",
            )
            inputs = {k: v.to(device) for k, v in inputs.items()}

            # 获取相关性分数
            scores = model(**inputs).logits.squeeze(-1)
            scores = torch.nn.functional.sigmoid(scores)  # 转换为概率

        # 按分数排序
        scores_list = scores.cpu().numpy().tolist()
        for i, chunk in enumerate(chunks):
            chunk["rerank_score"] = float(scores_list[i])

        sorted_chunks = sorted(chunks, key=lambda x: x["rerank_score"], reverse=True)
        return sorted_chunks[:top_k]

    except Exception as e:
        logger.error(f"BGE-Reranker 重排序失败: {e}")
        return _simple_rerank(chunks, top_k)


def _simple_rerank(chunks: List[dict], top_k: int) -> List[dict]:
    """
    简化重排序: 基于原始得分和内容长度
    当 BGE-Reranker 不可用时的降级方案
    """
    import math

    for chunk in chunks:
        # 综合得分 = 原始向量相似度 + 内容长度归一化得分
        original_score = chunk.get("score", 0.5)
        content_len = len(chunk.get("content", ""))
        length_bonus = min(content_len / 1000, 0.2)  # 长文档轻微加分

        # 计算最终得分
        chunk["rerank_score"] = min(original_score + length_bonus, 1.0)

    sorted_chunks = sorted(chunks, key=lambda x: x["rerank_score"], reverse=True)
    return sorted_chunks[:top_k]


class RerankRequest(BaseModel):
    query: str = Field(..., min_length=1, max_length=2000, description="查询文本")
    chunks: List[dict] = Field(..., min_length=1, description="待重排序的文档块列表")
    top_k: int = Field(default=5, ge=1, le=50, description="返回top-k结果")
    use_reranker: bool = Field(default=True, description="是否使用BGE-Reranker")


class EmbedRequest(BaseModel):
    chunks: List[dict] = Field(..., min_length=1, description="文档块列表")
    space_id: str = Field(..., min_length=1, max_length=128, description="知识库空间ID")
    security_level: int = Field(default=1, ge=1, le=4, description="安全等级1-4")
    chunk_strategy: str = Field(default="recursive", pattern="^(fixed|recursive|semantic|structure)$", description="分块策略")

    @field_validator('chunks')
    @classmethod
    def validate_chunks(cls, v):
        for chunk in v:
            if not chunk.get('content'):
                raise ValueError('每个chunk必须包含content字段')
        return v


class SearchRequest(BaseModel):
    query: str = Field(..., min_length=1, max_length=2000, description="查询文本")
    space_id: Optional[str] = Field(default=None, max_length=128, description="空间ID")
    top_k: int = Field(default=10, ge=1, le=100, description="返回结果数")
    security_level: int = Field(default=1, ge=1, le=4, description="用户安全等级1-4")
    dept_id: Optional[str] = Field(default=None, max_length=128, description="部门ID")
    role_ids: Optional[List[str]] = Field(default=None, description="角色ID列表")
    user_id: Optional[str] = Field(default=None, max_length=128, description="用户ID")
    search_type: str = Field(default="hybrid", pattern="^(vector|keyword|hybrid)$", description="检索类型")


class SearchResponse(BaseModel):
    chunks: List[dict]
    total: int


@router.post("/embed", summary="向量化入库")
async def embed_and_store(request: EmbedRequest):
    """
    文本分块 + Embedding向量化 + 入库
    流程: 原始chunks → (可选)二次分块 → Embedding → 权限元数据绑定 → 写入向量数据库
    """
    from app.core.vector_db import get_vector_db_instance

    try:
        vector_db = get_vector_db_instance()
        dimension = embedding_service.get_dimension()

        # 确保集合存在
        collection_name = f"space_{request.space_id}"
        await vector_db.create_collection(collection_name, dimension)

        # Embedding向量化
        texts = [c.get("content", "") for c in request.chunks]
        vectors = await embedding_service.embed_texts(texts)

        # 构建带权限元数据的数据
        data = []
        for i, chunk in enumerate(request.chunks):
            item = {
                "chunk_id": chunk.get("chunk_id", f"chunk_{i}"),
                "document_id": chunk.get("document_id", ""),
                "document_name": chunk.get("document_name", ""),
                "space_id": request.space_id,
                "content": chunk.get("content", ""),
                "chunk_index": chunk.get("chunk_index", i),
                "page_num": chunk.get("page_num", 0),
                "security_level": chunk.get("security_level", request.security_level),
                "owning_dept_id": chunk.get("owning_dept_id", ""),
                "allowed_dept_ids": chunk.get("allowed_dept_ids", ""),
                "allowed_role_ids": chunk.get("allowed_role_ids", ""),
                "vector": vectors[i],
            }
            data.append(item)

        # 批量写入向量数据库
        ids = await vector_db.insert(collection_name, data)

        logger.info(f"向量化入库完成: space_id={request.space_id}, count={len(ids)}")
        return Result.success(data={"embedded_count": len(ids), "chunk_ids": ids})

    except Exception as e:
        logger.error(f"向量化入库失败: {e}")
        raise BusinessException(code=5002, message=f"向量化入库失败: {str(e)}")


@router.post("/search", summary="向量相似度检索")
async def vector_search(request: SearchRequest, req: Request):
    """向量语义检索，支持权限前置过滤"""
    from app.core.vector_db import get_vector_db_instance

    try:
        vector_db = get_vector_db_instance()

        # 构建权限过滤表达式
        filter_parts = [f'security_level <= {request.security_level}']
        if request.space_id:
            filter_parts.append(f'space_id == "{request.space_id}"')
        if request.dept_id:
            filter_parts.append(f'(owning_dept_id == "{request.dept_id}" || owning_dept_id == "")')
        filter_expr = ' and '.join(filter_parts)

        # 查询向量化
        query_vector = await embedding_service.embed_text(request.query)

        # 执行检索
        collection_name = f"space_{request.space_id}" if request.space_id else "knowledge_chunks"
        results = await vector_db.search(
            collection_name=collection_name,
            query_vector=query_vector,
            top_k=request.top_k,
            filter_expr=filter_expr,
        )

        logger.info(f"向量检索完成: query={request.query[:50]}, results={len(results)}")
        return Result.success(data={"chunks": results, "total": len(results)})

    except Exception as e:
        logger.error(f"向量检索失败: {e}")
        raise BusinessException(code=5003, message=f"检索失败: {str(e)}")


@router.post("/hybrid-search", summary="混合检索")
async def hybrid_search(request: SearchRequest, req: Request):
    """混合检索：向量语义召回 + BM25关键词召回"""
    from app.core.vector_db import get_vector_db_instance

    try:
        vector_db = get_vector_db_instance()

        filter_parts = [f'security_level <= {request.security_level}']
        if request.space_id:
            filter_parts.append(f'space_id == "{request.space_id}"')
        filter_expr = ' and '.join(filter_parts)

        query_vector = await embedding_service.embed_text(request.query)

        collection_name = f"space_{request.space_id}" if request.space_id else "knowledge_chunks"
        results = await vector_db.hybrid_search(
            collection_name=collection_name,
            query_vector=query_vector,
            query_text=request.query,
            top_k=request.top_k,
            filter_expr=filter_expr,
        )

        logger.info(f"混合检索完成: query={request.query[:50]}, results={len(results)}")
        return Result.success(data={"chunks": results, "total": len(results)})

    except Exception as e:
        logger.error(f"混合检索失败: {e}")
        raise BusinessException(code=5003, message=f"混合检索失败: {str(e)}")


@router.post("/rerank", summary="重排序")
async def rerank(request: RerankRequest):
    """
    对召回结果进行 Cross-Encoder 重排序
    支持 BGE-Reranker 模型进行精细相关性评分
    """
    try:
        if not request.chunks:
            return Result.success(data={"chunks": [], "total": 0})

        # 使用 BGE-Reranker 或简化重排序
        if request.use_reranker:
            reranked = await rerank_with_bge(
                query=request.query,
                chunks=request.chunks,
                top_k=request.top_k,
            )
        else:
            reranked = _simple_rerank(request.chunks, request.top_k)

        logger.info(f"重排序完成: query={request.query[:30]}, input={len(request.chunks)}, output={len(reranked)}")
        return Result.success(data={"chunks": reranked, "total": len(reranked)})

    except Exception as e:
        logger.error(f"重排序失败: {e}")
        raise BusinessException(code=5005, message=f"重排序失败: {str(e)}")


@router.delete("/chunks/{space_id}", summary="删除空间下所有向量数据")
async def delete_space_chunks(space_id: str):
    """删除指定知识库空间下的所有向量数据"""
    from app.core.vector_db import get_vector_db_instance

    try:
        vector_db = get_vector_db_instance()
        collection_name = f"space_{space_id}"
        await vector_db.delete_by_filter(collection_name, f'space_id == "{space_id}"')
        return Result.success()
    except Exception as e:
        logger.error(f"删除向量数据失败: {e}")
        raise BusinessException(code=5004, message=f"删除失败: {str(e)}")
