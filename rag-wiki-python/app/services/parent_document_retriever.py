"""
父文档检索/上下文增强服务

核心策略：
1. 分块时保留父子关系 - 每个chunk记录其所属的parent_document_id
2. 检索时先匹配细粒度chunks，再自动关联其父文档
3. 父文档提供更完整的上下文，提升回答质量
4. 支持递归上下文窗口 - 检索到chunk后，可获取其前后兄弟chunks
"""
import asyncio
import logging
from typing import List, Dict, Any, Optional

from app.services.vector_db import vector_db
from app.services.embedding import embedding_service

logger = logging.getLogger(__name__)


class ParentDocumentRetriever:
    """父文档检索/上下文增强"""

    def __init__(self):
        self._context_window_size = 3  # 前后各取3个兄弟chunk

    async def search_with_parent_context(
        self,
        query: str,
        collection_name: str = "rag_wiki",
        top_k: int = 5,
        include_parent: bool = True,
        include_siblings: bool = True,
        context_window: int = 3,
    ) -> Dict[str, Any]:
        """
        检索时自动关联父文档和兄弟chunks

        Args:
            query: 查询文本
            collection_name: 向量集合名
            top_k: 返回结果数
            include_parent: 是否包含父文档
            include_siblings: 是否包含兄弟chunks
            context_window: 上下文窗口大小

        Returns:
            增强后的检索结果
        """
        try:
            # Step 1: 正常检索最相关的chunks
            query_embedding = await embedding_service.get_embedding(query)
            if not query_embedding:
                return {"chunks": [], "enhanced_context": ""}

            results = await vector_db.search(
                collection_name=collection_name,
                query_vector=query_embedding,
                top_k=top_k,
            )

            if not results:
                return {"chunks": [], "enhanced_context": ""}

            # Step 2: 增强每个结果
            enhanced_chunks = []
            parent_cache = {}  # 缓存已获取的父文档
            all_context_texts = []

            for result in results:
                chunk = {
                    "chunk_id": result.get("chunk_id", ""),
                    "content": result.get("content", ""),
                    "score": result.get("score", 0.0),
                    "metadata": result.get("metadata", {}),
                    "parent_content": None,
                    "sibling_contents": [],
                }

                # 获取父文档内容
                if include_parent:
                    parent_id = result.get("metadata", {}).get("parent_document_id")
                    if parent_id:
                        if parent_id in parent_cache:
                            chunk["parent_content"] = parent_cache[parent_id]
                        else:
                            parent_content = await self._fetch_parent_document(
                                collection_name, parent_id
                            )
                            parent_cache[parent_id] = parent_content
                            chunk["parent_content"] = parent_content

                # 获取兄弟chunks（前后文）
                if include_siblings:
                    chunk_index = result.get("metadata", {}).get("chunk_index", 0)
                    document_id = result.get("metadata", {}).get("document_id", "")

                    if document_id:
                        siblings = await self._fetch_sibling_chunks(
                            collection_name=collection_name,
                            document_id=document_id,
                            center_index=chunk_index,
                            window_size=context_window,
                        )
                        chunk["sibling_contents"] = siblings

                enhanced_chunks.append(chunk)

                # 构建增强上下文文本
                context_parts = []
                if chunk.get("parent_content"):
                    context_parts.append(f"[父文档摘要]: {chunk['parent_content']}")
                if chunk.get("sibling_contents"):
                    for sib in chunk["sibling_contents"]:
                        context_parts.append(f"[上下文]: {sib}")
                context_parts.append(f"[相关片段]: {chunk['content']}")
                all_context_texts.append("\n".join(context_parts))

            enhanced_context = "\n\n---\n\n".join(all_context_texts)

            return {
                "chunks": enhanced_chunks,
                "enhanced_context": enhanced_context,
                "total_chunks": len(enhanced_chunks),
                "parents_retrieved": len(parent_cache),
            }

        except Exception as e:
            logger.error(f"父文档检索失败: {e}")
            return {"chunks": [], "enhanced_context": "", "error": str(e)}

    async def _fetch_parent_document(
        self,
        collection_name: str,
        parent_id: str
    ) -> Optional[str]:
        """获取父文档内容"""
        try:
            # 查询parent_document_id对应的文档（通常是chunk_index=0的chunk）
            results = await vector_db.query(
                collection_name=collection_name,
                limit=1,
                output_fields=["content", "metadata"],
            )
            # 过滤找到匹配的父文档
            for r in results:
                metadata = r.get("metadata", {})
                if isinstance(metadata, str):
                    import json
                    try:
                        metadata = json.loads(metadata)
                    except:
                        metadata = {}

                if metadata.get("document_id") == parent_id and metadata.get("chunk_index", -1) == 0:
                    return r.get("content", "")
                elif metadata.get("parent_document_id") == parent_id:
                    return r.get("content", "")

            # 如果没找到，尝试用原始文档ID查询
            return None

        except Exception as e:
            logger.warning(f"获取父文档失败: {e}")
            return None

    async def _fetch_sibling_chunks(
        self,
        collection_name: str,
        document_id: str,
        center_index: int,
        window_size: int = 3
    ) -> List[str]:
        """获取指定chunk前后的兄弟chunks"""
        try:
            start_index = max(0, center_index - window_size)
            end_index = center_index + window_size + 1

            siblings = []
            # 查询同document_id的所有chunks
            results = await vector_db.query(
                collection_name=collection_name,
                limit=50,
                output_fields=["content", "metadata"],
            )

            for r in results:
                metadata = r.get("metadata", {})
                if isinstance(metadata, str):
                    import json
                    try:
                        metadata = json.loads(metadata)
                    except:
                        metadata = {}

                doc_id = metadata.get("document_id", "")
                chunk_idx = metadata.get("chunk_index", -1)

                if doc_id == document_id and start_index <= chunk_idx < end_index:
                    content = r.get("content", "")
                    if chunk_idx != center_index:  # 排除自身
                        siblings.append(content)

            return siblings[:window_size * 2]  # 限制数量

        except Exception as e:
            logger.warning(f"获取兄弟chunks失败: {e}")
            return []

    def build_chunk_metadata(
        self,
        document_id: str,
        chunk_index: int,
        total_chunks: int,
        parent_document_id: Optional[str] = None,
        space_id: Optional[str] = None,
        security_level: int = 1,
    ) -> Dict[str, Any]:
        """
        构建chunk的元数据（用于分块时标记父子关系）

        应在文档分块时调用，将返回值存入向量库的metadata字段。
        """
        return {
            "document_id": document_id,
            "chunk_index": chunk_index,
            "total_chunks": total_chunks,
            "parent_document_id": parent_document_id or document_id,
            "space_id": space_id,
            "security_level": security_level,
            "has_prev": chunk_index > 0,
            "has_next": chunk_index < total_chunks - 1,
        }


# 全局实例
parent_document_retriever = ParentDocumentRetriever()
