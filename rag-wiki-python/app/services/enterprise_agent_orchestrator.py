"""
多Agent协作框架 - 企业场景增强

在现有 agent_orchestrator.py 骨架基础上，提供：
1. 预置企业场景模板（数据分析/文档生成/代码审查/知识问答）
2. Agent间消息传递与状态共享
3. 协作冲突检测与解决
4. 执行结果质量评估
"""
import asyncio
import logging
import uuid
import time
from typing import List, Dict, Any, Optional, Callable
from enum import Enum
from dataclasses import dataclass, field

from app.services.llm_provider import llm_provider

logger = logging.getLogger(__name__)


class CollaborationPattern(Enum):
    """协作模式"""
    SEQUENTIAL = "sequential"       # 顺序执行
    PARALLEL = "parallel"           # 并行执行
    DEBATE = "debate"               # 辩论模式（多Agent讨论后决策）
    HIERARCHICAL = "hierarchical"   # 层级模式（主管分配+审核）
    PIPELINE = "pipeline"           # 流水线模式


class MessageBus:
    """Agent间消息总线"""

    def __init__(self):
        self._messages: Dict[str, List[Dict]] = {}
        self._subscribers: Dict[str, List[Callable]] = {}

    async def publish(self, channel: str, message: Dict[str, Any]):
        """发布消息到频道"""
        if channel not in self._messages:
            self._messages[channel] = []
        message["_timestamp"] = time.time()
        message["_id"] = str(uuid.uuid4())[:8]
        self._messages[channel].append(message)

        # 通知订阅者
        for callback in self._subscribers.get(channel, []):
            try:
                if asyncio.iscoroutinefunction(callback):
                    await callback(message)
                else:
                    callback(message)
            except Exception as e:
                logger.warning(f"消息订阅回调失败: {e}")

    async def subscribe(self, channel: str, callback: Callable):
        """订阅频道"""
        if channel not in self._subscribers:
            self._subscribers[channel] = []
        self._subscribers[channel].append(callback)

    def get_messages(self, channel: str, since: float = 0) -> List[Dict]:
        """获取频道消息"""
        messages = self._messages.get(channel, [])
        return [m for m in messages if m.get("_timestamp", 0) > since]


class SharedState:
    """Agent间共享状态"""

    def __init__(self):
        self._state: Dict[str, Any] = {}
        self._version: Dict[str, int] = {}

    def set(self, key: str, value: Any, agent_id: str = ""):
        self._state[key] = value
        self._version[key] = self._version.get(key, 0) + 1
        logger.debug(f"共享状态更新: key={key}, by={agent_id}, version={self._version[key]}")

    def get(self, key: str, default: Any = None) -> Any:
        return self._state.get(key, default)

    def get_version(self, key: str) -> int:
        return self._version.get(key, 0)

    def snapshot(self) -> Dict[str, Any]:
        return {**self._state, "_versions": {**self._version}}


@dataclass
class AgentCapability:
    """Agent能力描述"""
    name: str
    description: str
    input_schema: Dict[str, Any]
    output_schema: Dict[str, Any]
    max_retries: int = 2


class CollaborativeAgent:
    """协作Agent"""

    def __init__(self, agent_id: str, role: str, system_prompt: str, capabilities: List[AgentCapability]):
        self.agent_id = agent_id
        self.role = role
        self.system_prompt = system_prompt
        self.capabilities = capabilities
        self.status = "IDLE"
        self._message_bus: Optional[MessageBus] = None
        self._shared_state: Optional[SharedState] = None

    def bind(self, message_bus: MessageBus, shared_state: SharedState):
        self._message_bus = message_bus
        self._shared_state = shared_state

    async def execute(self, task: str, context: Dict[str, Any] = None) -> Dict[str, Any]:
        """执行任务"""
        self.status = "RUNNING"
        start = time.time()

        try:
            # 构建prompt
            messages = [{"role": "system", "content": self.system_prompt}]
            if context:
                messages.append({"role": "system", "content": f"上下文信息: {context}"})
            messages.append({"role": "user", "content": task})

            response = await llm_provider.chat(messages=messages, temperature=0.3, max_tokens=2000)
            result = response.get("content", "")

            # 发布结果到消息总线
            if self._message_bus:
                await self._message_bus.publish(f"agent.{self.agent_id}.result", {
                    "agent_id": self.agent_id,
                    "role": self.role,
                    "task": task[:100],
                    "result_preview": result[:200],
                })

            self.status = "COMPLETED"
            return {
                "agent_id": self.agent_id,
                "role": self.role,
                "result": result,
                "elapsed": round(time.time() - start, 2),
                "status": "COMPLETED",
            }
        except Exception as e:
            self.status = "FAILED"
            return {
                "agent_id": self.agent_id,
                "role": self.role,
                "error": str(e),
                "elapsed": round(time.time() - start, 2),
                "status": "FAILED",
            }


# ==================== 企业场景预置模板 ====================

SCENARIO_TEMPLATES = {
    "data_analysis": {
        "name": "数据分析报告",
        "description": "多Agent协作完成数据分析：规划→数据检索→统计分析→可视化建议→报告撰写",
        "pattern": CollaborationPattern.PIPELINE,
        "agents": [
            {"role": "planner", "prompt": "你是数据分析规划专家。根据用户需求制定数据分析计划，明确分析目标、数据来源、分析方法和输出格式。"},
            {"role": "retriever", "prompt": "你是数据检索专家。根据分析计划，从知识库中检索相关数据和信息，整理为结构化格式。"},
            {"role": "analyst", "prompt": "你是统计分析专家。基于检索到的数据，进行统计分析、趋势识别和异常检测，提供数据洞察。"},
            {"role": "writer", "prompt": "你是报告撰写专家。基于分析结果，撰写专业的数据分析报告，包含摘要、方法、发现和建议。"},
        ],
    },
    "code_review": {
        "name": "代码审查",
        "description": "多Agent协作完成代码审查：规范检查→逻辑分析→安全审计→改进建议",
        "pattern": CollaborationPattern.PARALLEL,
        "agents": [
            {"role": "style_checker", "prompt": "你是代码风格审查专家。检查代码是否符合编码规范、命名约定和最佳实践。"},
            {"role": "logic_analyzer", "prompt": "你是逻辑分析专家。审查代码逻辑正确性、边界条件处理、异常处理和性能问题。"},
            {"role": "security_auditor", "prompt": "你是安全审计专家。检查SQL注入、XSS、权限绕过、敏感信息泄露等安全问题。"},
        ],
    },
    "document_generation": {
        "name": "文档生成",
        "description": "多Agent协作生成专业文档：大纲→初稿→审核→定稿",
        "pattern": CollaborationPattern.SEQUENTIAL,
        "agents": [
            {"role": "outliner", "prompt": "你是文档大纲专家。根据主题和需求，生成详细的文档大纲，包含各章节要点。"},
            {"role": "writer", "prompt": "你是技术写作专家。根据大纲和参考资料，撰写完整的文档内容。"},
            {"role": "reviewer", "prompt": "你是文档审核专家。审核文档的完整性、准确性、可读性和格式规范，提出修改意见。"},
        ],
    },
    "knowledge_qa": {
        "name": "深度知识问答",
        "description": "多Agent辩论式深度问答：检索→分析→质疑→综合",
        "pattern": CollaborationPattern.DEBATE,
        "agents": [
            {"role": "retriever", "prompt": "你是知识检索专家。根据问题从知识库中检索最相关的信息，提供事实支撑。"},
            {"role": "analyst", "prompt": "你是分析专家。基于检索结果进行逻辑推理和深度分析，给出初步答案。"},
            {"role": "critic", "prompt": "你是质疑专家。审视初步答案中的逻辑漏洞、事实错误和片面之处，提出反驳和补充。"},
            {"role": "synthesizer", "prompt": "你是综合专家。权衡正反论据，综合各方观点，给出最终平衡的答案。"},
        ],
    },
}


class EnterpriseAgentOrchestrator:
    """企业级多Agent协作编排器"""

    def __init__(self):
        self._message_bus = MessageBus()
        self._shared_state = SharedState()
        self._sessions: Dict[str, Dict] = {}

    async def execute_scenario(
        self,
        scenario_id: str,
        task: str,
        context: Dict[str, Any] = None,
        max_rounds: int = 3,
    ) -> Dict[str, Any]:
        """
        执行预置场景

        Args:
            scenario_id: 场景模板ID
            task: 用户任务描述
            context: 额外上下文
            max_rounds: 最大执行轮次

        Returns:
            执行结果
        """
        template = SCENARIO_TEMPLATES.get(scenario_id)
        if not template:
            return {"error": f"场景模板不存在: {scenario_id}", "available": list(SCENARIO_TEMPLATES.keys())}

        session_id = str(uuid.uuid4())[:12]
        self._sessions[session_id] = {
            "scenario": scenario_id,
            "task": task,
            "started_at": time.time(),
            "status": "RUNNING",
        }

        # 创建Agent实例
        agents = []
        for agent_config in template["agents"]:
            agent = CollaborativeAgent(
                agent_id=f"{session_id}_{agent_config['role']}",
                role=agent_config["role"],
                system_prompt=agent_config["prompt"],
                capabilities=[],
            )
            agent.bind(self._message_bus, self._shared_state)
            agents.append(agent)

        pattern = template["pattern"]
        results = []

        try:
            if pattern == CollaborationPattern.PIPELINE:
                results = await self._execute_pipeline(agents, task, context)
            elif pattern == CollaborationPattern.PARALLEL:
                results = await self._execute_parallel(agents, task, context)
            elif pattern == CollaborationPattern.SEQUENTIAL:
                results = await self._execute_sequential(agents, task, context)
            elif pattern == CollaborationPattern.DEBATE:
                results = await self._execute_debate(agents, task, context, max_rounds)
            elif pattern == CollaborationPattern.HIERARCHICAL:
                results = await self._execute_hierarchical(agents, task, context)
            else:
                results = await self._execute_sequential(agents, task, context)

            # 质量评估
            quality = self._evaluate_quality(results)

            self._sessions[session_id]["status"] = "COMPLETED"
            self._sessions[session_id]["elapsed"] = round(time.time() - self._sessions[session_id]["started_at"], 2)

            return {
                "session_id": session_id,
                "scenario": scenario_id,
                "scenario_name": template["name"],
                "pattern": pattern.value,
                "results": results,
                "quality": quality,
                "elapsed": self._sessions[session_id]["elapsed"],
            }

        except Exception as e:
            logger.error(f"场景执行失败: {e}")
            self._sessions[session_id]["status"] = "FAILED"
            return {"session_id": session_id, "error": str(e), "results": results}

    async def _execute_pipeline(self, agents: List[CollaborativeAgent], task: str, context: Dict = None) -> List[Dict]:
        """流水线执行：每个Agent的输出作为下一个的输入"""
        results = []
        current_input = task
        for agent in agents:
            agent_context = {**(context or {}), "previous_results": results}
            result = await agent.execute(current_input, agent_context)
            results.append(result)
            current_input = result.get("result", "")
        return results

    async def _execute_parallel(self, agents: List[CollaborativeAgent], task: str, context: Dict = None) -> List[Dict]:
        """并行执行：所有Agent同时处理相同任务"""
        tasks = [agent.execute(task, context) for agent in agents]
        return await asyncio.gather(*tasks)

    async def _execute_sequential(self, agents: List[CollaborativeAgent], task: str, context: Dict = None) -> List[Dict]:
        """顺序执行：按顺序依次执行"""
        results = []
        for agent in agents:
            result = await agent.execute(task, context)
            results.append(result)
        return results

    async def _execute_debate(self, agents: List[CollaborativeAgent], task: str, context: Dict = None, max_rounds: int = 3) -> List[Dict]:
        """辩论模式：多轮讨论后综合"""
        results = []
        current_topic = task

        for round_num in range(max_rounds):
            round_results = []
            for agent in agents:
                debate_context = {
                    **(context or {}),
                    "round": round_num + 1,
                    "previous_debate": [r.get("result", "")[:500] for r in results[-len(agents):]] if results else [],
                }
                result = await agent.execute(current_topic, debate_context)
                round_results.append(result)
            results.extend(round_results)

        return results

    async def _execute_hierarchical(self, agents: List[CollaborativeAgent], task: str, context: Dict = None) -> List[Dict]:
        """层级模式：第一个Agent(主管)分配任务，最后一个审核"""
        if len(agents) < 2:
            return await self._execute_sequential(agents, task, context)

        # 主管规划
        supervisor = agents[0]
        plan_result = await supervisor.execute(f"请将以下任务分解为{len(agents)-1}个子任务:\n{task}", context)
        results = [plan_result]

        # 执行子任务
        sub_tasks = plan_result.get("result", "").split("\n")
        for i, agent in enumerate(agents[1:], 1):
            sub_task = sub_tasks[min(i-1, len(sub_tasks)-1)] if sub_tasks else task
            result = await agent.execute(sub_task, context)
            results.append(result)

        return results

    def _evaluate_quality(self, results: List[Dict]) -> Dict[str, Any]:
        """评估协作质量"""
        if not results:
            return {"score": 0, "issues": ["无执行结果"]}

        completed = sum(1 for r in results if r.get("status") == "COMPLETED")
        failed = sum(1 for r in results if r.get("status") == "FAILED")
        total = len(results)

        score = completed / total if total > 0 else 0
        issues = []
        if failed > 0:
            issues.append(f"{failed}个Agent执行失败")
        if score < 0.8:
            issues.append("整体完成率偏低")

        return {
            "score": round(score, 2),
            "completed": completed,
            "failed": failed,
            "total": total,
            "issues": issues,
        }

    def get_scenarios(self) -> List[Dict[str, str]]:
        """获取可用场景列表"""
        return [{"id": k, "name": v["name"], "description": v["description"], "pattern": v["pattern"].value}
                for k, v in SCENARIO_TEMPLATES.items()]

    def get_session(self, session_id: str) -> Optional[Dict]:
        return self._sessions.get(session_id)


# 全局实例
enterprise_orchestrator = EnterpriseAgentOrchestrator()
