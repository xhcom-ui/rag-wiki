"""
Agent编排服务 - 多Agent协作框架
1. 规划Agent (PlannerAgent): 分解任务、制定执行计划
2. 检索Agent (RetrieverAgent): 知识库检索、信息收集
3. 编码Agent (CoderAgent): 代码生成、数据分析
4. 审计Agent (AuditorAgent): 结果校验、安全审计
"""
import json
import logging
import uuid
import time
from abc import ABC, abstractmethod
from typing import List, Dict, Any, Optional
from enum import Enum

from app.core.config import settings

logger = logging.getLogger(__name__)


class AgentRole(str, Enum):
    PLANNER = "planner"
    RETRIEVER = "retriever"
    CODER = "coder"
    AUDITOR = "auditor"


class TaskStatus(str, Enum):
    PENDING = "PENDING"
    RUNNING = "RUNNING"
    COMPLETED = "COMPLETED"
    FAILED = "FAILED"
    CANCELLED = "CANCELLED"


class AgentStep:
    """Agent执行步骤"""
    def __init__(self, agent_role: AgentRole, action: str, input_data: Dict,
                 output_data: Dict = None, status: str = "PENDING"):
        self.agent_role = agent_role
        self.action = action
        self.input_data = input_data
        self.output_data = output_data or {}
        self.status = status
        self.started_at = None
        self.completed_at = None

    def to_dict(self):
        return {
            "agent_role": self.agent_role.value,
            "action": self.action,
            "input_data": self.input_data,
            "output_data": self.output_data,
            "status": self.status,
        }


class BaseAgent(ABC):
    """Agent抽象基类"""

    role: AgentRole = None

    @abstractmethod
    async def execute(self, task_description: str, context: Dict[str, Any]) -> Dict[str, Any]:
        """执行Agent任务"""
        pass

    @abstractmethod
    def get_system_prompt(self) -> str:
        """获取Agent系统提示"""
        pass


class PlannerAgent(BaseAgent):
    """规划Agent - 分解任务"""

    role = AgentRole.PLANNER

    def get_system_prompt(self) -> str:
        return """你是一个任务规划专家。根据用户的复杂需求，将其分解为可执行的步骤列表。
每个步骤需要指定:
- agent: 执行者 (retriever/coder/auditor)
- action: 动作描述
- input: 所需输入
- depends_on: 依赖的前置步骤编号

输出JSON格式的步骤列表。"""

    async def execute(self, task_description: str, context: Dict[str, Any]) -> Dict[str, Any]:
        from app.services.rag_engine import llm_service

        prompt = f"""请将以下任务分解为可执行步骤：

任务: {task_description}

可用Agent:
- retriever: 知识库检索、信息搜索
- coder: 代码编写、数据分析
- auditor: 结果校验、安全审查

请输出JSON格式的步骤列表:
```json
[
  {{"step": 1, "agent": "retriever", "action": "动作描述", "input": "所需输入", "depends_on": []}},
  ...
]
```"""
        result = await llm_service.chat_completion(
            messages=[
                {"role": "system", "content": self.get_system_prompt()},
                {"role": "user", "content": prompt},
            ],
            temperature=0.3,
        )

        # 解析步骤
        steps = self._parse_steps(result)
        return {"plan": steps, "raw_response": result}

    @staticmethod
    def _parse_steps(response: str) -> List[Dict]:
        try:
            if "```json" in response:
                json_str = response.split("```json")[1].split("```")[0]
            elif "```" in response:
                json_str = response.split("```")[1].split("```")[0]
            else:
                json_str = response
            return json.loads(json_str.strip())
        except (json.JSONDecodeError, IndexError):
            return [{"step": 1, "agent": "retriever", "action": "检索相关信息",
                     "input": response, "depends_on": []}]


class RetrieverAgent(BaseAgent):
    """检索Agent - 知识库检索"""

    role = AgentRole.RETRIEVER

    def get_system_prompt(self) -> str:
        return "你是知识检索专家，负责从知识库中检索相关信息。"

    async def execute(self, task_description: str, context: Dict[str, Any]) -> Dict[str, Any]:
        from app.services.rag_engine import rag_engine

        query = task_description
        user_id = context.get("user_id", "agent")
        security_level = context.get("security_level", 1)
        space_id = context.get("space_id")

        results = await rag_engine._retrieve(
            query=query,
            security_level=security_level,
            space_id=space_id,
        )

        knowledge = "\n\n".join(
            f"[来源: {r.get('document_name', '未知')}] {r.get('content', '')}"
            for r in results[:5]
        )

        return {"query": query, "results_count": len(results), "knowledge": knowledge}


class CoderAgent(BaseAgent):
    """编码Agent - 代码生成与执行"""

    role = AgentRole.CODER

    def get_system_prompt(self) -> str:
        return """你是数据分析与编程专家。根据需求和提供的知识，生成分析代码。
只生成Python代码，使用pandas进行数据处理，使用matplotlib生成图表。
代码必须是安全的，不能包含网络访问、文件系统操作等危险操作。"""

    async def execute(self, task_description: str, context: Dict[str, Any]) -> Dict[str, Any]:
        from app.services.rag_engine import llm_service

        knowledge = context.get("knowledge", "")
        prompt = f"""基于以下知识，生成分析代码：

需求: {task_description}

相关知识:
{knowledge}

请生成Python分析代码:"""

        result = await llm_service.chat_completion(
            messages=[
                {"role": "system", "content": self.get_system_prompt()},
                {"role": "user", "content": prompt},
            ],
            temperature=0.2,
        )

        # 提取代码
        code = self._extract_code(result)

        # 安全检测
        from app.services.sandbox import sandbox_service
        security_check = sandbox_service.security_check(code)

        return {
            "code": code,
            "security_check": security_check,
            "safe_to_execute": security_check.get("is_safe", False),
        }

    @staticmethod
    def _extract_code(response: str) -> str:
        if "```python" in response:
            return response.split("```python")[1].split("```")[0]
        elif "```" in response:
            return response.split("```")[1].split("```")[0]
        return response


class AuditorAgent(BaseAgent):
    """审计Agent - 结果校验与安全审计"""

    role = AgentRole.AUDITOR

    def get_system_prompt(self) -> str:
        return """你是安全审计专家。检查以下内容:
1. 是否包含敏感信息泄露
2. 是否有不准确的结论
3. 是否有安全隐患
4. 是否需要权限隔离

输出JSON格式的审计报告。"""

    async def execute(self, task_description: str, context: Dict[str, Any]) -> Dict[str, Any]:
        from app.services.rag_engine import llm_service

        result_to_audit = context.get("result", "")
        security_level = context.get("security_level", 1)

        prompt = f"""审计以下内容:

原始任务: {task_description}
安全等级: {security_level}
内容: {result_to_audit[:2000]}

请输出审计报告(JSON):"""

        result = await llm_service.chat_completion(
            messages=[
                {"role": "system", "content": self.get_system_prompt()},
                {"role": "user", "content": prompt},
            ],
            temperature=0.2,
        )

        return {"audit_result": result, "passed": "不安全" not in result}


class AgentOrchestrator:
    """Agent编排器 - 协调多Agent执行"""

    def __init__(self):
        self.agents = {
            AgentRole.PLANNER: PlannerAgent(),
            AgentRole.RETRIEVER: RetrieverAgent(),
            AgentRole.CODER: CoderAgent(),
            AgentRole.AUDITOR: AuditorAgent(),
        }
        self._tasks: Dict[str, Dict] = {}  # 内存中的任务状态

    async def submit_task(
        self,
        task_description: str,
        user_id: str,
        space_id: Optional[str] = None,
        tools: List[str] = None,
        max_steps: int = 10,
    ) -> Dict[str, Any]:
        """提交Agent任务"""
        task_id = str(uuid.uuid4())[:12]

        task = {
            "task_id": task_id,
            "description": task_description,
            "user_id": user_id,
            "space_id": space_id,
            "status": TaskStatus.RUNNING,
            "steps": [],
            "result": None,
            "created_at": time.time(),
        }
        self._tasks[task_id] = task

        # 异步执行
        try:
            result = await self._execute_task(task)
            task["status"] = TaskStatus.COMPLETED
            task["result"] = result
        except Exception as e:
            logger.error(f"Agent任务执行失败: {e}")
            task["status"] = TaskStatus.FAILED
            task["result"] = {"error": str(e)}

        return {"task_id": task_id, "status": task["status"].value, "result": task["result"]}

    async def _execute_task(self, task: Dict) -> Dict[str, Any]:
        """执行完整Agent任务流程"""
        context = {
            "user_id": task["user_id"],
            "space_id": task.get("space_id"),
            "security_level": 1,
        }

        # Step 1: 规划
        planner = self.agents[AgentRole.PLANNER]
        plan_result = await planner.execute(task["description"], context)
        steps = plan_result.get("plan", [])
        task["steps"].append(AgentStep(AgentRole.PLANNER, "task_decomposition",
                                       {"description": task["description"]}, plan_result, "COMPLETED").to_dict())

        # Step 2-3: 逐步执行
        accumulated_context = {**context}

        for step in steps:
            agent_name = step.get("agent", "retriever")
            action = step.get("action", "")
            step_input = step.get("input", "")

            try:
                agent_role = AgentRole(agent_name)
            except ValueError:
                agent_role = AgentRole.RETRIEVER

            agent = self.agents.get(agent_role)
            if not agent:
                continue

            step_result = await agent.execute(action or step_input, {**accumulated_context, **context})

            # 将结果注入到后续上下文
            if agent_role == AgentRole.RETRIEVER:
                accumulated_context["knowledge"] = step_result.get("knowledge", "")
            elif agent_role == AgentRole.CODER:
                accumulated_context["code"] = step_result.get("code", "")
            elif agent_role == AgentRole.AUDITOR:
                accumulated_context["audit"] = step_result.get("audit_result", "")

            task["steps"].append(AgentStep(
                agent_role, action, {"input": step_input}, step_result, "COMPLETED"
            ).to_dict())

        # Step 4: 审计最终结果
        if AgentRole.AUDITOR not in [AgentRole(s.get("agent", "retriever")) for s in steps]:
            auditor = self.agents[AgentRole.AUDITOR]
            audit_result = await auditor.execute(
                task["description"],
                {**accumulated_context, "result": str(accumulated_context), **context},
            )
            task["steps"].append(AgentStep(
                AgentRole.AUDITOR, "final_audit", {}, audit_result, "COMPLETED"
            ).to_dict())

        return accumulated_context

    def get_task_status(self, task_id: str) -> Optional[Dict]:
        """查询任务状态"""
        task = self._tasks.get(task_id)
        if not task:
            return None
        return {
            "task_id": task["task_id"],
            "status": task["status"].value,
            "steps": task["steps"],
            "result": task["result"],
        }

    def cancel_task(self, task_id: str) -> bool:
        """取消任务"""
        task = self._tasks.get(task_id)
        if task and task["status"] == TaskStatus.RUNNING:
            task["status"] = TaskStatus.CANCELLED
            return True
        return False


# 全局编排器实例
agent_orchestrator = AgentOrchestrator()
