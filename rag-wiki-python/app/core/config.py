"""
全局配置 - 基于pydantic-settings，支持环境变量
"""
from pydantic_settings import BaseSettings
from typing import Optional


class Settings(BaseSettings):
    # 服务配置
    SERVICE_NAME: str = "rag-wiki-python"
    SERVICE_PORT: int = 8000
    DEBUG: bool = False  # 生产环境必须为False

    # 数据库
    MYSQL_HOST: str = "localhost"
    MYSQL_PORT: int = 3306
    MYSQL_USER: str = ""
    MYSQL_PASSWORD: str = ""
    MYSQL_DATABASE: str = "rag_wiki"

    # Redis
    REDIS_HOST: str = "localhost"
    REDIS_PORT: int = 6379
    REDIS_PASSWORD: str = ""
    REDIS_DB: int = 0

    # MinIO
    MINIO_ENDPOINT: str = "localhost:9000"
    MINIO_ACCESS_KEY: str = ""
    MINIO_SECRET_KEY: str = ""
    MINIO_BUCKET: str = "rag-wiki"
    MINIO_SECURE: bool = False

    # RabbitMQ
    RABBITMQ_HOST: str = "localhost"
    RABBITMQ_PORT: int = 5672
    RABBITMQ_USER: str = ""
    RABBITMQ_PASSWORD: str = ""

    # 向量数据库
    VECTOR_DB_TYPE: str = "milvus"  # milvus / qdrant
    MILVUS_HOST: str = "localhost"
    MILVUS_PORT: int = 19530
    QDRANT_HOST: str = "localhost"
    QDRANT_PORT: int = 6333
    QDRANT_API_KEY: Optional[str] = None

    # Elasticsearch
    ES_HOST: str = "localhost"
    ES_PORT: int = 9200

    # LLM配置
    LLM_PROVIDER: str = "openai"  # openai / azure / zhipu / qwen / local
    LLM_API_KEY: str = ""
    LLM_API_BASE: str = "https://api.openai.com/v1"
    LLM_MODEL: str = "gpt-4"
    LLM_TEMPERATURE: float = 0.7
    LLM_MAX_TOKENS: int = 4096

    # OpenAI专用配置
    OPENAI_API_KEY: str = ""
    OPENAI_API_BASE: str = "https://api.openai.com/v1"
    OPENAI_MODEL: str = "gpt-4"

    # 智谱AI专用配置
    ZHIPU_API_KEY: str = ""
    ZHIPU_API_BASE: str = "https://open.bigmodel.cn/api/paas/v4"
    ZHIPU_MODEL: str = "glm-4"

    # Embedding配置
    EMBEDDING_PROVIDER: str = "openai"  # openai / local / huggingface
    EMBEDDING_API_KEY: str = ""
    EMBEDDING_API_BASE: str = "https://api.openai.com/v1"
    EMBEDDING_MODEL: str = "text-embedding-ada-002"
    EMBEDDING_DIMENSION: int = 1536

    # Reranker配置
    RERANKER_MODEL: str = "BAAI/bge-reranker-large"

    # 沙箱配置
    SANDBOX_DOCKER_IMAGE: str = "python:3.10-slim"
    SANDBOX_TIMEOUT: int = 30
    SANDBOX_MEMORY_LIMIT: str = "512m"
    SANDBOX_CPU_QUOTA: int = 50000
    SANDBOX_POOL_SIZE: int = 5

    # 分块配置
    CHUNK_SIZE: int = 512
    CHUNK_OVERLAP: int = 50

    # 检索配置
    RETRIEVAL_TOP_K: int = 10
    RERANK_TOP_K: int = 5
    HYBRID_SEARCH_ALPHA: float = 0.7  # 向量检索权重

    # 权限校验密钥 (与Java网关共享)
    AUTH_SECRET_KEY: str = ""  # 生产环境必须设置

    # OpenTelemetry 链路追踪
    OTEL_ENABLED: bool = False
    OTEL_SERVICE_NAME: str = "rag-wiki-python"
    OTEL_EXPORTER_OTLP_ENDPOINT: str = "http://localhost:4317"
    ENVIRONMENT: str = "development"

    class Config:
        env_file = ".env"
        env_file_encoding = "utf-8"


settings = Settings()
