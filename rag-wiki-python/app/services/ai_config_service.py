"""
AI配置同步服务

从Java LLM配置管理服务同步配置到Python AI服务，
使Python端的LLM调用使用Java端管理的配置信息。
"""
import logging
import asyncio
from typing import Dict, Any, Optional, List

logger = logging.getLogger(__name__)


class AIConfigService:
    """AI配置同步服务"""

    def __init__(self):
        self._configs: Dict[str, Dict[str, Any]] = {}
        self._default_provider: str = "openai"
        self._loaded: bool = False

    async def load_configs_from_java(self) -> Dict[str, Any]:
        """
        从Java LLM配置服务加载配置

        通过内部HTTP调用Java /api/ai/llm-config/list 获取启用的配置
        """
        try:
            import httpx
            async with httpx.AsyncClient(timeout=10.0) as client:
                response = await client.get("http://rag-wiki-audit:8084/api/ai/llm-config/list")
                if response.status_code == 200:
                    data = response.json()
                    configs = data.get("data", [])
                    for config in configs:
                        provider = config.get("provider", "openai")
                        self._configs[provider] = {
                            "config_name": config.get("configName", ""),
                            "provider": provider,
                            "model_name": config.get("modelName", ""),
                            "api_base": config.get("apiBase", ""),
                            "api_key": config.get("apiKey", ""),
                            "temperature": config.get("temperature", 0.7),
                            "max_tokens": config.get("maxTokens", 2000),
                            "timeout_ms": config.get("timeoutMs", 30000),
                            "is_default": config.get("isDefault", 0) == 1,
                        }
                        if config.get("isDefault", 0) == 1:
                            self._default_provider = provider
                    self._loaded = True
                    logger.info(f"AI配置加载完成: {len(self._configs)}个提供商, 默认={self._default_provider}")
                    return {"status": "success", "count": len(self._configs)}
                else:
                    logger.warning(f"Java配置服务返回异常: {response.status_code}")
                    return {"status": "failed", "error": f"HTTP {response.status_code}"}
        except Exception as e:
            logger.warning(f"从Java加载AI配置失败(使用本地配置): {e}")
            return {"status": "fallback", "error": str(e)}

    def get_config(self, provider: Optional[str] = None) -> Dict[str, Any]:
        """获取指定提供商的配置"""
        key = provider or self._default_provider
        return self._configs.get(key, {})

    def get_default_provider(self) -> str:
        """获取默认提供商"""
        return self._default_provider

    def get_all_providers(self) -> List[str]:
        """获取所有已配置的提供商"""
        return list(self._configs.keys())

    def is_loaded(self) -> bool:
        """配置是否已加载"""
        return self._loaded


# 全局实例
ai_config_service = AIConfigService()
