"""
多Agent协作编排API
"""
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field
from typing import Optional, List, Dict

from app.services.enterprise_agent_orchestrator import enterprise_orchestrator, CollaborationPattern

router = APIRouter()


class ScenarioExecuteRequest(BaseModel):
    scenario_id: str = Field(..., description="场景模板ID: data_analysis/code_review/document_generation/knowledge_qa")
    task: str = Field(..., description="任务描述")
    context: Optional[Dict] = Field(None, description="额外上下文")
    max_rounds: Optional[int] = Field(3, description="最大执行轮次(辩论模式)")


@router.get("/scenarios")
async def list_scenarios():
    """获取可用场景模板"""
    scenarios = enterprise_orchestrator.get_scenarios()
    return {"scenarios": scenarios}


@router.post("/execute")
async def execute_scenario(req: ScenarioExecuteRequest):
    """执行预置场景"""
    result = await enterprise_orchestrator.execute_scenario(
        scenario_id=req.scenario_id,
        task=req.task,
        context=req.context,
        max_rounds=req.max_rounds,
    )
    if "error" in result:
        raise HTTPException(status_code=400, detail=result["error"])
    return result


@router.get("/session/{session_id}")
async def get_session(session_id: str):
    """获取执行会话状态"""
    session = enterprise_orchestrator.get_session(session_id)
    if not session:
        raise HTTPException(status_code=404, detail="会话不存在")
    return session
