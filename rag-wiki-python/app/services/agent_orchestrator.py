"""
Agent编排服务 - 多Agent协作框架
支持：
1. 多角色Agent协同（规划/检索/编码/审计）
2. 工具调用（知识库检索/代码执行/API调用）
3. 工作流编排
4. 断点续传与中间结果查看
"""

from typing import List, Dict, Any, Optional, Callable
from enum import Enum
from dataclasses import dataclass, field
import logging
import uuid
import time
from abc import ABC, abstractmethod

logger = logging.getLogger(__name__)


class AgentRole(Enum):
    """Agent角色类型"""
    PLANNER = "PLANNER"          # 规划Agent
    RETRIEVER = "RETRIEVER"      # 检索Agent
    CODER = "CODER"              # 编码Agent
    AUDITOR = "AUDITOR"          # 审计Agent
    WRITER = "WRITER"            # 写作Agent
    ANALYST = "ANALYST"          # 分析Agent


class AgentStatus(Enum):
    """Agent状态"""
    PENDING = "PENDING"
    RUNNING = "RUNNING"
    COMPLETED = "COMPLETED"
    FAILED = "FAILED"
    WAITING = "WAITING"


class ToolType(Enum):
    """工具类型"""
    KNOWLEDGE_SEARCH = "knowledge_search"
    CODE_EXECUTION = "code_execution"
    API_CALL = "api_call"
    FILE_PROCESSING = "file_processing"
    DATA_ANALYSIS = "data_analysis"


@dataclass
class ToolDefinition:
    """工具定义"""
    tool_id: str
    tool_type: ToolType
    name: str
    description: str
    parameters: Dict[str, Any]
    handler: Optional[Callable] = None


@dataclass
class AgentTask:
    """Agent任务"""
    task_id: str
    agent_role: AgentRole
    description: str
    input_data: Dict[str, Any]
    status: AgentStatus = AgentStatus.PENDING
    output_data: Optional[Dict[str, Any]] = None
    error_message: Optional[str] = None
    start_time: Optional[float] = None
    end_time: Optional[float] = None
    tools: List[str] = field(default_factory=list)


@dataclass
class WorkflowExecution:
    """工作流执行实例"""
    workflow_id: str
    name: str
    description: str
    tasks: List[AgentTask] = field(default_factory=list)
    status: AgentStatus = AgentStatus.PENDING
    current_task_index: int = 0
    result: Optional[Dict[str, Any]] = None
    error_message: Optional[str] = None
    start_time: Optional[float] = None
    end_time: Optional[float] = None
    metadata: Dict[str, Any] = field(default_factory=dict)


class BaseAgent(ABC):
    """Agent基类"""
    
    def __init__(self, role: AgentRole, name: str):
        self.role = role
        self.name = name
        self.agent_id = f"{role.value}_{uuid.uuid4().hex[:8]}"
        self.tools: Dict[str, ToolDefinition] = {}
    
    def register_tool(self, tool: ToolDefinition):
        """注册工具"""
        self.tools[tool.tool_id] = tool
        logger.info(f"Agent {self.name} 注册工具: {tool.name}")
    
    @abstractmethod
    async def execute(self, task: AgentTask) -> Dict[str, Any]:
        """执行任务"""
        pass
    
    async def use_tool(self, tool_id: str, **kwargs) -> Any:
        """使用工具"""
        if tool_id not in self.tools:
            raise ValueError(f"工具 {tool_id} 未注册")
        
        tool = self.tools[tool_id]
        if tool.handler is None:
            raise ValueError(f"工具 {tool_id} 未配置处理器")
        
        logger.info(f"Agent {self.name} 使用工具: {tool.name}")
        return await tool.handler(**kwargs)


class PlannerAgent(BaseAgent):
    """规划Agent - 负责任务分解与路径规划"""
    
    def __init__(self):
        super().__init__(AgentRole.PLANNER, "规划Agent")
    
    async def execute(self, task: AgentTask) -> Dict[str, Any]:
        """
        执行规划任务
        
        输入：复杂任务描述
        输出：任务分解序列
        """
        logger.info(f"规划Agent开始执行: {task.description}")
        
        # 简化实现：实际应调用LLM进行智能规划
        plan = {
            "steps": [
                {"role": "RETRIEVER", "action": "检索相关知识"},
                {"role": "ANALYST", "action": "分析知识内容"},
                {"role": "WRITER", "action": "生成回答"},
            ],
            "estimated_time": 30,
        }
        
        return {
            "plan": plan,
            "agent_id": self.agent_id,
        }


class RetrieverAgent(BaseAgent):
    """检索Agent - 负责知识检索与信息提取"""
    
    def __init__(self):
        super().__init__(AgentRole.RETRIEVER, "检索Agent")
    
    async def execute(self, task: AgentTask) -> Dict[str, Any]:
        """
        执行检索任务
        
        输入：查询问题
        输出：检索结果
        """
        logger.info(f"检索Agent开始执行: {task.description}")
        
        # 调用知识检索工具
        if "knowledge_search" in self.tools:
            results = await self.use_tool(
                "knowledge_search",
                query=task.input_data.get("query", ""),
                top_k=task.input_data.get("top_k", 10),
            )
            return {"results": results, "count": len(results)}
        
        return {"results": [], "count": 0}


class CoderAgent(BaseAgent):
    """编码Agent - 负责代码生成与执行"""
    
    def __init__(self):
        super().__init__(AgentRole.CODER, "编码Agent")
    
    async def execute(self, task: AgentTask) -> Dict[str, Any]:
        """
        执行编码任务
        
        输入：代码需求
        输出：代码执行结果
        """
        logger.info(f"编码Agent开始执行: {task.description}")
        
        # 调用代码执行工具
        if "code_execution" in self.tools:
            result = await self.use_tool(
                "code_execution",
                code=task.input_data.get("code", ""),
                language=task.input_data.get("language", "python"),
            )
            return {"execution_result": result}
        
        return {"execution_result": None}


class WorkflowEngine:
    """工作流引擎 - 负责Agent编排与任务调度"""
    
    def __init__(self):
        self.agents: Dict[AgentRole, BaseAgent] = {}
        self.executions: Dict[str, WorkflowExecution] = {}
    
    def register_agent(self, agent: BaseAgent):
        """注册Agent"""
        self.agents[agent.role] = agent
        logger.info(f"注册Agent: {agent.name} ({agent.role.value})")
    
    async def create_workflow(
        self,
        name: str,
        description: str,
        tasks: List[Dict[str, Any]],
    ) -> WorkflowExecution:
        """
        创建工作流
        
        tasks示例：
        [
            {"role": "PLANNER", "description": "任务规划", "input": {...}},
            {"role": "RETRIEVER", "description": "知识检索", "input": {...}},
        ]
        """
        workflow_id = f"wf_{uuid.uuid4().hex[:12]}"
        
        agent_tasks = []
        for i, task_def in enumerate(tasks):
            task = AgentTask(
                task_id=f"task_{i}",
                agent_role=AgentRole(task_def["role"]),
                description=task_def["description"],
                input_data=task_def.get("input", {}),
                tools=task_def.get("tools", []),
            )
            agent_tasks.append(task)
        
        workflow = WorkflowExecution(
            workflow_id=workflow_id,
            name=name,
            description=description,
            tasks=agent_tasks,
        )
        
        self.executions[workflow_id] = workflow
        logger.info(f"创建工作流: {name} (ID: {workflow_id})")
        
        return workflow
    
    async def execute_workflow(self, workflow_id: str) -> Dict[str, Any]:
        """
        执行工作流
        
        按顺序执行每个任务，支持断点续传
        """
        if workflow_id not in self.executions:
            raise ValueError(f"工作流 {workflow_id} 不存在")
        
        workflow = self.executions[workflow_id]
        workflow.status = AgentStatus.RUNNING
        workflow.start_time = time.time()
        
        try:
            # 从当前任务索引开始执行
            for i in range(workflow.current_task_index, len(workflow.tasks)):
                task = workflow.tasks[i]
                workflow.current_task_index = i
                
                # 获取对应Agent
                agent = self.agents.get(task.agent_role)
                if not agent:
                    raise ValueError(f"Agent {task.agent_role.value} 未注册")
                
                # 执行任务
                task.status = AgentStatus.RUNNING
                task.start_time = time.time()
                
                logger.info(
                    f"执行任务 {i+1}/{len(workflow.tasks)}: "
                    f"{task.description} (by {task.agent_role.value})"
                )
                
                try:
                    result = await agent.execute(task)
                    task.output_data = result
                    task.status = AgentStatus.COMPLETED
                    task.end_time = time.time()
                    
                    logger.info(f"任务完成: {task.description}")
                    
                except Exception as e:
                    task.status = AgentStatus.FAILED
                    task.error_message = str(e)
                    task.end_time = time.time()
                    raise
            
            # 所有任务完成
            workflow.status = AgentStatus.COMPLETED
            workflow.end_time = time.time()
            workflow.result = {
                "tasks_completed": len(workflow.tasks),
                "total_time": workflow.end_time - workflow.start_time,
                "outputs": [t.output_data for t in workflow.tasks if t.output_data],
            }
            
            logger.info(f"工作流执行完成: {workflow.name}")
            return workflow.result
            
        except Exception as e:
            workflow.status = AgentStatus.FAILED
            workflow.error_message = str(e)
            workflow.end_time = time.time()
            logger.error(f"工作流执行失败: {e}")
            raise
    
    async def resume_workflow(self, workflow_id: str) -> Dict[str, Any]:
        """
        恢复工作流（断点续传）
        
        从失败的任务继续执行
        """
        if workflow_id not in self.executions:
            raise ValueError(f"工作流 {workflow_id} 不存在")
        
        workflow = self.executions[workflow_id]
        
        # 找到第一个未完成的任务
        for i, task in enumerate(workflow.tasks):
            if task.status in [AgentStatus.PENDING, AgentStatus.FAILED]:
                workflow.current_task_index = i
                logger.info(f"恢复工作流: {workflow.name}, 从任务 {i+1} 开始")
                return await self.execute_workflow(workflow_id)
        
        raise ValueError("工作流已完成，无需恢复")
    
    def get_workflow_status(self, workflow_id: str) -> Dict[str, Any]:
        """获取工作流状态"""
        if workflow_id not in self.executions:
            raise ValueError(f"工作流 {workflow_id} 不存在")
        
        workflow = self.executions[workflow_id]
        
        return {
            "workflow_id": workflow.workflow_id,
            "name": workflow.name,
            "status": workflow.status.value,
            "current_task": workflow.current_task_index,
            "total_tasks": len(workflow.tasks),
            "tasks": [
                {
                    "task_id": t.task_id,
                    "role": t.agent_role.value,
                    "description": t.description,
                    "status": t.status.value,
                    "error": t.error_message,
                }
                for t in workflow.tasks
            ],
            "result": workflow.result,
            "error": workflow.error_message,
        }


# 全局工作流引擎实例
_workflow_engine = None


def get_workflow_engine() -> WorkflowEngine:
    """获取全局工作流引擎实例"""
    global _workflow_engine
    if _workflow_engine is None:
        _workflow_engine = WorkflowEngine()
        
        # 注册默认Agent
        _workflow_engine.register_agent(PlannerAgent())
        _workflow_engine.register_agent(RetrieverAgent())
        _workflow_engine.register_agent(CoderAgent())
        
        logger.info("工作流引擎初始化完成")
    
    return _workflow_engine
