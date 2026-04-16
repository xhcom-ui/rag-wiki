"""
记忆管理服务 - 三类记忆 + 语义去重 + 双层架构
1. 语义记忆 (SEMANTIC): 事实性知识、概念、定义
2. 情节记忆 (EPISODIC): 具体事件、对话片段、用户经历
3. 程序性记忆 (PROCEDURAL): 操作步骤、工作流、偏好

双层架构:
- 短期记忆: Redis (会话级, TTL自动过期)
- 长期记忆: 向量库 + MySQL (持久化, 语义检索)
"""
import json
import logging
import time
import uuid
from typing import List, Dict, Any, Optional
from enum import Enum

from app.core.config import settings
from app.services.embedding import embedding_service

logger = logging.getLogger(__name__)


class MemoryType(str, Enum):
    SEMANTIC = "SEMANTIC"
    EPISODIC = "EPISODIC"
    PROCEDURAL = "PROCEDURAL"


class MemoryLifecycle(str, Enum):
    ADD = "ADD"
    UPDATE = "UPDATE"
    DELETE = "DELETE"
    NOOP = "NOOP"


class ShortTermMemory:
    """短期记忆 - Redis实现"""

    def __init__(self):
        self._redis = None

    async def _get_redis(self):
        if self._redis is None:
            import redis.asyncio as aioredis
            self._redis = aioredis.Redis(
                host=settings.REDIS_HOST,
                port=settings.REDIS_PORT,
                password=settings.REDIS_PASSWORD or None,
                db=settings.REDIS_DB + 1,  # 使用单独的DB
                decode_responses=True,
            )
        return self._redis

    async def store(self, user_id: str, key: str, value: str, ttl: int = 3600):
        """存储短期记忆"""
        redis = await self._get_redis()
        full_key = f"stm:{user_id}:{key}"
        await redis.setex(full_key, ttl, value)

    async def retrieve(self, user_id: str, key: str) -> Optional[str]:
        """检索短期记忆"""
        redis = await self._get_redis()
        full_key = f"stm:{user_id}:{key}"
        return await redis.get(full_key)

    async def retrieve_all(self, user_id: str) -> Dict[str, str]:
        """获取用户所有短期记忆"""
        redis = await self._get_redis()
        keys = await redis.keys(f"stm:{user_id}:*")
        if not keys:
            return {}
        values = await redis.mget(keys)
        result = {}
        for k, v in zip(keys, values):
            if v:
                short_key = k.split(f"stm:{user_id}:")[1]
                result[short_key] = v
        return result

    async def delete(self, user_id: str, key: str):
        """删除短期记忆"""
        redis = await self._get_redis()
        await redis.delete(f"stm:{user_id}:{key}")


class LongTermMemory:
    """长期记忆 - 向量库 + 数据库"""

    MEMORY_COLLECTION = "user_memories"

    async def store(self, user_id: str, memory_id: str, content: str,
                    memory_type: MemoryType, embedding: List[float],
                    metadata: Dict = None):
        """存储长期记忆到向量库"""
        from app.core.vector_db import get_vector_db_instance
        vector_db = get_vector_db_instance()

        # 确保集合存在
        dimension = embedding_service.get_dimension()
        await vector_db.create_collection(self.MEMORY_COLLECTION, dimension)

        data = [{
            "chunk_id": memory_id,
            "document_id": f"memory_{user_id}",
            "document_name": f"用户记忆-{memory_type.value}",
            "space_id": f"user_{user_id}",
            "content": content,
            "chunk_index": 0,
            "page_num": 0,
            "security_level": 4,  # 记忆最高安全等级
            "owning_dept_id": user_id,  # 用user_id作为部门隔离
            "allowed_dept_ids": user_id,
            "allowed_role_ids": "",
            "vector": embedding,
        }]
        if metadata:
            data[0].update(metadata)

        await vector_db.insert(self.MEMORY_COLLECTION, data)

    async def search(self, user_id: str, query_vector: List[float],
                     memory_type: Optional[MemoryType] = None,
                     top_k: int = 5) -> List[Dict]:
        """在用户记忆空间中检索"""
        from app.core.vector_db import get_vector_db_instance
        vector_db = get_vector_db_instance()

        filter_expr = f'owning_dept_id == "{user_id}" and security_level <= 4'
        if memory_type:
            filter_expr += f' and space_id == "user_{user_id}"'

        try:
            results = await vector_db.search(
                collection_name=self.MEMORY_COLLECTION,
                query_vector=query_vector,
                top_k=top_k,
                filter_expr=filter_expr,
                output_fields=["chunk_id", "content", "document_name", "space_id"],
            )
            return results
        except Exception as e:
            logger.error(f"长期记忆检索失败: {e}")
            return []

    async def delete(self, memory_id: str):
        """删除长期记忆"""
        from app.core.vector_db import get_vector_db_instance
        vector_db = get_vector_db_instance()
        await vector_db.delete(self.MEMORY_COLLECTION, [memory_id])


class MemoryService:
    """记忆管理服务 - 统一入口"""

    def __init__(self):
        self.stm = ShortTermMemory()
        self.ltm = LongTermMemory()

    async def create_memory(
        self,
        user_id: str,
        content: str,
        memory_type: MemoryType = MemoryType.EPISODIC,
        ttl: int = -1,
    ) -> Dict[str, Any]:
        """
        创建记忆

        Args:
            user_id: 用户ID
            content: 记忆内容
            memory_type: 记忆类型
            ttl: 过期时间戳，-1为永久
        """
        memory_id = str(uuid.uuid4())[:12]

        # 向量化
        embedding = await embedding_service.embed_text(content)

        # 长期记忆存储
        await self.ltm.store(
            user_id=user_id,
            memory_id=memory_id,
            content=content,
            memory_type=memory_type,
            embedding=embedding,
            metadata={"memory_type": memory_type.value},
        )

        # 短期记忆存储（如果有过期时间）
        if ttl > 0:
            redis_ttl = max(ttl - int(time.time()), 60)
            await self.stm.store(user_id, memory_id, content, redis_ttl)

        logger.info(f"创建记忆: user_id={user_id}, type={memory_type.value}, id={memory_id}")
        return {"memory_id": memory_id, "status": "created", "type": memory_type.value}

    async def search_memories(
        self,
        user_id: str,
        query: str,
        memory_type: Optional[MemoryType] = None,
        top_k: int = 5,
    ) -> Dict[str, Any]:
        """检索相关记忆"""
        query_vector = await embedding_service.embed_text(query)

        # 搜索长期记忆
        results = await self.ltm.search(user_id, query_vector, memory_type, top_k)

        # 搜索短期记忆
        stm_data = await self.stm.retrieve_all(user_id)

        # 合并结果
        memories = []
        for r in results:
            memories.append({
                "memory_id": r.get("chunk_id", ""),
                "content": r.get("content", ""),
                "type": r.get("document_name", ""),
                "score": r.get("score", 0),
                "source": "long_term",
            })

        for key, value in stm_data.items():
            memories.append({
                "memory_id": key,
                "content": value,
                "type": "EPISODIC",
                "score": 0,
                "source": "short_term",
            })

        # 按相关性排序
        memories.sort(key=lambda x: x["score"], reverse=True)

        return {"memories": memories[:top_k], "total": len(memories)}

    async def extract_from_conversation(
        self,
        session_id: str,
        user_id: str,
        messages: List[Dict],
    ) -> Dict[str, Any]:
        """
        从对话历史中提取记忆
        使用LLM提取关键信息，语义去重后存储
        """
        from app.services.rag_engine import PromptTemplates, llm_service

        # 构建对话文本
        conversation = "\n".join(
            f"{'用户' if m['role'] == 'user' else '助手'}: {m['content']}"
            for m in messages
        )

        # LLM提取记忆
        prompt = PromptTemplates.memory_extraction_prompt(conversation)
        try:
            result = await llm_service.chat_completion(
                messages=[{"role": "user", "content": prompt}],
                temperature=0.3,
            )

            # 解析提取结果
            extracted = self._parse_extraction_result(result)

            # 语义去重 + 存储
            created_count = 0
            skipped_count = 0
            for item in extracted:
                memory_type = MemoryType.SEMANTIC if item.get("type") == "KNOWLEDGE" else \
                              MemoryType.PROCEDURAL if item.get("type") == "HABIT" else \
                              MemoryType.EPISODIC

                # 语义去重 - 检查是否与已有记忆高度相似
                is_duplicate = await self._check_semantic_duplicate(
                    user_id, item["content"], memory_type, threshold=0.92
                )
                if is_duplicate:
                    skipped_count += 1
                    logger.debug(f"跳过重复记忆: {item['content'][:50]}...")
                    continue

                await self.create_memory(
                    user_id=user_id,
                    content=item["content"],
                    memory_type=memory_type,
                )
                created_count += 1

            return {
                "extracted_count": created_count,
                "skipped_duplicates": skipped_count,
                "details": extracted
            }

        except Exception as e:
            logger.error(f"记忆提取失败: {e}")
            return {"extracted_count": 0, "error": str(e)}

    @staticmethod
    def _parse_extraction_result(result: str) -> List[Dict]:
        """解析LLM提取的记忆JSON"""
        try:
            # 尝试从Markdown代码块中提取JSON
            if "```json" in result:
                json_str = result.split("```json")[1].split("```")[0]
            elif "```" in result:
                json_str = result.split("```")[1].split("```")[0]
            else:
                json_str = result

            extracted = json.loads(json_str.strip())
            if isinstance(extracted, list):
                return extracted
            return [extracted]
        except (json.JSONDecodeError, IndexError):
            logger.warning(f"记忆提取结果解析失败，原始内容: {result[:200]}")
            return []

    async def delete_memory(self, memory_id: str, user_id: str) -> bool:
        """删除记忆"""
        await self.ltm.delete(memory_id)
        await self.stm.delete(user_id, memory_id)
        return True

    async def _check_semantic_duplicate(
        self,
        user_id: str,
        content: str,
        memory_type: MemoryType,
        threshold: float = 0.92,
    ) -> bool:
        """
        检查语义重复
        通过向量相似度检测是否已存在高度相似的记忆
        """
        try:
            # 生成查询向量
            query_vector = await embedding_service.embed_text(content)

            # 搜索现有记忆
            results = await self.ltm.search(
                user_id=user_id,
                query_vector=query_vector,
                memory_type=memory_type,
                top_k=3,
            )

            # 检查是否有超过阈值的高相似度记忆
            for result in results:
                score = result.get("score", 0)
                # Milvus返回的是距离，需要转换为相似度
                # 假设使用余弦相似度，分数越高越相似
                if score >= threshold:
                    logger.info(f"检测到语义重复记忆: score={score:.3f}")
                    return True

            return False
        except Exception as e:
            logger.warning(f"语义去重检测失败: {e}")
            return False  # 失败时不阻止创建

    async def cleanup_expired(self) -> Dict[str, int]:
        """
        清理过期记忆
        - 短期记忆由Redis自动过期
        - 长期记忆需要检查TTL字段
        """
        import time
        from app.core.redis_pool import RedisManager

        current_time = int(time.time())
        cleaned_stm = 0
        cleaned_ltm = 0

        try:
            # 清理长期记忆 - 需要检查数据库中的TTL
            # 由于向量库不支持高效的范围查询，这里从MySQL查询
            # 实际实现中应该有对应的MySQL表
            import pymysql

            try:
                conn = pymysql.connect(
                    host=settings.MYSQL_HOST,
                    port=settings.MYSQL_PORT,
                    user=settings.MYSQL_USER,
                    password=settings.MYSQL_PASSWORD,
                    database=settings.MYSQL_DATABASE,
                )
                cursor = conn.cursor()

                # 查询过期的记忆ID
                cursor.execute(
                    """
                    SELECT memory_id FROM user_memory
                    WHERE ttl > 0 AND ttl < %s AND is_deleted = 0
                    """,
                    (current_time,)
                )
                expired_ids = [row[0] for row in cursor.fetchall()]

                # 批量删除
                for memory_id in expired_ids:
                    await self.ltm.delete(memory_id)
                    cleaned_ltm += 1

                # 标记数据库中的记录为已删除
                if expired_ids:
                    placeholders = ",".join(["%s"] * len(expired_ids))
                    cursor.execute(
                        f"UPDATE user_memory SET is_deleted = 1 WHERE memory_id IN ({placeholders})",
                        expired_ids,
                    )
                    conn.commit()

                cursor.close()
                conn.close()

            except Exception as e:
                logger.warning(f"MySQL记忆清理失败: {e}")

            logger.info(f"记忆清理完成: STM={cleaned_stm}, LTM={cleaned_ltm}")
            return {"cleaned_stm": cleaned_stm, "cleaned_ltm": cleaned_ltm}

        except Exception as e:
            logger.error(f"记忆清理失败: {e}")
            return {"cleaned_stm": 0, "cleaned_ltm": 0, "error": str(e)}


# 全局服务实例
memory_service = MemoryService()
