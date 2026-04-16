"""
统一响应模型与异常处理
"""
from fastapi import Request
from fastapi.responses import JSONResponse
from fastapi.exceptions import RequestValidationError
from pydantic import BaseModel
from typing import Optional, Any
import logging
import time
import httpx

logger = logging.getLogger(__name__)


class Result(BaseModel):
    """统一API响应体"""
    code: int = 200
    message: str = "操作成功"
    data: Optional[Any] = None
    timestamp: int = int(time.time() * 1000)
    traceId: Optional[str] = None

    @staticmethod
    def success(data: Any = None, message: str = "操作成功") -> "Result":
        return Result(code=200, message=message, data=data, traceId=_get_trace_id())

    @staticmethod
    def fail(code: int = 500, message: str = "系统内部错误") -> "Result":
        return Result(code=code, message=message, traceId=_get_trace_id())


def _get_trace_id() -> Optional[str]:
    """尝试获取当前TraceId"""
    try:
        from app.core.tracing import get_current_trace_id
        return get_current_trace_id()
    except Exception:
        return None


class BusinessException(Exception):
    """业务异常"""
    def __init__(self, code: int = 500, message: str = "系统内部错误"):
        self.code = code
        self.message = message


async def business_exception_handler(request: Request, exc: BusinessException):
    logger.warning(f"业务异常: code={exc.code}, message={exc.message}, path={request.url.path}")
    return JSONResponse(
        status_code=200,
        content=Result.fail(code=exc.code, message=exc.message).model_dump()
    )


async def validation_exception_handler(request: Request, exc: RequestValidationError):
    errors = exc.errors()
    message = "; ".join([f"{e['loc'][-1]}: {e['msg']}" for e in errors])
    logger.warning(f"参数校验失败: path={request.url.path}, errors={message}")
    return JSONResponse(
        status_code=200,
        content=Result.fail(code=400, message=message).model_dump()
    )


async def global_exception_handler(request: Request, exc: Exception):
    # 区分不同异常类型给出更具体的响应
    if isinstance(exc, httpx.TimeoutException):
        logger.error(f"外部服务超时: path={request.url.path}, error={exc}")
        return JSONResponse(
            status_code=200,
            content=Result.fail(code=504, message="AI服务响应超时，请稍后重试").model_dump()
        )
    if isinstance(exc, httpx.ConnectError):
        logger.error(f"外部服务连接失败: path={request.url.path}, error={exc}")
        return JSONResponse(
            status_code=200,
            content=Result.fail(code=503, message="AI服务暂时不可用").model_dump()
        )
    if isinstance(exc, PermissionError):
        logger.warning(f"权限不足: path={request.url.path}, error={exc}")
        return JSONResponse(
            status_code=200,
            content=Result.fail(code=403, message="权限不足").model_dump()
        )

    logger.error(f"系统异常: path={request.url.path}, error={exc}", exc_info=True)
    return JSONResponse(
        status_code=500,
        content=Result.fail(code=500, message="系统内部错误").model_dump()
    )
