"""
向量库管理API - 统计/查询/删除/重建索引
供Java后端通过Feign调用，管理Milvus/Qdrant向量库
"""
import logging
from typing import Optional
from fastapi import APIRouter
from pydantic import BaseModel, Field
from app.core.response import Result, BusinessException

logger = logging.getLogger(__name__)
router = APIRouter()


class VectorStatsRequest(BaseModel):
    """向量库统计请求"""
    collection: Optional[str] = Field(default=None, description="集合名称，为空则统计所有")


class VectorPageRequest(BaseModel):
    """向量分页查询请求"""
    collection: Optional[str] = Field(default=None, description="集合名称")
    keyword: Optional[str] = Field(default=None, description="关键词")
    page_num: int = Field(default=1, ge=1, description="页码")
    page_size: int = Field(default=20, ge=1, le=100, description="每页数量")


class VectorDeleteRequest(BaseModel):
    """向量删除请求"""
    vector_id: str = Field(..., min_length=1, description="向量ID")
    collection: Optional[str] = Field(default=None, description="集合名称")


class RebuildIndexRequest(BaseModel):
    """索引重建请求"""
    task_id: str = Field(..., min_length=1, description="任务ID")
    collection: Optional[str] = Field(default=None, description="集合名称，为空则重建所有")


@router.get("/stats", summary="获取向量库统计")
async def get_vector_stats(collection: Optional[str] = None):
    """
    获取向量库整体统计信息
    - 总文档数、总向量数
    - 存储大小、集合数量
    - 索引类型、度量类型、维度
    """
    try:
        from app.core.vector_db import get_vector_db_instance
        vector_db = get_vector_db_instance()

        stats = {
            "totalDocuments": 0,
            "totalVectors": 0,
            "storageSize": "0 MB",
            "collectionCount": 0,
            "indexType": "IVF_FLAT",
            "metricType": "COSINE",
            "dimension": 1024,
            "collections": [],
        }

        # 尝试获取实际统计
        try:
            if collection:
                count = await vector_db.count(f"space_{collection}")
                stats["totalVectors"] = count
                stats["collectionCount"] = 1
                stats["collections"] = [{
                    "name": collection,
                    "vectorCount": count,
                }]
            else:
                # 统计所有集合
                collections = await vector_db.list_collections()
                stats["collectionCount"] = len(collections) if collections else 0
                total = 0
                coll_list = []
                if collections:
                    for coll_name in collections:
                        try:
                            count = await vector_db.count(coll_name)
                            total += count
                            coll_list.append({
                                "name": coll_name,
                                "vectorCount": count,
                            })
                        except Exception:
                            coll_list.append({
                                "name": coll_name,
                                "vectorCount": 0,
                            })
                stats["totalVectors"] = total
                stats["collections"] = coll_list
        except Exception as e:
            logger.warning(f"获取向量库详细统计失败，返回默认值: {e}")

        return Result.success(data=stats)

    except Exception as e:
        logger.error(f"获取向量库统计失败: {e}")
        raise BusinessException(code=5006, message=f"获取统计失败: {str(e)}")


@router.get("/page", summary="分页查询向量数据")
async def page_vectors(
    collection: Optional[str] = None,
    keyword: Optional[str] = None,
    page_num: int = 1,
    page_size: int = 20,
):
    """
    分页查询向量数据
    支持按集合和关键词筛选
    """
    try:
        from app.core.vector_db import get_vector_db_instance
        vector_db = get_vector_db_instance()

        records = []
        total = 0

        if collection:
            collection_name = f"space_{collection}"
            count = await vector_db.count(collection_name)
            total = count

            # 分页查询向量数据
            try:
                results = await vector_db.query(
                    collection_name=collection_name,
                    limit=page_size,
                    offset=(page_num - 1) * page_size,
                    output_fields=["chunk_id", "document_id", "document_name",
                                   "space_id", "chunk_index", "security_level"],
                )
                records = results if results else []
            except Exception:
                records = []

        return Result.success(data={
            "records": records,
            "total": total,
            "pageNum": page_num,
            "pageSize": page_size,
        })

    except Exception as e:
        logger.error(f"分页查询向量数据失败: {e}")
        raise BusinessException(code=5007, message=f"查询失败: {str(e)}")


@router.delete("/{vector_id}", summary="删除向量")
async def delete_vector(vector_id: str, collection: Optional[str] = None):
    """删除指定的向量数据"""
    try:
        from app.core.vector_db import get_vector_db_instance
        vector_db = get_vector_db_instance()

        if collection:
            collection_name = f"space_{collection}"
            await vector_db.delete(collection_name, [vector_id])
            logger.info(f"向量已删除: vector_id={vector_id}, collection={collection_name}")
        else:
            logger.warning(f"删除向量未指定集合: vector_id={vector_id}")

        return Result.success(data={"deleted": True, "vector_id": vector_id})

    except Exception as e:
        logger.error(f"删除向量失败: {e}")
        raise BusinessException(code=5008, message=f"删除失败: {str(e)}")


@router.post("/rebuild-index", summary="重建索引")
async def rebuild_index(request: RebuildIndexRequest):
    """
    触发索引重建任务
    实际重建为异步操作，此处仅触发并记录任务
    """
    try:
        from app.core.vector_db import get_vector_db_instance

        # 记录任务开始
        logger.info(f"索引重建任务开始: task_id={request.task_id}, collection={request.collection}")

        if request.collection:
            collection_name = f"space_{request.collection}"
            vector_db = get_vector_db_instance()

            # 重建索引：删除旧集合 → 重新创建
            try:
                dimension = 1024  # 默认维度
                from app.services.embedding import embedding_service
                dimension = embedding_service.get_dimension()

                await vector_db.drop_collection(collection_name)
                await vector_db.create_collection(collection_name, dimension)

                logger.info(f"索引重建完成: collection={collection_name}")
            except Exception as e:
                logger.error(f"索引重建失败: {e}")
                raise BusinessException(code=5009, message=f"索引重建失败: {str(e)}")

        return Result.success(data={
            "taskId": request.task_id,
            "status": "COMPLETED",
            "collection": request.collection,
        })

    except BusinessException:
        raise
    except Exception as e:
        logger.error(f"触发索引重建失败: {e}")
        raise BusinessException(code=5009, message=f"触发重建失败: {str(e)}")


@router.get("/collections", summary="获取集合列表")
async def get_collections():
    """获取所有向量集合信息"""
    try:
        from app.core.vector_db import get_vector_db_instance
        vector_db = get_vector_db_instance()

        collections = []
        try:
            coll_names = await vector_db.list_collections()
            if coll_names:
                for name in coll_names:
                    try:
                        count = await vector_db.count(name)
                        collections.append({
                            "name": name,
                            "vectorCount": count,
                        })
                    except Exception:
                        collections.append({
                            "name": name,
                            "vectorCount": 0,
                        })
        except Exception as e:
            logger.warning(f"获取集合列表失败: {e}")

        return Result.success(data={"collections": collections})

    except Exception as e:
        logger.error(f"获取集合列表失败: {e}")
        raise BusinessException(code=5010, message=f"获取集合列表失败: {str(e)}")

