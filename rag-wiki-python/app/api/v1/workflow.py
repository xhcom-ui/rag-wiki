"""
智能工作流引擎API
"""
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field
from typing import Optional, List, Dict

from app.services.workflow_engine import workflow_engine

router = APIRouter()


class CreateWorkflowRequest(BaseModel):
    name: str = Field(..., description="工作流名称")
    description: Optional[str] = Field("", description="工作流描述")
    nodes: List[Dict] = Field(..., description="节点列表")
    edges: List[Dict] = Field(default_factory=list, description="边列表")


class ExecuteWorkflowRequest(BaseModel):
    input_data: Dict = Field(default_factory=dict, description="输入数据")


@router.get("/templates")
async def get_templates():
    """获取工作流模板"""
    return {"templates": workflow_engine.get_templates()}


@router.post("/create")
async def create_workflow(req: CreateWorkflowRequest):
    """创建工作流"""
    definition = {
        "name": req.name,
        "description": req.description,
        "nodes": req.nodes,
        "edges": req.edges,
    }
    result = workflow_engine.create_workflow(definition)
    return result


@router.get("/{workflow_id}")
async def get_workflow(workflow_id: str):
    """获取工作流定义"""
    result = workflow_engine.get_workflow(workflow_id)
    if not result:
        raise HTTPException(status_code=404, detail="工作流不存在")
    return result


@router.post("/{workflow_id}/execute")
async def execute_workflow(workflow_id: str, req: ExecuteWorkflowRequest):
    """执行工作流"""
    result = await workflow_engine.execute_workflow(workflow_id, req.input_data)
    if "error" in result:
        raise HTTPException(status_code=400, detail=result["error"])
    return result


@router.get("/execution/{execution_id}")
async def get_execution(execution_id: str):
    """获取执行状态"""
    result = workflow_engine.get_execution(execution_id)
    if not result:
        raise HTTPException(status_code=404, detail="执行实例不存在")
    return result
