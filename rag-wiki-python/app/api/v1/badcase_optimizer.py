"""
Badcase自动化优化闭环API
"""
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field
from typing import Optional, List, Dict

from app.services.badcase_optimizer import badcase_optimizer, OptimizationType

router = APIRouter()


class ExperimentCreateRequest(BaseModel):
    name: str = Field(..., description="实验名称")
    optimization_type: str = Field(..., description="优化类型: PROMPT/CHUNK_SIZE/TOP_K/SCORE_THRESHOLD/RERANKER_WEIGHT/EMBEDDING_MODEL")
    control_config: Dict = Field(..., description="对照组配置")
    experiment_config: Dict = Field(..., description="实验组配置")
    test_cases: List[Dict] = Field(..., description="测试用例列表")


class BadcaseAttributeRequest(BaseModel):
    question: str = Field(..., description="原始问题")
    bad_answer: str = Field(..., description="错误答案")
    expected_answer: str = Field("", description="期望答案")
    context: str = Field("", description="检索上下文")


class OptimizationLoopRequest(BaseModel):
    question: str = Field(..., description="原始问题")
    bad_answer: str = Field(..., description="错误答案")
    expected_answer: str = Field("", description="期望答案")
    context: str = Field("", description="检索上下文")
    max_iterations: Optional[int] = Field(3, description="最大优化迭代次数")


@router.post("/experiment")
async def create_experiment(req: ExperimentCreateRequest):
    """创建A/B测试实验"""
    try:
        opt_type = OptimizationType(req.optimization_type)
    except ValueError:
        raise HTTPException(status_code=400, detail=f"不支持的优化类型: {req.optimization_type}")

    result = await badcase_optimizer.create_experiment(
        name=req.name,
        optimization_type=opt_type,
        control_config=req.control_config,
        experiment_config=req.experiment_config,
        test_cases=req.test_cases,
    )
    return result


@router.post("/experiment/{experiment_id}/run")
async def run_experiment(experiment_id: str):
    """运行A/B测试实验"""
    result = await badcase_optimizer.run_experiment(experiment_id)
    if "error" in result:
        raise HTTPException(status_code=400, detail=result["error"])
    return result


@router.get("/experiment/{experiment_id}")
async def get_experiment(experiment_id: str):
    """获取实验详情"""
    result = badcase_optimizer.get_experiment(experiment_id)
    if not result:
        raise HTTPException(status_code=404, detail="实验不存在")
    return result


@router.post("/attribute")
async def attribute_badcase(req: BadcaseAttributeRequest):
    """自动归因分析Badcase根因"""
    result = await badcase_optimizer.auto_attribute_badcase(req.dict())
    return result


@router.post("/optimization-loop")
async def optimization_loop(req: OptimizationLoopRequest):
    """自动优化闭环"""
    result = await badcase_optimizer.optimization_loop(req.dict(), max_iterations=req.max_iterations)
    return result
