"""
OpenTelemetry 分布式链路追踪集成

与 SkyWalking OAP 配合，提供 Python AI 服务的链路追踪能力。
支持：
1. FastAPI 请求自动追踪
2. HTTP 客户端调用追踪（httpx/aiohttp）
3. 自定义 Span 标注关键业务逻辑
4. 自动将 trace_id 注入响应头

使用方式：
  from app.core.tracing import tracer, trace_operation
  
  # 自动追踪 FastAPI 请求（通过中间件自动启用）
  
  # 手动追踪关键操作
  with tracer.start_as_current_span("rag.query") as span:
      span.set_attribute("query.text", query)
      span.set_attribute("query.top_k", top_k)
      # ... 执行业务逻辑
"""
import logging
import os
from contextlib import contextmanager
from typing import Optional

from app.core.config import settings

logger = logging.getLogger(__name__)

# ==================== 全局变量 ====================
_tracer_provider = None
_tracer = None
_initialized = False


def init_tracing():
    """
    初始化 OpenTelemetry 追踪
    
    仅在 OTEL_ENABLED=True 时启用，否则为空操作。
    需要安装依赖：
      pip install opentelemetry-api opentelemetry-sdk
      pip install opentelemetry-instrumentation-fastapi
      pip install opentelemetry-exporter-otlp
    """
    global _tracer_provider, _tracer, _initialized
    
    if _initialized:
        return
    
    _initialized = True
    
    if not getattr(settings, 'OTEL_ENABLED', False):
        logger.info("OpenTelemetry 追踪未启用 (OTEL_ENABLED=False)")
        _tracer = NoOpTracer()
        return
    
    try:
        from opentelemetry import trace
        from opentelemetry.sdk.trace import TracerProvider
        from opentelemetry.sdk.trace.export import BatchSpanProcessor
        from opentelemetry.sdk.resources import Resource
        
        # 服务资源
        service_name = getattr(settings, 'OTEL_SERVICE_NAME', 'rag-wiki-python')
        resource = Resource.create({
            "service.name": service_name,
            "service.version": "1.0.0",
            "deployment.environment": getattr(settings, 'ENVIRONMENT', 'development'),
        })
        
        # 创建 TracerProvider
        _tracer_provider = TracerProvider(resource=resource)
        
        # 配置 Exporter
        endpoint = getattr(settings, 'OTEL_EXPORTER_OTLP_ENDPOINT', 'http://localhost:4317')
        _configure_exporter(_tracer_provider, endpoint)
        
        # 设置全局 TracerProvider
        trace.set_tracer_provider(_tracer_provider)
        
        # 获取 Tracer
        _tracer = trace.get_tracer(service_name, "1.0.0")
        
        logger.info(f"OpenTelemetry 追踪已初始化: service={service_name}, endpoint={endpoint}")
        
    except ImportError:
        logger.warning("OpenTelemetry 依赖未安装，追踪功能禁用。请安装: "
                      "pip install opentelemetry-api opentelemetry-sdk "
                      "opentelemetry-exporter-otlp")
        _tracer = NoOpTracer()
    except Exception as e:
        logger.error(f"OpenTelemetry 初始化失败: {e}")
        _tracer = NoOpTracer()


def _configure_exporter(provider, endpoint: str):
    """配置 Span Exporter"""
    try:
        from opentelemetry.exporter.otlp.proto.grpc.trace_exporter import OTLPSpanExporter
        
        exporter = OTLPSpanExporter(endpoint=endpoint, insecure=True)
        processor = BatchSpanProcessor(exporter)
        provider.add_span_processor(processor)
        
        logger.info(f"OTLP Exporter 配置完成: {endpoint}")
    except ImportError:
        # 降级到 console exporter
        try:
            from opentelemetry.sdk.trace.export import ConsoleSpanExporter
            processor = BatchSpanProcessor(ConsoleSpanExporter())
            provider.add_span_processor(processor)
            logger.info("降级使用 ConsoleSpanExporter")
        except Exception as e:
            logger.warning(f"无法配置任何 Exporter: {e}")


def get_tracer():
    """获取全局 Tracer 实例"""
    global _tracer
    if _tracer is None:
        init_tracing()
    return _tracer


# 模块级 tracer 引用
tracer = None  # 延迟初始化


def _ensure_tracer():
    """确保 tracer 已初始化"""
    global tracer
    if tracer is None:
        tracer = get_tracer()
    return tracer


# ==================== 便捷工具 ====================

@contextmanager
def trace_operation(operation_name: str, attributes: Optional[dict] = None):
    """
    追踪关键操作的上下文管理器
    
    用法:
        with trace_operation("rag.query", {"query.text": "如何使用..."}):
            # 执行业务逻辑
            result = rag_service.query(query)
    
    Args:
        operation_name: 操作名称
        attributes: Span 属性字典
    """
    t = _ensure_tracer()
    if isinstance(t, NoOpTracer):
        yield None
        return
    
    try:
        from opentelemetry import trace
        with t.start_as_current_span(operation_name) as span:
            if attributes:
                for key, value in attributes.items():
                    span.set_attribute(key, str(value))
            yield span
    except Exception as e:
        logger.debug(f"追踪操作失败（不影响业务）: {e}")
        yield None


def add_span_event(name: str, attributes: Optional[dict] = None):
    """向当前 Span 添加事件"""
    try:
        from opentelemetry import trace
        span = trace.get_current_span()
        if span and span.is_recording():
            span.add_event(name, attributes or {})
    except Exception as e:
        logger.debug(f"添加Span事件失败(追踪未启用): {e}")


def set_span_attribute(key: str, value: str):
    """设置当前 Span 属性"""
    try:
        from opentelemetry import trace
        span = trace.get_current_span()
        if span and span.is_recording():
            span.set_attribute(key, value)
    except Exception as e:
        logger.debug(f"设置Span属性失败(追踪未启用): {e}")


def get_current_trace_id() -> Optional[str]:
    """获取当前请求的 Trace ID"""
    try:
        from opentelemetry import trace
        span = trace.get_current_span()
        if span and span.get_span_context():
            return format(span.get_span_context().trace_id, '032x')
    except Exception as e:
        logger.debug(f"获取TraceId失败(追踪未启用): {e}")
    return None


# ==================== 空操作 Tracer ====================

class NoOpTracer:
    """
    空操作 Tracer - 追踪未启用时的替代实现
    确保业务代码无需判断追踪是否启用
    """
    
    @contextmanager
    def start_as_current_span(self, name: str, **kwargs):
        yield NoOpSpan()


class NoOpSpan:
    """空操作 Span"""
    
    def set_attribute(self, key: str, value):
        pass
    
    def add_event(self, name: str, attributes=None):
        pass
    
    def is_recording(self):
        return False
    
    def get_span_context(self):
        return None
    
    def __enter__(self):
        return self
    
    def __exit__(self, *args):
        pass


# ==================== FastAPI 中间件 ====================

def setup_fastapi_instrumentation(app):
    """
    为 FastAPI 应用启用自动追踪
    
    在 main.py 中调用:
      from app.core.tracing import setup_fastapi_instrumentation
      setup_fastapi_instrumentation(app)
    """
    if not getattr(settings, 'OTEL_ENABLED', False):
        logger.info("FastAPI 追踪中间件未启用")
        return
    
    try:
        from opentelemetry.instrumentation.fastapi import FastAPIInstrumentor
        FastAPIInstrumentor.instrument_app(app)
        logger.info("FastAPI 自动追踪已启用")
    except ImportError:
        logger.warning("opentelemetry-instrumentation-fastapi 未安装，FastAPI 追踪未启用")
    except Exception as e:
        logger.error(f"FastAPI 追踪初始化失败: {e}")


# ==================== HTTP 客户端追踪 ====================

def setup_httpx_instrumentation():
    """为 httpx 客户端启用自动追踪"""
    if not getattr(settings, 'OTEL_ENABLED', False):
        return
    
    try:
        from opentelemetry.instrumentation.httpx import HTTPXClientInstrumentor
        HTTPXClientInstrumentor().instrument()
        logger.info("httpx 自动追踪已启用")
    except ImportError:
        logger.debug("opentelemetry-instrumentation-httpx 未安装")
    except Exception as e:
        logger.debug(f"httpx 追踪初始化失败: {e}")
