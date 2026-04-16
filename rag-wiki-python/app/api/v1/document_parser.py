"""
文档解析服务API - 完整实现
"""
import logging
import tempfile
import os
import uuid
from typing import Optional, Dict, Any
from datetime import datetime
from fastapi import APIRouter, UploadFile, File, Form, Request
from pydantic import BaseModel
from app.core.response import Result, BusinessException
from app.services.document_parser import document_parser_service, ParserType
from app.services.quality_evaluator import quality_evaluator

logger = logging.getLogger(__name__)
router = APIRouter()

# 内存中的任务状态缓存（生产环境应使用Redis或数据库）
_task_cache: Dict[str, Dict[str, Any]] = {}


class ParseTaskRequest(BaseModel):
    document_id: str
    file_path: str
    file_type: str
    space_id: str
    security_level: int = 1
    parser_type: str = "auto"
    is_scanned: bool = False


class ParseTaskResponse(BaseModel):
    task_id: str
    status: str
    message: str = ""


class TaskStatus:
    """任务状态枚举"""
    PENDING = "PENDING"
    PARSING = "PARSING"
    VECTORIZING = "VECTORIZING"
    COMPLETED = "COMPLETED"
    FAILED = "FAILED"
    NEED_REVIEW = "NEED_REVIEW"


def _create_task(document_id: str, file_name: str, space_id: str, file_path: str = "") -> str:
    """创建解析任务"""
    task_id = str(uuid.uuid4())[:12]
    _task_cache[task_id] = {
        "task_id": task_id,
        "document_id": document_id,
        "file_name": file_name,
        "space_id": space_id,
        "file_path": file_path,
        "status": TaskStatus.PENDING,
        "progress": 0,
        "chunk_count": 0,
        "table_count": 0,
        "quality_score": 0.0,
        "error_message": "",
        "created_at": datetime.now().isoformat(),
        "updated_at": datetime.now().isoformat(),
    }
    return task_id


def _update_task(task_id: str, **kwargs):
    """更新任务状态"""
    if task_id in _task_cache:
        _task_cache[task_id].update(kwargs)
        _task_cache[task_id]["updated_at"] = datetime.now().isoformat()


def _get_task(task_id: str) -> Optional[Dict[str, Any]]:
    """获取任务信息"""
    return _task_cache.get(task_id)


@router.post("/parse", summary="提交文档解析任务")
async def submit_parse_task(request: ParseTaskRequest, req: Request):
    """
    提交文档解析任务，根据文件类型路由到对应解析器
    流程: 文件类型检测 → 解析器路由 → 文档解析 → 质量评估 → 分块 → 返回结果
    """
    try:
        parser_type = ParserType(request.parser_type)
    except ValueError:
        parser_type = ParserType.AUTO

    try:
        result = await document_parser_service.parse(
            file_path=request.file_path,
            document_id=request.document_id,
            parser_type=parser_type,
            is_scanned=request.is_scanned,
        )

        # 质量评估
        quality_report = quality_evaluator.evaluate(
            content=result.content,
            chunks=result.chunks,
            metadata=result.metadata,
        )

        return Result.success(data={
            "document_id": result.document_id,
            "content": result.content[:2000],  # 限制返回长度
            "chunks": result.chunks,
            "tables": result.tables,
            "images_count": len(result.images),
            "metadata": result.metadata,
            "quality": quality_report.to_dict(),
        })

    except FileNotFoundError as e:
        raise BusinessException(code=4004, message=str(e))
    except Exception as e:
        logger.error(f"文档解析失败: {e}")
        raise BusinessException(code=5006, message=f"文档解析失败: {str(e)}")


@router.post("/parse/status", summary="查询解析任务状态")
async def get_parse_status(task_id: str):
    """
    查询文档解析任务的状态
    支持：PENDING, PARSING, VECTORIZING, COMPLETED, FAILED, NEED_REVIEW
    """
    # 优先从内存缓存查询
    task = _get_task(task_id)
    if task:
        return Result.success(data=task)

    # 从数据库查询（生产环境）
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
        cursor.execute(
            """
            SELECT task_id, document_id, file_name, status, quality_score,
                   chunk_count, table_count, error_message, created_at, updated_at
            FROM document_parse_task
            WHERE task_id = %s
            """,
            (task_id,),
        )
        row = cursor.fetchone()
        cursor.close()
        conn.close()

        if row:
            return Result.success(data=row)
        else:
            raise BusinessException(code=4004, message=f"任务不存在: {task_id}")

    except Exception as e:
        logger.warning(f"数据库查询失败，返回默认状态: {e}")
        return Result.success(data={
            "task_id": task_id,
            "status": "UNKNOWN",
            "message": "任务状态未知，请稍后重试",
        })


@router.post("/upload", summary="上传文件并解析")
async def upload_and_parse(
    file: UploadFile = File(...),
    space_id: str = Form(...),
    security_level: int = Form(1),
):
    """
    上传文件并自动触发解析
    流程: 保存文件到MinIO → 创建解析任务 → 执行解析 → 返回结果
    """
    from app.services.minio_storage import minio_service

    try:
        # 读取文件内容
        content = await file.read()
        file_name = file.filename
        content_type = file.content_type or "application/octet-stream"

        # 上传到MinIO
        object_name = f"documents/{space_id}/{file_name}"
        minio_service.upload_bytes(
            object_name=object_name,
            data=content,
            content_type=content_type,
        )

        # 保存到临时文件进行解析
        ext = os.path.splitext(file_name)[1]
        with tempfile.NamedTemporaryFile(suffix=ext, delete=False) as tmp:
            tmp.write(content)
            tmp_path = tmp.name

        try:
            # 执行解析
            document_id = os.path.splitext(file_name)[0]
            result = await document_parser_service.parse(
                file_path=tmp_path,
                document_id=document_id,
                parser_type=ParserType.AUTO,
            )

            # 质量评估
            quality_report = quality_evaluator.evaluate(
                content=result.content,
                chunks=result.chunks,
                metadata=result.metadata,
            )

            return Result.success(data={
                "file_name": file_name,
                "document_id": document_id,
                "object_name": object_name,
                "chunks": result.chunks,
                "tables": result.tables,
                "metadata": result.metadata,
                "quality": quality_report.to_dict(),
            })
        finally:
            os.unlink(tmp_path)

    except Exception as e:
        logger.error(f"上传解析失败: {e}")
        raise BusinessException(code=5007, message=f"上传解析失败: {str(e)}")


@router.post("/reparse", summary="重新解析（增强解析）")
async def reparse_document(
    task_id: str,
    parser_type: Optional[str] = None,
    force_ocr: bool = False,
):
    """
    使用指定解析器重新解析文档
    场景：原解析质量不佳、文档类型误判、需要增强解析
    """
    # 获取原始任务信息
    task = _get_task(task_id)
    if not task:
        # 尝试从数据库获取
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
            cursor.execute(
                """
                SELECT t.*, d.file_path
                FROM document_parse_task t
                JOIN document_info d ON t.document_id = d.document_id
                WHERE t.task_id = %s
                """,
                (task_id,),
            )
            row = cursor.fetchone()
            cursor.close()
            conn.close()

            if row:
                task = dict(row)
            else:
                raise BusinessException(code=4004, message=f"任务不存在: {task_id}")

        except Exception as e:
            logger.error(f"获取任务信息失败: {e}")
            raise BusinessException(code=5001, message=f"获取任务信息失败: {str(e)}")

    file_path = task.get("file_path", "")
    document_id = task.get("document_id", "")

    if not file_path:
        raise BusinessException(code=4004, message="原始文件路径不存在，无法重新解析")

    # 确定解析器类型
    try:
        selected_parser = ParserType(parser_type) if parser_type else ParserType.ENHANCED
    except ValueError:
        selected_parser = ParserType.ENHANCED

    # 更新任务状态为解析中
    _update_task(task_id, status=TaskStatus.PARSING, progress=0)

    try:
        # 执行重新解析
        result = await document_parser_service.parse(
            file_path=file_path,
            document_id=document_id,
            parser_type=selected_parser,
            is_scanned=force_ocr,
        )

        # 质量评估
        quality_report = quality_evaluator.evaluate(
            content=result.content,
            chunks=result.chunks,
            metadata=result.metadata,
        )

        # 判断是否需要人工复核
        final_status = TaskStatus.COMPLETED
        if quality_report.score < 0.5:
            final_status = TaskStatus.NEED_REVIEW

        # 更新任务状态
        _update_task(
            task_id,
            status=final_status,
            progress=100,
            chunk_count=len(result.chunks),
            table_count=len(result.tables),
            quality_score=quality_report.score,
        )

        logger.info(f"重新解析完成: task_id={task_id}, quality={quality_report.score:.2f}")

        return Result.success(data={
            "task_id": task_id,
            "status": final_status,
            "document_id": document_id,
            "chunks": result.chunks,
            "tables": result.tables,
            "quality": quality_report.to_dict(),
        })

    except Exception as e:
        logger.error(f"重新解析失败: {e}")
        _update_task(task_id, status=TaskStatus.FAILED, error_message=str(e))
        raise BusinessException(code=5006, message=f"重新解析失败: {str(e)}")
