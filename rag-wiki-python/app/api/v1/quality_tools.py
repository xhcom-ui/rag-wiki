"""
答案忠实度校验 & 记忆冲突检测 API
"""
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field
from typing import Optional, List, Dict

from app.services.faithfulness_validator import faithfulness_validator
from app.services.memory_conflict_detector import memory_conflict_detector

router = APIRouter()


# ==================== 忠实度校验 ====================

class FaithfulnessValidateRequest(BaseModel):
    answer: str = Field(..., description="生成的答案")
    context: str = Field(..., description="检索到的上下文")
    detailed: Optional[bool] = Field(False, description="是否返回详细声明分析")


class QuickValidateRequest(BaseModel):
    answer: str = Field(..., description="生成的答案")
    context: str = Field(..., description="检索到的上下文")


@router.post("/faithfulness/validate")
async def validate_faithfulness(req: FaithfulnessValidateRequest):
    """完整忠实度校验"""
    result = await faithfulness_validator.validate(req.answer, req.context)
    return result


@router.post("/faithfulness/quick")
async def quick_validate(req: QuickValidateRequest):
    """快速忠实度校验（仅返回评分）"""
    result = await faithfulness_validator.quick_validate(req.answer, req.context)
    return result


# ==================== 记忆冲突检测 ====================

class ConflictDetectRequest(BaseModel):
    space_id: Optional[str] = Field(None, description="知识库空间ID")
    memory_ids: Optional[List[str]] = Field(None, description="指定记忆ID列表(不指定则检测全部)")


@router.post("/memory/conflict-detect")
async def detect_conflicts(req: ConflictDetectRequest):
    """检测记忆冲突（语义去重+矛盾检测）"""
    # 使用默认user_id，实际应从请求上下文中获取
    user_id = req.memory_ids[0] if req.memory_ids else "default"
    result = await memory_conflict_detector.full_conflict_analysis(
        user_id=user_id,
        space_id=req.space_id,
    )
    return result


@router.post("/memory/semantic-dedup")
async def semantic_dedup(req: ConflictDetectRequest):
    """语义去重"""
    user_id = req.memory_ids[0] if req.memory_ids else "default"
    result = await memory_conflict_detector.detect_semantic_duplicates(
        user_id=user_id,
        space_id=req.space_id,
    )
    return {"duplicate_groups": result}
