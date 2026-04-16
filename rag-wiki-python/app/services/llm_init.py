"""
LLM初始化引导模块

在Python AI服务启动时，自动加载LLM配置：
1. 优先从Java配置服务获取
2. 回退到本地.env配置
3. 初始化llm_provider
"""
import logging
from app.core.config import settings

logger = logging.getLogger(__name__)


async def init_llm():
    """初始化LLM配置"""
    logger.info("开始初始化LLM配置...")

    try:
        from app.services.ai_config_service import ai_config_service
        result = await ai_config_service.load_configs_from_java()
        if result.get("status") == "success":
            logger.info(f"LLM配置已从Java服务加载: {result.get('count')}个提供商")
        else:
            logger.info("使用本地配置初始化LLM")
            _init_from_env()
    except Exception as e:
        logger.warning(f"LLM初始化异常，使用本地配置: {e}")
        _init_from_env()

    # 初始化llm_provider
    try:
        from app.services.llm_provider import llm_provider
        await llm_provider.initialize()
        logger.info("LLM Provider初始化完成")
    except Exception as e:
        logger.warning(f"LLM Provider初始化失败: {e}")


def _init_from_env():
    """从环境变量/.env初始化配置"""
    from app.services.ai_config_service import ai_config_service

    providers = []
    if settings.OPENAI_API_KEY:
        providers.append("openai")
        ai_config_service._configs["openai"] = {
            "provider": "openai",
            "model_name": settings.OPENAI_MODEL,
            "api_base": settings.OPENAI_API_BASE,
            "api_key": settings.OPENAI_API_KEY,
            "temperature": 0.7,
            "max_tokens": 2000,
            "is_default": True,
        }
        ai_config_service._default_provider = "openai"

    if settings.ZHIPU_API_KEY:
        providers.append("zhipu")
        ai_config_service._configs["zhipu"] = {
            "provider": "zhipu",
            "model_name": "glm-4",
            "api_key": settings.ZHIPU_API_KEY,
            "temperature": 0.7,
            "max_tokens": 2000,
            "is_default": not providers,
        }
        if not providers:
            ai_config_service._default_provider = "zhipu"

    if providers:
        ai_config_service._loaded = True
        logger.info(f"从环境变量加载LLM配置: {providers}")
    else:
        logger.warning("未配置任何LLM提供商，AI功能不可用")
