"""
代码沙箱服务API - 完整实现
"""
import logging
from typing import Optional, List
from fastapi import APIRouter, Request, UploadFile, File, Form
from pydantic import BaseModel
from app.core.response import Result, BusinessException
from app.services.sandbox import sandbox_service

logger = logging.getLogger(__name__)
router = APIRouter()


class CodeExecuteRequest(BaseModel):
    user_request: str
    session_id: Optional[str] = None
    language: str = "python"
    timeout: int = 30


class CodeExecuteResponse(BaseModel):
    task_id: str
    status: str
    output: Optional[str] = None
    error: Optional[str] = None
    charts: List[str] = []


@router.post("/execute", summary="代码执行请求")
async def execute_code(request: CodeExecuteRequest, req: Request):
    """
    安全代码解释器：
    1. 需求理解与代码生成
    2. 静态安全检测（AST分析）
    3. Docker隔离沙箱执行
    4. 执行结果安全校验
    """
    user_id = getattr(req.state, "user_id", None) or "anonymous"

    try:
        result = await sandbox_service.execute_code(
            user_request=request.user_request,
            user_id=user_id,
            language=request.language,
            timeout=request.timeout,
        )
        return Result.success(data=result)
    except Exception as e:
        logger.error(f"代码执行失败: {e}")
        raise BusinessException(code=5009, message=f"代码执行失败: {str(e)}")


@router.post("/execute/upload", summary="上传数据文件并执行分析")
async def upload_and_execute(
    files: List[UploadFile] = File(...),
    user_request: str = Form(...),
    language: str = Form("python"),
):
    """上传数据文件，通过自然语言描述分析需求"""
    from app.services.minio_storage import minio_service
    import tempfile
    import os

    try:
        # 保存文件到MinIO
        file_paths = []
        for file in files:
            content = await file.read()
            object_name = f"sandbox/{file.filename}"
            minio_service.upload_bytes(object_name, content)
            file_paths.append(file.filename)

        # 构建分析请求
        enhanced_request = f"""分析以下数据文件: {', '.join(file_paths)}
用户需求: {user_request}
请先读取数据文件，然后进行分析。"""

        result = await sandbox_service.execute_code(
            user_request=enhanced_request,
            user_id="upload_user",
            language=language,
        )
        return Result.success(data=result)

    except Exception as e:
        logger.error(f"上传执行失败: {e}")
        raise BusinessException(code=5010, message=f"上传执行失败: {str(e)}")


@router.get("/result/{task_id}", summary="查询代码执行结果")
async def get_execution_result(task_id: str):
    """查询代码执行的结果"""
    result = sandbox_service.get_result(task_id)
    if not result:
        raise BusinessException(code=4004, message="执行结果不存在")
    return Result.success(data=result)


@router.post("/security-check", summary="代码静态安全检测")
async def security_check(code: str):
    """
    代码静态安全检测（AST分析）
    禁止：危险模块、系统调用、网络访问等高危代码模式
    """
    result = sandbox_service.security_check(code)
    return Result.success(data=result)
