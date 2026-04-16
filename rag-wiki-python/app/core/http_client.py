"""
弹性HTTP客户端 - 内置重试、超时、熔断
基于httpx + tenacity实现企业级容错
"""
import logging
from typing import Any, Dict, Optional

import httpx
from tenacity import (
    retry,
    stop_after_attempt,
    wait_exponential,
    retry_if_exception_type,
    before_sleep_log,
    after_log,
)

from app.core.config import settings

logger = logging.getLogger(__name__)

# 默认超时配置
DEFAULT_TIMEOUT = httpx.Timeout(
    connect=5.0,      # 连接超时
    read=30.0,         # 读取超时
    write=10.0,        # 写入超时
    pool=5.0,          # 连接池等待超时
)

# AI服务超时（LLM响应较慢）
AI_TIMEOUT = httpx.Timeout(
    connect=5.0,
    read=120.0,
    write=10.0,
    pool=5.0,
)

# 重试配置：可重试的异常类型
RETRYABLE_EXCEPTIONS = (
    httpx.ConnectError,
    httpx.ConnectTimeout,
    httpx.ReadTimeout,
    httpx.PoolTimeout,
)


def create_retry_decorator(max_attempts: int = 3, min_wait: float = 0.5, max_wait: float = 10.0):
    """创建tenacity重试装饰器"""
    return retry(
        stop=stop_after_attempt(max_attempts),
        wait=wait_exponential(multiplier=1, min=min_wait, max=max_wait),
        retry=retry_if_exception_type(RETRYABLE_EXCEPTIONS),
        before_sleep=before_sleep_log(logger, logging.WARNING),
        after=after_log(logger, logging.DEBUG),
        reraise=True,
    )


# 预定义的重试策略
retry_default = create_retry_decorator(max_attempts=3, min_wait=0.5, max_wait=10)
retry_ai = create_retry_decorator(max_attempts=2, min_wait=1.0, max_wait=30)
retry_critical = create_retry_decorator(max_attempts=5, min_wait=1.0, max_wait=60)


class ResilientHttpClient:
    """
    弹性HTTP客户端
    
    特性:
    1. 连接池复用（httpx.AsyncClient）
    2. 自动重试（tenacity指数退避）
    3. 可配超时策略
    4. 请求/响应日志
    """

    def __init__(
        self,
        base_url: str = "",
        timeout: httpx.Timeout = DEFAULT_TIMEOUT,
        headers: Optional[Dict[str, str]] = None,
        max_connections: int = 100,
        max_keepalive_connections: int = 20,
    ):
        self._client: Optional[httpx.AsyncClient] = None
        self._base_url = base_url
        self._timeout = timeout
        self._headers = headers or {}
        self._max_connections = max_connections
        self._max_keepalive = max_keepalive_connections

    def _get_client(self) -> httpx.AsyncClient:
        if self._client is None or self._client.is_closed:
            self._client = httpx.AsyncClient(
                base_url=self._base_url,
                timeout=self._timeout,
                headers=self._headers,
                limits=httpx.Limits(
                    max_connections=self._max_connections,
                    max_keepalive_connections=self._max_keepalive,
                ),
            )
        return self._client

    @retry_default
    async def get(self, url: str, params: Optional[Dict] = None, **kwargs) -> httpx.Response:
        client = self._get_client()
        response = await client.get(url, params=params, **kwargs)
        response.raise_for_status()
        return response

    @retry_default
    async def post(self, url: str, json: Any = None, data: Any = None, **kwargs) -> httpx.Response:
        client = self._get_client()
        response = await client.post(url, json=json, data=data, **kwargs)
        response.raise_for_status()
        return response

    @retry_default
    async def put(self, url: str, json: Any = None, **kwargs) -> httpx.Response:
        client = self._get_client()
        response = await client.put(url, json=json, **kwargs)
        response.raise_for_status()
        return response

    @retry_default
    async def delete(self, url: str, **kwargs) -> httpx.Response:
        client = self._get_client()
        response = await client.delete(url, **kwargs)
        response.raise_for_status()
        return response

    async def close(self):
        if self._client and not self._client.is_closed:
            await self._client.aclose()
            self._client = None


class AIServiceClient(ResilientHttpClient):
    """
    AI服务专用客户端
    - 更长的读取超时（LLM生成慢）
    - 更宽松的重试策略（减少重复LLM调用）
    """

    def __init__(self, base_url: str = "", api_key: str = ""):
        super().__init__(
            base_url=base_url,
            timeout=AI_TIMEOUT,
            headers={
                "Authorization": f"Bearer {api_key}" if api_key else "",
                "Content-Type": "application/json",
            },
        )

    @retry_ai
    async def chat_completion(self, messages: list, model: str = "", **kwargs) -> Dict:
        """调用LLM chat completion API（自动重试）"""
        payload = {
            "model": model or "deepseek-chat",
            "messages": messages,
            **kwargs,
        }
        response = await self.post("/v1/chat/completions", json=payload)
        return response.json()

    @retry_ai
    async def embeddings(self, texts: list, model: str = "") -> Dict:
        """调用Embedding API（自动重试）"""
        payload = {
            "model": model or "text-embedding-v2",
            "input": texts,
        }
        response = await self.post("/v1/embeddings", json=payload)
        return response.json()


# 全局客户端实例（延迟初始化）
_default_client: Optional[ResilientHttpClient] = None
_ai_client: Optional[AIServiceClient] = None


def get_http_client() -> ResilientHttpClient:
    global _default_client
    if _default_client is None:
        _default_client = ResilientHttpClient()
    return _default_client


def get_ai_client() -> AIServiceClient:
    global _ai_client
    if _ai_client is None:
        _ai_client = AIServiceClient(
            base_url=getattr(settings, 'LLM_API_BASE', 'http://localhost:11434'),
            api_key=getattr(settings, 'LLM_API_KEY', ''),
        )
    return _ai_client
