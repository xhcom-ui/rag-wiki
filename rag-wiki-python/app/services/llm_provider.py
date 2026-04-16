"""
多LLM提供商抽象层
支持: OpenAI、DeepSeek、千问(Qwen)、谷歌(Gemini)、智普(GLM)、Kimi(Moonshot)
参考: 大厂面试最佳实践 - 统一抽象 + 配置驱动 + 策略模式
"""
import json
import logging
import time
import asyncio
from abc import ABC, abstractmethod
from typing import List, Dict, Any, Optional, AsyncGenerator
from dataclasses import dataclass
from enum import Enum

logger = logging.getLogger(__name__)


class LLMProvider(str, Enum):
    """LLM提供商枚举"""
    OPENAI = "openai"
    DEEPSEEK = "deepseek"
    QWEN = "qwen"           # 千问
    GEMINI = "gemini"       # 谷歌
    GLM = "glm"             # 智普
    KIMI = "kimi"           # Moonshot
    LOCAL = "local"         # 本地模型


@dataclass
class LLMConfig:
    """LLM配置"""
    provider: LLMProvider
    api_key: str
    api_base: str
    model: str
    temperature: float = 0.7
    max_tokens: int = 4096
    
    # 各提供商的默认配置
    DEFAULTS = {
        LLMProvider.OPENAI: {
            "api_base": "https://api.openai.com/v1",
            "model": "gpt-4o",
        },
        LLMProvider.DEEPSEEK: {
            "api_base": "https://api.deepseek.com/v1",
            "model": "deepseek-chat",
        },
        LLMProvider.QWEN: {
            "api_base": "https://dashscope.aliyuncs.com/compatible-mode/v1",
            "model": "qwen-turbo",
        },
        LLMProvider.GEMINI: {
            "api_base": "https://generativelanguage.googleapis.com/v1beta",
            "model": "gemini-pro",
        },
        LLMProvider.GLM: {
            "api_base": "https://open.bigmodel.cn/api/paas/v4",
            "model": "glm-4",
        },
        LLMProvider.KIMI: {
            "api_base": "https://api.moonshot.cn/v1",
            "model": "moonshot-v1-8k",
        },
        LLMProvider.LOCAL: {
            "api_base": "http://localhost:8000/v1",
            "model": "local-model",
        },
    }

    @classmethod
    def from_provider(cls, provider: LLMProvider, api_key: str, 
                      api_base: str = None, model: str = None,
                      temperature: float = 0.7, max_tokens: int = 4096) -> "LLMConfig":
        """从提供商创建配置"""
        defaults = cls.DEFAULTS.get(provider, {})
        return cls(
            provider=provider,
            api_key=api_key,
            api_base=api_base or defaults.get("api_base", ""),
            model=model or defaults.get("model", ""),
            temperature=temperature,
            max_tokens=max_tokens,
        )


class BaseLLMProvider(ABC):
    """LLM提供商抽象基类"""
    
    def __init__(self, config: LLMConfig):
        self.config = config
        self._client = None
    
    @abstractmethod
    async def chat_completion(
        self,
        messages: List[Dict[str, str]],
        temperature: float = None,
        max_tokens: int = None,
    ) -> str:
        """非流式对话"""
        pass
    
    @abstractmethod
    async def chat_completion_stream(
        self,
        messages: List[Dict[str, str]],
        temperature: float = None,
        max_tokens: int = None,
    ) -> AsyncGenerator[str, None]:
        """流式对话"""
        pass
    
    def _get_client(self):
        """获取HTTP客户端"""
        if self._client is None:
            import httpx
            self._client = httpx.AsyncClient(
                base_url=self.config.api_base,
                headers=self._build_headers(),
                timeout=120.0,
            )
        return self._client
    
    def _build_headers(self) -> Dict[str, str]:
        """构建请求头"""
        return {
            "Authorization": f"Bearer {self.config.api_key}",
            "Content-Type": "application/json",
        }


class OpenAICompatibleProvider(BaseLLMProvider):
    """
    OpenAI兼容接口提供商
    适用于: OpenAI、DeepSeek、千问、Kimi等OpenAI兼容API
    """
    
    async def chat_completion(
        self,
        messages: List[Dict[str, str]],
        temperature: float = None,
        max_tokens: int = None,
    ) -> str:
        client = self._get_client()
        payload = {
            "model": self.config.model,
            "messages": messages,
            "temperature": temperature or self.config.temperature,
            "max_tokens": max_tokens or self.config.max_tokens,
            "stream": False,
        }
        
        response = await client.post("/chat/completions", json=payload)
        response.raise_for_status()
        data = response.json()
        return data["choices"][0]["message"]["content"]
    
    async def chat_completion_stream(
        self,
        messages: List[Dict[str, str]],
        temperature: float = None,
        max_tokens: int = None,
    ) -> AsyncGenerator[str, None]:
        client = self._get_client()
        payload = {
            "model": self.config.model,
            "messages": messages,
            "temperature": temperature or self.config.temperature,
            "max_tokens": max_tokens or self.config.max_tokens,
            "stream": True,
        }
        
        async with client.stream("POST", "/chat/completions", json=payload) as response:
            response.raise_for_status()
            async for line in response.aiter_lines():
                if line.startswith("data: "):
                    data_str = line[6:]
                    if data_str.strip() == "[DONE]":
                        break
                    try:
                        data = json.loads(data_str)
                        delta = data["choices"][0].get("delta", {})
                        content = delta.get("content", "")
                        if content:
                            yield content
                    except json.JSONDecodeError:
                        continue


class GeminiProvider(BaseLLMProvider):
    """
    谷歌Gemini提供商
    使用Gemini API格式
    """
    
    def _build_headers(self) -> Dict[str, str]:
        return {
            "Content-Type": "application/json",
        }
    
    async def chat_completion(
        self,
        messages: List[Dict[str, str]],
        temperature: float = None,
        max_tokens: int = None,
    ) -> str:
        import httpx
        
        # 转换消息格式
        contents = []
        for msg in messages:
            role = "user" if msg["role"] == "user" else "model"
            contents.append({
                "role": role,
                "parts": [{"text": msg["content"]}]
            })
        
        payload = {
            "contents": contents,
            "generationConfig": {
                "temperature": temperature or self.config.temperature,
                "maxOutputTokens": max_tokens or self.config.max_tokens,
            }
        }
        
        url = f"{self.config.api_base}/models/{self.config.model}:generateContent?key={self.config.api_key}"
        async with httpx.AsyncClient(timeout=120.0) as client:
            response = await client.post(url, json=payload)
            response.raise_for_status()
            data = response.json()
            return data["candidates"][0]["content"]["parts"][0]["text"]
    
    async def chat_completion_stream(
        self,
        messages: List[Dict[str, str]],
        temperature: float = None,
        max_tokens: int = None,
    ) -> AsyncGenerator[str, None]:
        # Gemini流式实现
        import httpx
        
        contents = []
        for msg in messages:
            role = "user" if msg["role"] == "user" else "model"
            contents.append({
                "role": role,
                "parts": [{"text": msg["content"]}]
            })
        
        payload = {
            "contents": contents,
            "generationConfig": {
                "temperature": temperature or self.config.temperature,
                "maxOutputTokens": max_tokens or self.config.max_tokens,
            }
        }
        
        url = f"{self.config.api_base}/models/{self.config.model}:streamGenerateContent?key={self.config.api_key}&alt=sse"
        async with httpx.AsyncClient(timeout=120.0) as client:
            async with client.stream("POST", url, json=payload) as response:
                async for line in response.aiter_lines():
                    if line.startswith("data: "):
                        try:
                            data = json.loads(line[6:])
                            text = data["candidates"][0]["content"]["parts"][0].get("text", "")
                            if text:
                                yield text
                        except (json.JSONDecodeError, KeyError, IndexError):
                            continue


class GLMProvider(BaseLLMProvider):
    """
    智普GLM提供商
    使用智普API格式
    """
    
    async def chat_completion(
        self,
        messages: List[Dict[str, str]],
        temperature: float = None,
        max_tokens: int = None,
    ) -> str:
        client = self._get_client()
        payload = {
            "model": self.config.model,
            "messages": messages,
            "temperature": temperature or self.config.temperature,
            "max_tokens": max_tokens or self.config.max_tokens,
        }
        
        response = await client.post("/chat/completions", json=payload)
        response.raise_for_status()
        data = response.json()
        return data["choices"][0]["message"]["content"]
    
    async def chat_completion_stream(
        self,
        messages: List[Dict[str, str]],
        temperature: float = None,
        max_tokens: int = None,
    ) -> AsyncGenerator[str, None]:
        client = self._get_client()
        payload = {
            "model": self.config.model,
            "messages": messages,
            "temperature": temperature or self.config.temperature,
            "max_tokens": max_tokens or self.config.max_tokens,
            "stream": True,
        }
        
        async with client.stream("POST", "/chat/completions", json=payload) as response:
            response.raise_for_status()
            async for line in response.aiter_lines():
                if line.startswith("data: "):
                    data_str = line[6:]
                    if data_str.strip() == "[DONE]":
                        break
                    try:
                        data = json.loads(data_str)
                        delta = data["choices"][0].get("delta", {})
                        content = delta.get("content", "")
                        if content:
                            yield content
                    except json.JSONDecodeError:
                        continue


class MultiLLMService:
    """
    多LLM服务 - 统一入口
    支持:
    1. 多提供商切换
    2. 负载均衡
    3. 失败重试
    4. 渐进式上下文注入
    """
    
    def __init__(self):
        self._providers: Dict[str, BaseLLMProvider] = {}
        self._default_provider: str = None
    
    def register_provider(self, name: str, config: LLMConfig) -> None:
        """注册LLM提供商"""
        provider_cls = self._get_provider_class(config.provider)
        self._providers[name] = provider_cls(config)
        
        if self._default_provider is None:
            self._default_provider = name
        
        logger.info(f"注册LLM提供商: name={name}, provider={config.provider.value}, model={config.model}")
    
    def _get_provider_class(self, provider: LLMProvider) -> type:
        """获取提供商实现类"""
        if provider == LLMProvider.GEMINI:
            return GeminiProvider
        elif provider == LLMProvider.GLM:
            return GLMProvider
        else:
            # OpenAI兼容接口: OpenAI, DeepSeek, Qwen, Kimi, Local
            return OpenAICompatibleProvider
    
    def set_default_provider(self, name: str) -> None:
        """设置默认提供商"""
        if name not in self._providers:
            raise ValueError(f"提供商 {name} 未注册")
        self._default_provider = name
        logger.info(f"设置默认LLM提供商: {name}")
    
    def get_provider(self, name: str = None) -> BaseLLMProvider:
        """获取提供商"""
        name = name or self._default_provider
        if name not in self._providers:
            raise ValueError(f"提供商 {name} 未注册")
        return self._providers[name]
    
    async def chat_completion(
        self,
        messages: List[Dict[str, str]],
        provider: str = None,
        temperature: float = None,
        max_tokens: int = None,
        retry_providers: List[str] = None,
    ) -> str:
        """
        非流式对话 - 支持失败重试
        
        Args:
            messages: 对话消息
            provider: 指定提供商
            temperature: 温度
            max_tokens: 最大token数
            retry_providers: 失败后重试的其他提供商列表
        """
        errors = []
        
        # 主提供商
        providers_to_try = [provider or self._default_provider]
        if retry_providers:
            providers_to_try.extend(retry_providers)
        
        for prov_name in providers_to_try:
            try:
                prov = self.get_provider(prov_name)
                return await prov.chat_completion(messages, temperature, max_tokens)
            except Exception as e:
                logger.warning(f"LLM调用失败: provider={prov_name}, error={e}")
                errors.append((prov_name, str(e)))
                continue
        
        raise RuntimeError(f"所有LLM提供商调用失败: {errors}")
    
    async def chat_completion_stream(
        self,
        messages: List[Dict[str, str]],
        provider: str = None,
        temperature: float = None,
        max_tokens: int = None,
    ) -> AsyncGenerator[str, None]:
        """流式对话"""
        prov = self.get_provider(provider or self._default_provider)
        async for token in prov.chat_completion_stream(messages, temperature, max_tokens):
            yield token
    
    async def chat_with_context(
        self,
        system_prompt: str,
        user_query: str,
        context_chunks: List[str],
        provider: str = None,
        max_context_tokens: int = 6000,
        temperature: float = None,
    ) -> AsyncGenerator[Dict[str, Any], None]:
        """
        渐进式上下文注入对话
        
        实现思路（参考大厂面试最佳实践）：
        1. 按token数分批注入上下文
        2. 每批上下文单独调用LLM生成中间结果
        3. 最后汇总生成最终答案
        
        这样做的优点：
        - 避免一次性注入过多上下文导致超token
        - 可以逐步处理大量检索结果
        - 提高召回质量
        """
        # 构建消息
        messages = [{"role": "system", "content": system_prompt}]
        
        # 估算token（简化实现，实际应使用tiktoken）
        def estimate_tokens(text: str) -> int:
            return len(text) // 2  # 中文约2字符/token
        
        # 分批处理上下文
        current_context = ""
        batch_results = []
        
        for i, chunk in enumerate(context_chunks):
            chunk_tokens = estimate_tokens(chunk)
            current_tokens = estimate_tokens(current_context)
            
            if current_tokens + chunk_tokens > max_context_tokens and current_context:
                # 当前批次已满，生成中间结果
                batch_prompt = self._build_batch_prompt(user_query, current_context, i)
                messages.append({"role": "user", "content": batch_prompt})
                
                try:
                    result = await self.chat_completion(messages, provider, temperature)
                    batch_results.append(result)
                    yield {"type": "batch_progress", "batch": i, "result": result}
                except Exception as e:
                    logger.error(f"批次处理失败: {e}")
                
                # 重置当前上下文
                current_context = chunk
                messages = [{"role": "system", "content": system_prompt}]
            else:
                current_context += f"\n\n--- 知识片段 {i+1} ---\n{chunk}"
        
        # 处理最后一批
        if current_context:
            final_prompt = self._build_final_prompt(user_query, current_context, batch_results)
            messages.append({"role": "user", "content": final_prompt})
            
            async for token in self.chat_completion_stream(messages, provider, temperature):
                yield {"type": "token", "content": token}
        
        yield {"type": "done", "batch_count": len(batch_results)}
    
    def _build_batch_prompt(self, query: str, context: str, batch_num: int) -> str:
        """构建批次处理提示词"""
        return f"""请基于以下知识片段，提取与用户问题相关的重要信息：

用户问题: {query}

知识片段 (批次{batch_num + 1}):
{context}

请提取关键信息，不要编造内容。"""
    
    def _build_final_prompt(self, query: str, context: str, batch_results: List[str]) -> str:
        """构建最终提示词"""
        batch_section = ""
        if batch_results:
            batch_section = "\n\n## 已提取的关键信息\n" + "\n".join(
                f"- {r}" for r in batch_results if r
            )
        
        return f"""请综合以下所有信息，完整回答用户的问题：

用户问题: {query}

{batch_section}

## 详细知识内容
{context}

请给出完整、准确的回答，并标注信息来源。"""


# ==================== 全局实例 ====================

multi_llm_service = MultiLLMService()


def init_llm_from_settings():
    """从配置初始化LLM服务"""
    from app.core.config import settings
    
    # 主LLM
    primary_config = LLMConfig.from_provider(
        provider=LLMProvider(settings.LLM_PROVIDER),
        api_key=settings.LLM_API_KEY,
        api_base=settings.LLM_API_BASE,
        model=settings.LLM_MODEL,
        temperature=settings.LLM_TEMPERATURE,
        max_tokens=settings.LLM_MAX_TOKENS,
    )
    multi_llm_service.register_provider("primary", primary_config)
    
    # 注册备用提供商（如果配置了）
    for prov_config in getattr(settings, "LLM_FALLBACK_PROVIDERS", []):
        name = prov_config.get("name", f"fallback_{len(multi_llm_service._providers)}")
        config = LLMConfig.from_provider(
            provider=LLMProvider(prov_config["provider"]),
            api_key=prov_config["api_key"],
            api_base=prov_config.get("api_base"),
            model=prov_config.get("model"),
        )
        multi_llm_service.register_provider(name, config)
    
    return multi_llm_service
