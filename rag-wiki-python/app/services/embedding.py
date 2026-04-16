"""
Embedding向量化服务 - 支持多种Embedding提供商
1. OpenAI Embedding (text-embedding-ada-002 / text-embedding-3-small)
2. HuggingFace本地模型 (BAAI/bge-large-zh-v1.5 等)
3. 自定义API (兼容OpenAI接口)
"""
import logging
from abc import ABC, abstractmethod
from typing import List, Optional
from app.core.config import settings

logger = logging.getLogger(__name__)


class BaseEmbedder(ABC):
    """Embedding抽象基类"""

    @abstractmethod
    async def embed_text(self, text: str) -> List[float]:
        """单条文本向量化"""
        pass

    @abstractmethod
    async def embed_texts(self, texts: List[str]) -> List[List[float]]:
        """批量文本向量化"""
        pass

    @abstractmethod
    def get_dimension(self) -> int:
        """获取向量维度"""
        pass


class OpenAIEmbedder(BaseEmbedder):
    """OpenAI Embedding实现"""

    def __init__(self):
        import httpx
        self.api_key = settings.EMBEDDING_API_KEY or settings.LLM_API_KEY
        self.api_base = settings.EMBEDDING_API_BASE
        self.model = settings.EMBEDDING_MODEL
        self.dimension = settings.EMBEDDING_DIMENSION
        self.client = httpx.AsyncClient(
            base_url=self.api_base,
            headers={"Authorization": f"Bearer {self.api_key}"},
            timeout=60.0,
        )

    async def embed_text(self, text: str) -> List[float]:
        result = await self.embed_texts([text])
        return result[0]

    async def embed_texts(self, texts: List[str]) -> List[List[float]]:
        # OpenAI限制每批最多2048条
        batch_size = 2048
        all_embeddings = []

        for i in range(0, len(texts), batch_size):
            batch = texts[i:i + batch_size]
            response = await self.client.post(
                "/embeddings",
                json={
                    "model": self.model,
                    "input": batch,
                },
            )
            response.raise_for_status()
            data = response.json()
            # 按index排序确保顺序正确
            sorted_data = sorted(data["data"], key=lambda x: x["index"])
            all_embeddings.extend([item["embedding"] for item in sorted_data])

        return all_embeddings

    def get_dimension(self) -> int:
        return self.dimension


class HuggingFaceEmbedder(BaseEmbedder):
    """HuggingFace本地模型Embedding实现"""

    def __init__(self):
        from sentence_transformers import SentenceTransformer
        model_name = settings.EMBEDDING_MODEL
        logger.info(f"加载HuggingFace Embedding模型: {model_name}")
        self.model = SentenceTransformer(model_name)
        self._dimension = self.model.get_sentence_embedding_dimension()

    async def embed_text(self, text: str) -> List[float]:
        embedding = self.model.encode(text, normalize_embeddings=True)
        return embedding.tolist()

    async def embed_texts(self, texts: List[str]) -> List[List[float]]:
        embeddings = self.model.encode(texts, normalize_embeddings=True, batch_size=32)
        return embeddings.tolist()

    def get_dimension(self) -> int:
        return self._dimension


class LocalAPIEmbedder(BaseEmbedder):
    """
    本地API Embedding实现 (如: Xinference, Ollama, vLLM等)
    兼容OpenAI接口格式
    """

    def __init__(self):
        import httpx
        self.api_base = settings.EMBEDDING_API_BASE
        self.model = settings.EMBEDDING_MODEL
        self.dimension = settings.EMBEDDING_DIMENSION
        self.client = httpx.AsyncClient(
            base_url=self.api_base,
            timeout=60.0,
        )

    async def embed_text(self, text: str) -> List[float]:
        result = await self.embed_texts([text])
        return result[0]

    async def embed_texts(self, texts: List[str]) -> List[List[float]]:
        response = await self.client.post(
            "/embeddings",
            json={"model": self.model, "input": texts},
        )
        response.raise_for_status()
        data = response.json()
        sorted_data = sorted(data["data"], key=lambda x: x["index"])
        return [item["embedding"] for item in sorted_data]

    def get_dimension(self) -> int:
        return self.dimension


class EmbeddingService:
    """Embedding服务 - 统一入口"""

    def __init__(self):
        self._embedder: Optional[BaseEmbedder] = None

    def _get_embedder(self) -> BaseEmbedder:
        if self._embedder is None:
            provider = settings.EMBEDDING_PROVIDER.lower()
            if provider == "openai":
                self._embedder = OpenAIEmbedder()
            elif provider == "huggingface":
                self._embedder = HuggingFaceEmbedder()
            elif provider == "local":
                self._embedder = LocalAPIEmbedder()
            else:
                logger.warning(f"未知Embedding提供商: {provider}，使用OpenAI")
                self._embedder = OpenAIEmbedder()
            logger.info(f"Embedding服务初始化: provider={provider}, dimension={self._embedder.get_dimension()}")
        return self._embedder

    async def embed_text(self, text: str) -> List[float]:
        """单条文本向量化"""
        embedder = self._get_embedder()
        return await embedder.embed_text(text)

    async def embed_texts(self, texts: List[str]) -> List[List[float]]:
        """批量文本向量化"""
        embedder = self._get_embedder()
        return await embedder.embed_texts(texts)

    def get_dimension(self) -> int:
        """获取向量维度"""
        embedder = self._get_embedder()
        return embedder.get_dimension()


# 全局服务实例
embedding_service = EmbeddingService()
