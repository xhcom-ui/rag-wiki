"""
RAG与对话服务API - 完整实现
"""
import json
import logging
import uuid
from datetime import datetime
from typing import Optional, List
from fastapi import APIRouter, Request
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field, field_validator
from app.core.response import Result, BusinessException
from app.services.rag_engine import rag_engine
from app.services.vector_filter import (
    get_filter_instance, 
    create_user_permission,
    FilterConfig,
    TenantIsolationLevel
)

logger = logging.getLogger(__name__)
router = APIRouter()


class BadCaseType:
    """Badcase类型枚举"""
    RETRIEVAL_FAILURE = "RETRIEVAL_FAILURE"  # 检索失败
    HALLUCINATION = "HALLUCINATION"          # 幻觉生成
    ROUTING_ERROR = "ROUTING_ERROR"          # 路由错误
    KNOWLEDGE_GAP = "KNOWLEDGE_GAP"          # 知识缺失


class BadCaseSource:
    """Badcase来源枚举"""
    USER_FEEDBACK = "USER_FEEDBACK"          # 用户反馈
    CUSTOMER_TICKET = "CUSTOMER_TICKET"      # 客服工单
    AUTO_DETECTION = "AUTO_DETECTION"        # 自动检测


class FeedbackRequest(BaseModel):
    session_id: str = Field(..., min_length=1, max_length=128, description="会话ID")
    message_id: str = Field(..., min_length=1, max_length=128, description="消息ID")
    feedback: str = Field(..., pattern="^(like|dislike|inaccurate|unsafe)$", description="反馈类型")
    comment: Optional[str] = Field(default=None, max_length=1000, description="反馈备注")
    query: Optional[str] = Field(default=None, max_length=2000, description="原始问题")
    answer: Optional[str] = Field(default=None, max_length=5000, description="AI回答")


class RAGQueryRequest(BaseModel):
    question: str = Field(..., min_length=1, max_length=2000, description="用户问题")
    session_id: Optional[str] = Field(default=None, max_length=128, description="会话ID")
    space_id: Optional[str] = Field(default=None, max_length=128, description="知识库空间ID")
    stream: bool = Field(default=True, description="是否流式输出")
    
    # 租户与权限上下文
    tenant_id: Optional[str] = Field(default="default", max_length=64, description="租户ID")
    dept_id: Optional[str] = Field(default=None, max_length=128, description="部门ID")
    role_ids: Optional[List[str]] = Field(default=[], description="角色ID列表")
    allowed_space_ids: Optional[List[str]] = Field(default=[], description="允许访问的空间ID")


@router.post("/query", summary="智能问答（RAG）")
async def rag_query(request: RAGQueryRequest, req: Request):
    """
    增强型RAG问答全流程:
    1. 接入层：身份认证与权限解析
    2. 检索层：权限前置过滤的多路召回
    3. 记忆层：个性化记忆注入
    4. 生成层：安全约束下的LLM生成
    5. 返回层：二次权限校验与结果过滤
    6. 审计层：全链路日志记录
    """
    user_id = getattr(req.state, "user_id", None) or "anonymous"
    security_level = getattr(req.state, "security_level", 1)
    dept_id = getattr(req.state, "dept_id", None) or request.dept_id
    tenant_id = getattr(req.state, "tenant_id", None) or request.tenant_id
    role_ids = getattr(req.state, "role_ids", None) or request.role_ids
    
    # 构建用户权限上下文
    user_permission = create_user_permission(
        user_id=user_id,
        tenant_id=tenant_id,
        dept_id=dept_id or "",
        role_ids=role_ids or [],
        security_level=security_level,
        allowed_space_ids=request.allowed_space_ids or [],
    )
    
    # 获取前置过滤器实例
    vector_filter = get_filter_instance()
    
    # 前置权限验证
    if not vector_filter.validate_permission(user_permission, request.space_id):
        raise BusinessException(code=4003, message="无权访问该知识库空间")
    
    # 构建过滤表达式
    filter_expr = vector_filter.build_filter_expression(
        permission=user_permission,
        space_id=request.space_id,
    )
    
    # 获取Collection名称
    collection_name = vector_filter.get_collection_name(tenant_id, request.space_id)
    
    logger.info(
        f"RAG查询 - 用户={user_id}, 租户={tenant_id}, "
        f"空间={request.space_id}, 安全等级={security_level}"
    )

    try:
        result = await rag_engine.query(
            question=request.question,
            user_id=user_id,
            security_level=security_level,
            dept_id=dept_id,
            space_id=request.space_id,
            session_id=request.session_id,
            filter_expr=filter_expr,
            collection_name=collection_name,
        )
        return Result.success(data=result)
    except BusinessException:
        raise
    except Exception as e:
        logger.error(f"RAG查询失败: {e}")
        raise BusinessException(code=5005, message=f"问答服务异常: {str(e)}")


@router.post("/query/stream", summary="流式智能问答（SSE）")
async def rag_query_stream(request: RAGQueryRequest, req: Request):
    """SSE流式输出的RAG问答"""
    user_id = getattr(req.state, "user_id", None) or "anonymous"
    security_level = getattr(req.state, "security_level", 1)
    dept_id = getattr(req.state, "dept_id", None)

    async def event_generator():
        try:
            async for chunk in rag_engine.query_stream(
                question=request.question,
                user_id=user_id,
                security_level=security_level,
                dept_id=dept_id,
                space_id=request.space_id,
                session_id=request.session_id,
            ):
                yield chunk
        except Exception as e:
            logger.error(f"流式RAG查询失败: {e}")
            yield json.dumps({"type": "error", "data": str(e)}, ensure_ascii=False) + "\n\n"

    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )


@router.get("/sessions/{session_id}", summary="获取对话历史")
async def get_session_history(session_id: str):
    """获取指定会话的对话历史"""
    from app.services.rag_engine import conversation_manager
    messages = await conversation_manager.get_history(session_id)
    return Result.success(data={"session_id": session_id, "messages": messages})


@router.delete("/sessions/{session_id}", summary="清除对话历史")
async def clear_session_history(session_id: str):
    """清除指定会话的对话历史"""
    from app.services.rag_engine import conversation_manager
    await conversation_manager.clear_session(session_id)
    return Result.success()


@router.post("/feedback", summary="答案反馈（点赞/点踩）")
async def submit_feedback(request: FeedbackRequest, req: Request):
    """
    提交答案反馈，用于Badcase收集和闭环优化
    feedback类型：
    - like: 点赞，答案满意
    - dislike: 点踩，答案不满意
    - inaccurate: 答案不准确
    - unsafe: 答案存在安全问题
    """
    user_id = getattr(req.state, "user_id", None) or "anonymous"

    logger.info(f"答案反馈: session={request.session_id}, message={request.message_id}, feedback={request.feedback}")

    # 构建反馈记录
    feedback_id = str(uuid.uuid4())[:12]
    feedback_record = {
        "feedback_id": feedback_id,
        "session_id": request.session_id,
        "message_id": request.message_id,
        "user_id": user_id,
        "feedback_type": request.feedback,
        "comment": request.comment,
        "query": request.query,
        "answer": request.answer,
        "created_at": datetime.now().isoformat(),
    }

    # 存储 feedback 到数据库
    try:
        import pymysql
        from app.core.config import settings

        conn = pymysql.connect(
            host=settings.MYSQL_HOST,
            port=settings.MYSQL_PORT,
            user=settings.MYSQL_USER,
            password=settings.MYSQL_PASSWORD,
            database=settings.MYSQL_DATABASE,
        )
        cursor = conn.cursor()

        # 创建反馈表（如果不存在）
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS rag_feedback (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                feedback_id VARCHAR(64) UNIQUE NOT NULL,
                session_id VARCHAR(64),
                message_id VARCHAR(64),
                user_id VARCHAR(64),
                feedback_type VARCHAR(32),
                comment TEXT,
                query TEXT,
                answer TEXT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_session (session_id),
                INDEX idx_user (user_id),
                INDEX idx_type (feedback_type)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """)

        # 插入反馈记录
        cursor.execute(
            """
            INSERT INTO rag_feedback (feedback_id, session_id, message_id, user_id, feedback_type, comment, query, answer)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
            """,
            (
                feedback_id,
                request.session_id,
                request.message_id,
                user_id,
                request.feedback,
                request.comment,
                request.query,
                request.answer,
            ),
        )
        conn.commit()

    except Exception as e:
        logger.warning(f"反馈存储失败（已记录到日志）: {e}")
        # 即使数据库失败，也记录到日志
        logger.warning(f"反馈记录: {json.dumps(feedback_record, ensure_ascii=False)}")
        if 'conn' in locals() and conn:
            conn.rollback()
    finally:
        if 'cursor' in locals() and cursor:
            cursor.close()
        if 'conn' in locals() and conn:
            conn.close()

    # 负面反馈触发 Badcase 检测
    if request.feedback in ["dislike", "inaccurate", "unsafe"]:
        await _detect_and_create_badcase(
            session_id=request.session_id,
            user_id=user_id,
            query=request.query or "",
            answer=request.answer or "",
            feedback_type=request.feedback,
            comment=request.comment,
        )

    return Result.success(data={"feedback_id": feedback_id})


async def _detect_and_create_badcase(
    session_id: str,
    user_id: str,
    query: str,
    answer: str,
    feedback_type: str,
    comment: Optional[str] = None,
):
    """
    检测并创建 Badcase 记录
    根据反馈类型自动分类，创建待处理的 Badcase
    """
    # 根据 feedback 类型映射 Badcase 类型
    badcase_type_map = {
        "dislike": BadCaseType.RETRIEVAL_FAILURE,
        "inaccurate": BadCaseType.HALLUCINATION,
        "unsafe": BadCaseType.HALLUCINATION,
    }
    badcase_type = badcase_type_map.get(feedback_type, BadCaseType.RETRIEVAL_FAILURE)

    case_id = str(uuid.uuid4())[:12]
    badcase_record = {
        "case_id": case_id,
        "query": query,
        "answer": answer,
        "badcase_type": badcase_type,
        "source": BadCaseSource.USER_FEEDBACK,
        "user_id": user_id,
        "session_id": session_id,
        "status": "NEW",
        "comment": comment,
        "created_at": datetime.now().isoformat(),
    }

    try:
        import pymysql
        from app.core.config import settings

        conn = pymysql.connect(
            host=settings.MYSQL_HOST,
            port=settings.MYSQL_PORT,
            user=settings.MYSQL_USER,
            password=settings.MYSQL_PASSWORD,
            database=settings.MYSQL_DATABASE,
        )
        cursor = conn.cursor()

        # 创建 Badcase 表（如果不存在）
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS rag_badcase (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                case_id VARCHAR(64) UNIQUE NOT NULL,
                query TEXT NOT NULL,
                answer TEXT,
                expected_answer TEXT,
                badcase_type ENUM('RETRIEVAL_FAILURE', 'HALLUCINATION', 'ROUTING_ERROR', 'KNOWLEDGE_GAP') NOT NULL,
                source ENUM('USER_FEEDBACK', 'CUSTOMER_TICKET', 'AUTO_DETECTION') NOT NULL,
                user_id VARCHAR(64),
                session_id VARCHAR(64),
                confidence_score FLOAT,
                status ENUM('NEW', 'IN_REVIEW', 'FIXED', 'WONT_FIX') DEFAULT 'NEW',
                assigned_to VARCHAR(64),
                fix_strategy VARCHAR(255),
                comment TEXT,
                resolved_at TIMESTAMP NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_type_status (badcase_type, status),
                INDEX idx_source_created (source, created_at)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """)

        # 插入 Badcase 记录
        cursor.execute(
            """
            INSERT INTO rag_badcase (case_id, query, answer, badcase_type, source, user_id, session_id, status, comment)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)
            """,
            (case_id, query, answer, badcase_type, BadCaseSource.USER_FEEDBACK, user_id, session_id, "NEW", comment),
        )
        conn.commit()
        cursor.close()
        conn.close()

        logger.info(f"Badcase已创建: case_id={case_id}, type={badcase_type}")

    except Exception as e:
        logger.warning(f"Badcase创建失败（已记录到日志）: {e}")
        logger.warning(f"Badcase记录: {json.dumps(badcase_record, ensure_ascii=False)}")


async def _auto_detect_badcase(query: str, answer: str, sources: List[dict]) -> Optional[str]:
    """
    使用 LLM 自动检测 Badcase
    检测：幻觉、不相关、无来源等问题
    """
    if not answer or not sources:
        return None

    try:
        from app.services.rag_engine import llm_service

        prompt = f"""请评估以下AI回答的质量，判断是否存在问题：

问题: {query}

AI回答: {answer[:500]}

参考资料数量: {len(sources)}

请判断是否存在以下问题（回答 YES 或 NO）：
1. 幻觉: 回答内容与参考资料不符
2. 不相关: 回答与问题不相关
3. 无来源: 回答没有引用来源或来源不可靠

只回答 JSON 格式: {{"has_issue": true/false, "issue_type": "HALLUCINATION/IRRELEVANT/NO_SOURCE/NONE", "confidence": 0.0-1.0}}
"""
        result = await llm_service.chat_completion(
            messages=[{"role": "user", "content": prompt}],
            temperature=0.1,
        )

        # 解析结果
        import json
        if "```json" in result:
            json_str = result.split("```json")[1].split("```")[0]
        else:
            json_str = result

        detection = json.loads(json_str.strip())
        if detection.get("has_issue") and detection.get("confidence", 0) > 0.7:
            return detection.get("issue_type")
        return None

    except Exception as e:
        logger.warning(f"自动检测失败: {e}")
        return None
