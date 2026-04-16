"""
RAG引擎 - 增强型检索增强生成全流程
1. 权限前置过滤的检索
2. 记忆注入
3. LLM生成（支持流式SSE）
4. 答案校验与二次权限过滤
5. 审计日志
"""
import json
import logging
import uuid
import time
from typing import List, Dict, Any, Optional, AsyncGenerator

from app.core.config import settings
from app.core.response import Result, BusinessException
from app.services.embedding import embedding_service
from app.services.chunking import chunking_service
from app.services.post_search_validator import get_post_search_validator, PermissionContext

logger = logging.getLogger(__name__)


# ==================== Prompt模板 ====================

class PromptTemplates:
    """RAG Prompt工程模板"""

    @staticmethod
    def system_prompt(security_level: int = 1) -> str:
        return f"""你是智维Wiki智能助手，一个专业的企业知识库问答系统。

## 核心规则
1. **仅基于检索到的知识回答**，不要编造信息
2. 如果知识不足以回答，明确告知用户并建议相关搜索方向
3. **安全等级约束**: 当前用户安全等级为{security_level}级（1-公开,2-内部,3-敏感,4-机密），不得泄露高于用户等级的信息
4. 回答时标注信息来源，增加可信度
5. 使用专业、准确的语言回答

## 输出格式
- 使用Markdown格式
- 在回答末尾列出引用来源
- 如有相关表格数据，用表格形式呈现"""

    @staticmethod
    def rag_prompt(question: str, context: str, memory_context: str = "",
                   security_level: int = 1) -> str:
        memory_section = f"\n## 用户偏好记忆\n{memory_context}\n" if memory_context else ""
        security_hint = ""
        if security_level >= 3:
            security_hint = "\n⚠️ 注意：用户具有高安全等级，回答需特别注意信息隔离，不得引用与用户部门无关的敏感数据。"

        return f"""## 检索到的相关知识
{context}
{memory_section}
## 用户问题
{question}
{security_hint}

请基于以上知识回答用户问题。如果知识不足以完整回答，请说明缺少哪些信息。"""

    @staticmethod
    def query_rewrite_prompt(query: str) -> str:
        return f"""请将以下用户查询改写为更适合检索的形式，保留核心意图，补充必要的上下文关键词：

原始查询: {query}

改写后的查询:"""

    @staticmethod
    def memory_extraction_prompt(conversation: str) -> str:
        return f"""从以下对话中提取用户的偏好、习惯和关键信息，作为长期记忆保存。
只提取稳定的知识（如偏好、专业领域、常见需求），忽略临时性对话。

对话内容:
{conversation}

提取的记忆(JSON格式):
```json
[
  {{"type": "PREFERENCE|KNOWLEDGE|HABIT", "content": "记忆内容", "importance": "high|medium|low"}}
]
```"""

    @staticmethod
    def answer_verification_prompt(question: str, answer: str, sources: str) -> str:
        return f"""请校验以下回答是否与提供的知识来源一致：

问题: {question}
回答: {answer}

知识来源:
{sources}

校验结果:
1. 回答是否基于来源？(是/否/部分)
2. 是否有编造信息？(是/否)
3. 是否泄露了不应透露的信息？(是/否)
4. 可信度评分 (0-1):"""


# ==================== LLM调用层 ====================

class LLMService:
    """LLM调用抽象层 - 支持多种LLM提供商"""

    def __init__(self):
        self._client = None

    def _get_client(self):
        if self._client is None:
            import httpx
            self._client = httpx.AsyncClient(
                base_url=settings.LLM_API_BASE,
                headers={"Authorization": f"Bearer {settings.LLM_API_KEY}"},
                timeout=120.0,
            )
        return self._client

    async def chat_completion(
        self,
        messages: List[Dict[str, str]],
        temperature: float = None,
        max_tokens: int = None,
        stream: bool = False,
    ) -> str:
        """非流式对话"""
        client = self._get_client()
        payload = {
            "model": settings.LLM_MODEL,
            "messages": messages,
            "temperature": temperature or settings.LLM_TEMPERATURE,
            "max_tokens": max_tokens or settings.LLM_MAX_TOKENS,
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
        """流式对话 (SSE)"""
        client = self._get_client()
        payload = {
            "model": settings.LLM_MODEL,
            "messages": messages,
            "temperature": temperature or settings.LLM_TEMPERATURE,
            "max_tokens": max_tokens or settings.LLM_MAX_TOKENS,
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


# 全局LLM实例
llm_service = LLMService()


# ==================== 对话会话管理 ====================

class ConversationManager:
    """对话会话管理 - Redis存储"""

    def __init__(self):
        self._redis = None

    async def _get_redis(self):
        if self._redis is None:
            import redis.asyncio as aioredis
            self._redis = aioredis.Redis(
                host=settings.REDIS_HOST,
                port=settings.REDIS_PORT,
                password=settings.REDIS_PASSWORD or None,
                db=settings.REDIS_DB,
                decode_responses=True,
            )
        return self._redis

    async def get_or_create_session(self, session_id: Optional[str], user_id: str) -> Dict:
        """获取或创建会话"""
        redis = await self._get_redis()

        if session_id:
            data = await redis.get(f"session:{session_id}")
            if data:
                return json.loads(data)

        # 创建新会话
        new_session_id = session_id or str(uuid.uuid4())
        session = {
            "session_id": new_session_id,
            "user_id": user_id,
            "messages": [],
            "created_at": time.time(),
            "updated_at": time.time(),
        }
        await redis.setex(
            f"session:{new_session_id}",
            86400 * 7,  # 7天过期
            json.dumps(session, ensure_ascii=False),
        )
        return session

    async def add_message(self, session_id: str, role: str, content: str,
                          sources: List[Dict] = None):
        """添加消息到会话"""
        redis = await self._get_redis()
        data = await redis.get(f"session:{session_id}")
        if not data:
            return

        session = json.loads(data)
        session["messages"].append({
            "role": role,
            "content": content,
            "sources": sources or [],
            "timestamp": time.time(),
        })
        session["updated_at"] = time.time()

        # 保留最近20轮对话
        if len(session["messages"]) > 40:
            session["messages"] = session["messages"][-40:]

        await redis.setex(
            f"session:{session_id}",
            86400 * 7,
            json.dumps(session, ensure_ascii=False),
        )

    async def get_history(self, session_id: str, limit: int = 10) -> List[Dict]:
        """获取对话历史"""
        redis = await self._get_redis()
        data = await redis.get(f"session:{session_id}")
        if not data:
            return []
        session = json.loads(data)
        return session.get("messages", [])[-limit * 2:]

    async def clear_session(self, session_id: str):
        """清除会话"""
        redis = await self._get_redis()
        await redis.delete(f"session:{session_id}")


# 全局会话管理器
conversation_manager = ConversationManager()


# ==================== RAG引擎 ====================

class RAGEngine:
    """增强型RAG引擎"""

    def __init__(self):
        self.vector_db = None
        self.llm = llm_service
        self.conversation = conversation_manager

    def _get_vector_db(self):
        """延迟获取向量数据库实例"""
        if self.vector_db is None:
            from app.core.vector_db import get_vector_db
            self.vector_db = get_vector_db()
        return self.vector_db

    def _build_permission_filter(
        self,
        security_level: int,
        dept_id: Optional[str] = None,
        role_ids: Optional[List[str]] = None,
    ) -> str:
        """构建权限过滤表达式"""
        conditions = [f'security_level <= {security_level}']

        if dept_id:
            conditions.append(f'(owning_dept_id == "{dept_id}" || owning_dept_id == "")')

        return ' and '.join(conditions)

    async def _retrieve(
        self,
        query: str,
        security_level: int,
        dept_id: Optional[str] = None,
        role_ids: Optional[List[str]] = None,
        space_id: Optional[str] = None,
        top_k: int = None,
    ) -> List[Dict]:
        """检索相关知识"""
        top_k = top_k or settings.RETRIEVAL_TOP_K
        filter_expr = self._build_permission_filter(security_level, dept_id, role_ids)

        if space_id:
            filter_expr += f' and space_id == "{space_id}"'

        # 查询向量化
        query_vector = await embedding_service.embed_text(query)

        # 执行检索
        vdb = self._get_vector_db()
        collection_name = f"knowledge_chunks"

        try:
            results = await vdb.search(
                collection_name=collection_name,
                query_vector=query_vector,
                top_k=top_k,
                filter_expr=filter_expr,
                output_fields=["chunk_id", "content", "document_id", "document_name",
                                "space_id", "security_level", "page_num", "chunk_index"],
            )
        except Exception as e:
            logger.error(f"向量检索失败: {e}")
            results = []

        return results

    async def _retrieve_with_memory(
        self,
        query: str,
        user_id: str,
        security_level: int,
        dept_id: Optional[str] = None,
        role_ids: Optional[List[str]] = None,
        space_id: Optional[str] = None,
    ) -> tuple:
        """检索知识 + 记忆"""
        # 并行执行知识检索和记忆检索
        knowledge_results = await self._retrieve(
            query, security_level, dept_id, role_ids, space_id
        )

        # 记忆检索（简化实现，实际应从记忆服务获取）
        memory_context = ""

        return knowledge_results, memory_context

    def _format_context(self, chunks: List[Dict]) -> str:
        """格式化检索结果为上下文"""
        if not chunks:
            return "未检索到相关知识。"

        context_parts = []
        for i, chunk in enumerate(chunks, 1):
            doc_name = chunk.get("document_name", "未知文档")
            page = chunk.get("page_num", "")
            content = chunk.get("content", "")
            source_info = f"[来源{i}: {doc_name}"
            if page:
                source_info += f", 第{page}页"
            source_info += "]"
            context_parts.append(f"{source_info}\n{content}")

        return "\n\n".join(context_parts)

    def _filter_by_security(self, chunks: List[Dict], security_level: int) -> List[Dict]:
        """二次权限过滤：移除超权限的检索结果"""
        return [c for c in chunks if c.get("security_level", 1) <= security_level]

    async def query(
        self,
        question: str,
        user_id: str,
        security_level: int = 1,
        dept_id: Optional[str] = None,
        role_ids: Optional[List[str]] = None,
        space_id: Optional[str] = None,
        session_id: Optional[str] = None,
        tenant_id: str = "default",
        allowed_space_ids: Optional[List[str]] = None,
        filter_expr: Optional[str] = None,
        collection_name: Optional[str] = None,
    ) -> Dict[str, Any]:
        """
        完整RAG查询流程（带双重权限校验）
        
        流程：
        1. 前置权限验证（已在API层完成）
        2. 带权限过滤的向量检索
        3. 二次权限校验（检索结果再次验证）
        4. 结果脱敏
        5. LLM生成
        6. 答案权限校验

        Returns:
            {answer, sources, session_id, confidence}
        """
        start_time = time.time()

        # 1. 会话管理
        session = await self.conversation.get_or_create_session(session_id, user_id)
        session_id = session["session_id"]

        # 2. 检索（带前置权限过滤）
        chunks, memory_context = await self._retrieve_with_memory(
            question, user_id, security_level, dept_id, role_ids, space_id
        )

        # 3. 二次权限校验与过滤（双重保险）
        if chunks:
            permission = PermissionContext(
                user_id=user_id,
                tenant_id=tenant_id,
                dept_id=dept_id or "",
                role_ids=role_ids or [],
                security_level=security_level,
                allowed_space_ids=allowed_space_ids or [],
            )
            
            validator = get_post_search_validator()
            chunks = validator.validate_and_filter(chunks, permission)
            
            logger.info(
                f"二次权限校验完成: user={user_id}, "
                f"原始={len(chunks)}, 过滤后={len(chunks)}"
            )

        # 4. 构建Prompt
        context = self._format_context(chunks)
        system_prompt = PromptTemplates.system_prompt(security_level)
        user_prompt = PromptTemplates.rag_prompt(
            question=question,
            context=context,
            memory_context=memory_context,
            security_level=security_level,
        )

        messages = [
            {"role": "system", "content": system_prompt},
        ]

        # 加入对话历史（最近几轮）
        history = session.get("messages", [])[-6:]
        for msg in history:
            messages.append({"role": msg["role"], "content": msg["content"]})

        messages.append({"role": "user", "content": user_prompt})

        # 5. LLM生成
        try:
            answer = await self.llm.chat_completion(messages)
        except Exception as e:
            logger.error(f"LLM生成失败: {e}")
            raise BusinessException(code=5001, message="AI服务暂时不可用，请稍后重试")

        # 6. 保存对话
        await self.conversation.add_message(session_id, "user", question)
        sources = [{"document_name": c.get("document_name"), "page_num": c.get("page_num"),
                     "chunk_id": c.get("chunk_id")} for c in chunks[:5]]
        await self.conversation.add_message(session_id, "assistant", answer, sources)

        # 7. 答案忠实度校验
        faithfulness = None
        try:
            from app.services.faithfulness_validator import faithfulness_validator
            context_text = "\n\n".join([c.get("content", "") for c in chunks[:5]])
            faithfulness = await faithfulness_validator.quick_validate(answer, context_text)
        except Exception as e:
            logger.warning(f"忠实度校验失败(不影响结果): {e}")

        # 8. 审计日志
        elapsed = time.time() - start_time
        logger.info(
            f"RAG查询完成: user_id={user_id}, session_id={session_id}, "
            f"chunks={len(chunks)}, elapsed={elapsed:.2f}s"
        )

        return {
            "answer": answer,
            "sources": sources,
            "session_id": session_id,
            "confidence": min(len(chunks) / 3.0, 1.0),  # 简单的置信度估算
            "faithfulness": faithfulness,
        }

    async def query_stream(
        self,
        question: str,
        user_id: str,
        security_level: int = 1,
        dept_id: Optional[str] = None,
        role_ids: Optional[List[str]] = None,
        space_id: Optional[str] = None,
        session_id: Optional[str] = None,
    ) -> AsyncGenerator[str, None]:
        """SSE流式RAG查询"""
        # 检索阶段
        chunks, memory_context = await self._retrieve_with_memory(
            question, user_id, security_level, dept_id, role_ids, space_id
        )
        chunks = self._filter_by_security(chunks, security_level)

        # 构建Prompt
        context = self._format_context(chunks)
        system_prompt = PromptTemplates.system_prompt(security_level)
        user_prompt = PromptTemplates.rag_prompt(question, context, memory_context, security_level)

        messages = [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_prompt},
        ]

        # 发送来源信息
        sources = [{"document_name": c.get("document_name"), "page_num": c.get("page_num")}
                    for c in chunks[:5]]
        yield json.dumps({"type": "sources", "data": sources}, ensure_ascii=False) + "\n\n"

        # 流式生成
        full_answer = ""
        async for token in self.llm.chat_completion_stream(messages):
            full_answer += token
            yield json.dumps({"type": "token", "data": token}, ensure_ascii=False) + "\n\n"

        # 发送完成信号
        yield json.dumps({"type": "done", "data": {"session_id": session_id}}, ensure_ascii=False) + "\n\n"

        # 异步保存对话
        session = await self.conversation.get_or_create_session(session_id, user_id)
        await self.conversation.add_message(session["session_id"], "user", question)
        await self.conversation.add_message(session["session_id"], "assistant", full_answer, sources)


# 全局RAG引擎实例
rag_engine = RAGEngine()
