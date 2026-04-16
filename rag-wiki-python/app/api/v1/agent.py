"""
Agent编排服务API - 完整实现
"""
import logging
from typing import Optional, List
from fastapi import APIRouter, Request
from pydantic import BaseModel
from app.core.response import Result, BusinessException
from app.services.agent import agent_orchestrator

logger = logging.getLogger(__name__)
router = APIRouter()


class AgentTaskRequest(BaseModel):
    task_description: str
    session_id: Optional[str] = None
    space_id: Optional[str] = None
    tools: List[str] = ["knowledge_search", "code_execute"]
    max_steps: int = 10


@router.post("/submit", summary="提交Agent任务")
async def submit_agent_task(request: AgentTaskRequest, req: Request):
    """
    提交多步骤复杂任务给Agent处理
    支持：跨文档对比分析、行业报告撰写、数据汇总研究
    流程: 规划Agent分解 → 检索Agent获取知识 → 编码Agent执行 → 审计Agent校验
    """
    user_id = getattr(req.state, "user_id", None) or "anonymous"
    try:
        result = await agent_orchestrator.submit_task(
            task_description=request.task_description,
            user_id=user_id,
            space_id=request.space_id,
            tools=request.tools,
            max_steps=request.max_steps,
        )
        return Result.success(data=result)
    except Exception as e:
        logger.error(f"Agent任务失败: {e}")
        raise BusinessException(code=5008, message=f"Agent任务执行失败: {str(e)}")


@router.get("/task/{task_id}", summary="查询Agent任务状态")
async def get_agent_task_status(task_id: str):
    """查询Agent任务执行状态和中间结果"""
    result = agent_orchestrator.get_task_status(task_id)
    if not result:
        raise BusinessException(code=4004, message="任务不存在")
    return Result.success(data=result)


@router.post("/task/{task_id}/cancel", summary="取消Agent任务")
async def cancel_agent_task(task_id: str):
    """取消正在执行的Agent任务"""
    success = agent_orchestrator.cancel_task(task_id)
    if not success:
        raise BusinessException(code=4005, message="任务无法取消")
    return Result.success()


@router.get("/tools", summary="获取可用工具列表")
async def list_available_tools():
    """获取Agent可调用的工具列表"""
    tools = [
        {"name": "knowledge_search", "description": "知识库检索 - 从企业知识库中检索相关信息", "enabled": True, "agent": "retriever"},
        {"name": "code_execute", "description": "代码执行（沙箱）- 在安全沙箱中运行Python代码", "enabled": True, "agent": "coder"},
        {"name": "data_analysis", "description": "数据分析 - 生成数据分析代码并执行", "enabled": True, "agent": "coder"},
        {"name": "report_generation", "description": "报告生成 - 基于检索结果生成分析报告", "enabled": True, "agent": "coder"},
        {"name": "security_audit", "description": "安全审计 - 检查结果的安全性和合规性", "enabled": True, "agent": "auditor"},
        {"name": "web_search", "description": "网络搜索 - 从互联网获取信息", "enabled": False, "agent": "retriever"},
        {"name": "api_call", "description": "API调用 - 调用外部API获取数据", "enabled": False, "agent": "coder"},
    ]
    return Result.success(data=tools)
