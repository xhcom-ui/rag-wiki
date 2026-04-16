"""
权限验证中间件 - 从请求头中提取用户权限信息
"""
import logging
from typing import Callable
from fastapi import Request, Response
from starlette.middleware.base import BaseHTTPMiddleware

logger = logging.getLogger(__name__)

# 不需要权限校验的路径
WHITE_LIST = ["/health", "/docs", "/redoc", "/openapi.json"]


class PermissionMiddleware(BaseHTTPMiddleware):
    """从网关注入的请求头中提取权限信息，注入到请求状态"""

    async def dispatch(self, request: Request, call_next: Callable) -> Response:
        # 白名单路径跳过
        path = request.url.path
        if any(path.startswith(w) or path == w for w in WHITE_LIST):
            return await call_next(request)

        # 从请求头提取网关注入的权限信息
        request.state.user_id = request.headers.get("X-User-Id")
        request.state.username = request.headers.get("X-User-Name")
        request.state.dept_id = request.headers.get("X-Dept-Id")
        request.state.security_level = int(request.headers.get("X-Security-Level", "1"))
        request.state.tenant_id = request.headers.get("X-Tenant-Id")

        # 如果没有用户信息，拒绝访问（生产环境应启用）
        # if not request.state.user_id:
        #     return JSONResponse(status_code=401, content={"detail": "未认证"})

        return await call_next(request)
