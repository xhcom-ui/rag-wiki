"""
Celery异步任务配置
用于: 文档解析、向量化入库、记忆提取等耗时操作
"""
import logging
from celery import Celery
from app.core.config import settings

logger = logging.getLogger(__name__)

celery_app = Celery(
    "rag_wiki",
    broker=f"amqp://{settings.RABBITMQ_USER}:{settings.RABBITMQ_PASSWORD}@"
           f"{settings.RABBITMQ_HOST}:{settings.RABBITMQ_PORT}//",
    backend=f"redis://{settings.REDIS_HOST}:{settings.REDIS_PORT}/2",
)

celery_app.conf.update(
    task_serializer="json",
    accept_content=["json"],
    result_serializer="json",
    timezone="Asia/Shanghai",
    enable_utc=True,
    task_track_started=True,
    task_time_limit=300,  # 5分钟超时
    task_soft_time_limit=240,
    worker_prefetch_multiplier=1,
    worker_max_tasks_per_child=100,
)

# 自动发现任务模块
celery_app.autodiscover_tasks(["app.tasks"])


@celery_app.task(name="document_parse_task")
def document_parse_task(document_id: str, file_path: str, file_type: str,
                        space_id: str, security_level: int = 1):
    """异步文档解析任务"""
    import asyncio
    from app.services.document_parser import document_parser_service, ParserType

    async def _parse():
        result = await document_parser_service.parse(
            file_path=file_path,
            document_id=document_id,
            parser_type=ParserType.AUTO,
        )
        return {
            "document_id": result.document_id,
            "chunks_count": len(result.chunks),
            "quality_score": result.quality_score,
            "metadata": result.metadata,
        }

    loop = asyncio.new_event_loop()
    try:
        return loop.run_until_complete(_parse())
    finally:
        loop.close()


@celery_app.task(name="vector_embed_task")
def vector_embed_task(chunks: list, space_id: str, security_level: int = 1):
    """异步向量化入库任务"""
    import asyncio
    from app.services.embedding import embedding_service

    async def _embed():
        texts = [c.get("content", "") for c in chunks]
        vectors = await embedding_service.embed_texts(texts)
        return {"embedded_count": len(vectors), "space_id": space_id}

    loop = asyncio.new_event_loop()
    try:
        return loop.run_until_complete(_embed())
    finally:
        loop.close()


@celery_app.task(name="memory_extract_task")
def memory_extract_task(session_id: str, user_id: str):
    """异步记忆提取任务"""
    import asyncio
    from app.services.memory import memory_service
    from app.services.rag_engine import conversation_manager

    async def _extract():
        messages = await conversation_manager.get_history(session_id)
        if not messages:
            return {"extracted_count": 0}
        result = await memory_service.extract_from_conversation(session_id, user_id, messages)
        return result

    loop = asyncio.new_event_loop()
    try:
        return loop.run_until_complete(_extract())
    finally:
        loop.close()
