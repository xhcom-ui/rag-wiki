"""
领域模型微调API
"""
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field
from typing import Optional, List, Dict

from app.services.domain_model_tuning import domain_tuning_service, TuningMethod

router = APIRouter()


class BuildDatasetRequest(BaseModel):
    name: str = Field(..., description="数据集名称")
    space_ids: List[str] = Field(..., description="知识库空间ID列表")
    instruction_template: Optional[str] = Field("基于以下知识回答问题", description="指令模板")
    max_samples: Optional[int] = Field(500, description="最大样本数")


class CreateTuningJobRequest(BaseModel):
    name: str = Field(..., description="任务名称")
    base_model: str = Field(..., description="基础模型名称")
    method: str = Field("LORA", description="微调方法: FULL/LORA/QLORA/P_TUNING/PREFIX")
    dataset_id: str = Field(..., description="数据集ID")
    hyperparams: Optional[Dict] = Field(None, description="超参数配置")


@router.post("/dataset/build")
async def build_dataset(req: BuildDatasetRequest):
    """从知识库构建微调数据集"""
    result = await domain_tuning_service.build_dataset_from_knowledge(
        name=req.name,
        space_ids=req.space_ids,
        instruction_template=req.instruction_template,
        max_samples=req.max_samples,
    )
    if "error" in result:
        raise HTTPException(status_code=400, detail=result["error"])
    return result


@router.post("/job")
async def create_tuning_job(req: CreateTuningJobRequest):
    """创建微调任务"""
    try:
        method = TuningMethod(req.method)
    except ValueError:
        raise HTTPException(status_code=400, detail=f"不支持的微调方法: {req.method}")

    result = await domain_tuning_service.create_tuning_job(
        name=req.name,
        base_model=req.base_model,
        method=method,
        dataset_id=req.dataset_id,
        hyperparams=req.hyperparams,
    )
    if "error" in result:
        raise HTTPException(status_code=400, detail=result["error"])
    return result


@router.post("/job/{job_id}/start")
async def start_tuning(job_id: str):
    """启动微调任务"""
    result = await domain_tuning_service.start_tuning(job_id)
    if "error" in result:
        raise HTTPException(status_code=400, detail=result["error"])
    return result


@router.get("/job/{job_id}")
async def get_job_status(job_id: str):
    """获取微调任务状态"""
    result = domain_tuning_service.get_job_status(job_id)
    if not result:
        raise HTTPException(status_code=404, detail="任务不存在")
    return result


@router.get("/jobs")
async def list_jobs():
    """列出所有微调任务"""
    return {"jobs": domain_tuning_service.list_jobs()}
