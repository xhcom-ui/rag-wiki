"""
记忆管理服务API - 完整实现
"""
import logging
from typing import Optional, List
from fastapi import APIRouter, Request
from pydantic import BaseModel
from app.core.response import Result, BusinessException
from app.services.memory import memory_service, MemoryType

logger = logging.getLogger(__name__)
router = APIRouter()


class MemoryCreateRequest(BaseModel):
    content: str
    memory_type: str = "EPISODIC"  # SEMANTIC / EPISODIC / PROCEDURAL
    ttl: int = -1


class MemorySearchRequest(BaseModel):
    query: str
    memory_type: Optional[str] = None
    top_k: int = 5


@router.post("/create", summary="创建记忆")
async def create_memory(request: MemoryCreateRequest, req: Request):
    """创建用户记忆，自动向量化存储"""
    user_id = getattr(req.state, "user_id", None) or "anonymous"
    try:
        mem_type = MemoryType(request.memory_type)
    except ValueError:
        mem_type = MemoryType.EPISODIC

    result = await memory_service.create_memory(
        user_id=user_id,
        content=request.content,
        memory_type=mem_type,
        ttl=request.ttl,
    )
    return Result.success(data=result)


@router.post("/search", summary="检索记忆")
async def search_memories(request: MemorySearchRequest, req: Request):
    """根据查询检索相关记忆"""
    user_id = getattr(req.state, "user_id", None) or "anonymous"
    try:
        mem_type = MemoryType(request.memory_type) if request.memory_type else None
    except ValueError:
        mem_type = None

    result = await memory_service.search_memories(
        user_id=user_id,
        query=request.query,
        memory_type=mem_type,
        top_k=request.top_k,
    )
    return Result.success(data=result)


@router.post("/extract", summary="从对话中提取记忆")
async def extract_memories(session_id: str, user_id: str):
    """从对话历史中提取关键信息，形成长期记忆"""
    from app.services.rag_engine import conversation_manager

    # 获取对话历史
    messages = await conversation_manager.get_history(session_id)
    if not messages:
        return Result.success(data={"extracted_count": 0, "message": "无对话历史"})

    result = await memory_service.extract_from_conversation(
        session_id=session_id,
        user_id=user_id,
        messages=messages,
    )
    return Result.success(data=result)


@router.get("/list", summary="获取用户记忆列表")
async def list_memories(req: Request, memory_type: Optional[str] = None, page: int = 1, size: int = 10):
    """分页获取用户记忆列表"""
    user_id = getattr(req.state, "user_id", None) or "anonymous"
    
    try:
        import pymysql
        from app.core.config import settings

        conn = pymysql.connect(
            host=settings.MYSQL_HOST,
            port=settings.MYSQL_PORT,
            user=settings.MYSQL_USER,
            password=settings.MYSQL_PASSWORD,
            database=settings.MYSQL_DATABASE,
        )
        cursor = conn.cursor(pymysql.cursors.DictCursor)

        # 构建查询条件
        where_clauses = ["user_id = %s", "is_deleted = 0"]
        params = [user_id]

        if memory_type:
            where_clauses.append("memory_type = %s")
            params.append(memory_type)

        where_clause = " AND ".join(where_clauses)

        # 查询总数
        cursor.execute(f"SELECT COUNT(*) as total FROM user_memory WHERE {where_clause}", params)
        total = cursor.fetchone()["total"]

        # 分页查询
        offset = (page - 1) * size
        cursor.execute(
            f"""
            SELECT memory_id, content, memory_type, ttl, created_at, updated_at
            FROM user_memory
            WHERE {where_clause}
            ORDER BY created_at DESC
            LIMIT %s OFFSET %s
            """,
            params + [size, offset],
        )
        records = cursor.fetchall()

        cursor.close()
        conn.close()

        return Result.success(data={
            "records": records,
            "total": total,
            "page": page,
            "size": size,
        })

    except Exception as e:
        logger.warning(f"查询记忆列表失败: {e}")
        return Result.success(data={"records": [], "total": 0, "page": page, "size": size})


@router.delete("/{memory_id}", summary="删除记忆")
async def delete_memory(memory_id: str, req: Request):
    """软删除记忆（合规清理，满足GDPR被遗忘权）"""
    user_id = getattr(req.state, "user_id", None) or "anonymous"
    await memory_service.delete_memory(memory_id, user_id)
    return Result.success()


@router.post("/cleanup", summary="清理过期记忆")
async def cleanup_expired_memories():
    """清理过期记忆（由定时任务调用）"""
    cleaned = await memory_service.cleanup_expired()
    return Result.success(data={"cleaned_count": cleaned})
