"""
智维Wiki Python AI微服务集群 - 主应用入口
"""
import logging
import time
import uvicorn
from fastapi import FastAPI, Request, Response
from fastapi.middleware.cors import CORSMiddleware
from prometheus_client import Counter, Histogram, Gauge, generate_latest, CONTENT_TYPE_LATEST

from app.core.config import settings
from app.core.middleware import PermissionMiddleware
from app.core.response import BusinessException, business_exception_handler, \
    validation_exception_handler, global_exception_handler
from app.api.v1 import document_parser, vector_retrieval, rag_conversation, memory, agent, sandbox, vector_admin

# 配置日志
logging.basicConfig(
    level=logging.DEBUG if settings.DEBUG else logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)

app = FastAPI(
    title="智维Wiki AI服务",
    description="企业级智能安全知识库系统 - Python AI微服务集群",
    version="1.0.0",
    docs_url="/docs",
    redoc_url="/redoc",
)

# 启动时初始化LLM配置
@app.on_event("startup")
async def startup_event():
    from app.services.llm_init import init_llm
    await init_llm()
    # 初始化 OpenTelemetry 链路追踪
    from app.core.tracing import init_tracing, setup_fastapi_instrumentation, setup_httpx_instrumentation
    init_tracing()
    setup_fastapi_instrumentation(app)
    setup_httpx_instrumentation()

# CORS（生产环境应通过环境变量CORS_ORIGINS配置具体域名）
import os
cors_origins = os.getenv("CORS_ORIGINS", "http://localhost:5173,http://localhost:8080").split(",")
app.add_middleware(
    CORSMiddleware,
    allow_origins=cors_origins,
    allow_credentials=True,
    allow_methods=["GET", "POST", "PUT", "DELETE", "OPTIONS"],
    allow_headers=["*"],
)

# 权限验证中间件
app.add_middleware(PermissionMiddleware)

# ==================== Prometheus 指标定义 ====================
REQUEST_COUNT = Counter(
    'rag_wiki_request_total',
    'Total request count',
    ['method', 'endpoint', 'status']
)

REQUEST_LATENCY = Histogram(
    'rag_wiki_request_duration_seconds',
    'Request latency in seconds',
    ['method', 'endpoint'],
    buckets=[0.05, 0.1, 0.25, 0.5, 1.0, 2.5, 5.0, 10.0, 30.0, 60.0]
)

ACTIVE_REQUESTS = Gauge(
    'rag_wiki_active_requests',
    'Active requests being processed',
    ['method', 'endpoint']
)

AI_TASK_COUNT = Counter(
    'rag_wiki_ai_task_total',
    'AI task count by type and status',
    ['task_type', 'status']
)

AI_TASK_DURATION = Histogram(
    'rag_wiki_ai_task_duration_seconds',
    'AI task duration in seconds',
    ['task_type'],
    buckets=[0.5, 1.0, 2.0, 5.0, 10.0, 30.0, 60.0, 120.0, 300.0]
)

VECTOR_SEARCH_COUNT = Counter(
    'rag_wiki_vector_search_total',
    'Vector search count by type',
    ['search_type']
)

VECTOR_SEARCH_DURATION = Histogram(
    'rag_wiki_vector_search_duration_seconds',
    'Vector search duration in seconds',
    ['search_type'],
    buckets=[0.01, 0.05, 0.1, 0.25, 0.5, 1.0, 2.0, 5.0]
)

DOCUMENT_PARSE_COUNT = Counter(
    'rag_wiki_document_parse_total',
    'Document parse count by status',
    ['status']
)

# ==================== Prometheus 监控中间件 ====================
@app.middleware("http")
async def prometheus_middleware(request: Request, call_next):
    # 跳过指标和健康检查端点
    if request.url.path in ("/metrics", "/health"):
        return await call_next(request)

    method = request.method
    endpoint = request.url.path

    ACTIVE_REQUESTS.labels(method=method, endpoint=endpoint).inc()
    start_time = time.time()

    try:
        response: Response = await call_next(request)
        status = response.status_code
        REQUEST_COUNT.labels(method=method, endpoint=endpoint, status=status).inc()
        return response
    except Exception as e:
        REQUEST_COUNT.labels(method=method, endpoint=endpoint, status=500).inc()
        raise
    finally:
        duration = time.time() - start_time
        REQUEST_LATENCY.labels(method=method, endpoint=endpoint).observe(duration)
        ACTIVE_REQUESTS.labels(method=method, endpoint=endpoint).dec()


# Prometheus 指标端点
@app.get("/metrics", include_in_schema=False)
async def metrics():
    return Response(
        content=generate_latest(),
        media_type=CONTENT_TYPE_LATEST
    )

# 注册异常处理器
app.add_exception_handler(BusinessException, business_exception_handler)
app.add_exception_handler(Exception, global_exception_handler)
from fastapi.exceptions import RequestValidationError
app.add_exception_handler(RequestValidationError, validation_exception_handler)

# 注册路由
app.include_router(document_parser.router, prefix="/api/ai/document", tags=["文档解析"])
app.include_router(vector_retrieval.router, prefix="/api/ai/vector", tags=["向量检索"])
app.include_router(vector_admin.router, prefix="/api/ai/vector/admin", tags=["向量库管理"])
app.include_router(rag_conversation.router, prefix="/api/ai/rag", tags=["RAG问答"])
app.include_router(memory.router, prefix="/api/ai/memory", tags=["记忆管理"])
app.include_router(agent.router, prefix="/api/ai/agent", tags=["Agent编排"])
app.include_router(sandbox.router, prefix="/api/ai/sandbox", tags=["代码沙箱"])

# 新增功能路由
from app.api.v1 import content_assist
app.include_router(content_assist.router, prefix="/api/ai/content", tags=["内容创作辅助"])

# 第四阶段功能路由
from app.api.v1 import enterprise_agent
app.include_router(enterprise_agent.router, prefix="/api/ai/enterprise-agent", tags=["多Agent协作"])

from app.api.v1 import badcase_optimizer
app.include_router(badcase_optimizer.router, prefix="/api/ai/badcase-optimizer", tags=["Badcase优化闭环"])

from app.api.v1 import domain_tuning
app.include_router(domain_tuning.router, prefix="/api/ai/domain-tuning", tags=["领域模型微调"])

from app.api.v1 import workflow
app.include_router(workflow.router, prefix="/api/ai/workflow", tags=["智能工作流引擎"])

from app.api.v1 import third_party
app.include_router(third_party.router, prefix="/api/ai/third-party", tags=["第三方系统集成"])

from app.api.v1 import quality_tools
app.include_router(quality_tools.router, prefix="/api/ai/quality", tags=["质量工具(忠实度/冲突)"])


@app.get("/health")
async def health_check():
    return {"status": "ok", "service": "rag-wiki-python", "version": "1.0.0"}


if __name__ == "__main__":
    uvicorn.run(
        "main:app",
        host="0.0.0.0",
        port=settings.SERVICE_PORT,
        reload=settings.DEBUG,
    )
