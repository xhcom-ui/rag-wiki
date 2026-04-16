"""
内容创作辅助API

基于知识库的内容创作、大纲生成、润色、扩写、摘要等
"""
from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field
from typing import Optional, List

from app.services.llm_provider import llm_provider
from app.services.vector_db import vector_db
from app.services.embedding import embedding_service

router = APIRouter(prefix="/content", tags=["内容创作辅助"])


class OutlineRequest(BaseModel):
    topic: str = Field(..., description="文章主题")
    space_id: Optional[str] = Field(None, description="知识库空间ID")
    style: Optional[str] = Field("professional", description="风格: professional/casual/academic/creative")
    sections: Optional[int] = Field(5, description="大纲章节数")


class PolishRequest(BaseModel):
    content: str = Field(..., description="待润色内容")
    style: Optional[str] = Field("professional", description="润色风格")
    keep_structure: Optional[bool] = Field(True, description="保持原有结构")


class ExpandRequest(BaseModel):
    content: str = Field(..., description="待扩写内容")
    direction: Optional[str] = Field("depth", description="扩写方向: depth(深入)/breadth(广度)/example(举例)")
    target_length: Optional[int] = Field(None, description="目标字数")


class SummarizeRequest(BaseModel):
    content: str = Field(..., description="待摘要内容")
    max_length: Optional[int] = Field(200, description="摘要最大字数")
    style: Optional[str] = Field("concise", description="风格: concise/detailed/bullet")


class GenerateFromKnowledgeRequest(BaseModel):
    topic: str = Field(..., description="创作主题")
    space_ids: List[str] = Field(..., description="参考知识库空间IDs")
    template: Optional[str] = Field(None, description="创作模板: report/article/tutorial/memo")


# ==================== 大纲生成 ====================

@router.post("/outline")
async def generate_outline(req: OutlineRequest):
    """基于主题生成文章大纲"""
    # 如果指定了知识库空间，先检索相关知识
    reference = ""
    if req.space_id:
        reference = await _search_knowledge(req.topic, req.space_id)

    prompt = f"""请为以下主题生成一个详细的文章大纲。

主题: {req.topic}
风格: {req.style}
章节数: {req.sections}
{"参考资料: " + reference if reference else ""}

请以Markdown格式输出大纲，包含一级和二级标题。每个章节简要说明要涵盖的内容要点。"""

    response = await llm_provider.chat(
        messages=[{"role": "user", "content": prompt}],
        temperature=0.7,
        max_tokens=1000,
    )

    return {"outline": response.get("content", ""), "topic": req.topic, "style": req.style}


# ==================== 内容润色 ====================

@router.post("/polish")
async def polish_content(req: PolishRequest):
    """润色内容"""
    prompt = f"""请润色以下内容，使其更加{req.style}。

原始内容:
{req.content}

要求:
1. 保持原文的核心含义不变
2. {"保持原有段落结构" if req.keep_structure else "可以优化段落结构"}
3. 改善语言表达，使其更加流畅、准确
4. 修正语法和用词问题

请直接输出润色后的内容，不需要解释修改点。"""

    response = await llm_provider.chat(
        messages=[{"role": "user", "content": prompt}],
        temperature=0.5,
        max_tokens=2000,
    )

    return {"polished": response.get("content", ""), "style": req.style}


# ==================== 内容扩写 ====================

@router.post("/expand")
async def expand_content(req: ExpandRequest):
    """扩写内容"""
    direction_desc = {
        "depth": "深入展开，增加细节和深度分析",
        "breadth": "横向扩展，增加相关话题和视角",
        "example": "增加具体案例、数据和实例说明",
    }

    prompt = f"""请扩写以下内容，{direction_desc.get(req.direction, "深入展开")}。

原始内容:
{req.content}

{"目标字数: 约" + str(req.target_length) + "字" if req.target_length else "适当扩展篇幅"}

请保持与原文风格一致，扩写后的内容应自然流畅。"""

    response = await llm_provider.chat(
        messages=[{"role": "user", "content": prompt}],
        temperature=0.6,
        max_tokens=2000,
    )

    return {"expanded": response.get("content", ""), "direction": req.direction}


# ==================== 内容摘要 ====================

@router.post("/summarize")
async def summarize_content(req: SummarizeRequest):
    """生成内容摘要"""
    style_desc = {
        "concise": "简洁精炼，突出核心要点",
        "detailed": "保留主要论据和关键细节",
        "bullet": "以要点列表形式呈现",
    }

    prompt = f"""请为以下内容生成摘要。

内容:
{req.content}

要求:
1. 摘要风格: {style_desc.get(req.style, "简洁精炼")}
2. 最大字数: {req.max_length}字
3. 保留核心观点和关键信息

请直接输出摘要内容。"""

    response = await llm_provider.chat(
        messages=[{"role": "user", "content": prompt}],
        temperature=0.3,
        max_tokens=500,
    )

    return {"summary": response.get("content", ""), "style": req.style}


# ==================== 基于知识库创作 ====================

@router.post("/generate")
async def generate_from_knowledge(req: GenerateFromKnowledgeRequest):
    """基于知识库内容生成文章"""
    # 检索相关知识
    all_references = []
    for space_id in req.space_ids[:3]:  # 最多3个空间
        ref = await _search_knowledge(req.topic, space_id, top_k=5)
        if ref:
            all_references.append(ref)

    reference_text = "\n\n---\n\n".join(all_references) if all_references else "无参考知识库内容"

    template_desc = {
        "report": "报告格式（含背景、分析、结论、建议）",
        "article": "文章格式（含引言、正文、总结）",
        "tutorial": "教程格式（含概述、步骤、注意事项）",
        "memo": "备忘录格式（含主题、要点、行动项）",
    }

    prompt = f"""请基于以下知识库参考资料，围绕主题撰写一篇文章。

主题: {req.topic}
格式: {template_desc.get(req.template, "自由格式") if req.template else "自由格式"}

参考资料:
{reference_text}

要求:
1. 内容应基于参考资料中的事实和信息
2. 如果参考资料不足以支撑，请明确标注[需补充]
3. 保持专业、准确的写作风格
4. 适当引用参考资料中的关键数据和观点"""

    response = await llm_provider.chat(
        messages=[{"role": "user", "content": prompt}],
        temperature=0.6,
        max_tokens=3000,
    )

    return {
        "content": response.get("content", ""),
        "topic": req.topic,
        "template": req.template,
        "reference_spaces": req.space_ids,
    }


# ==================== 辅助方法 ====================

async def _search_knowledge(query: str, space_id: str, top_k: int = 3) -> str:
    """搜索知识库获取参考资料"""
    try:
        query_embedding = await embedding_service.get_embedding(query)
        if not query_embedding:
            return ""

        results = await vector_db.search(
            collection_name="rag_wiki",
            query_vector=query_embedding,
            top_k=top_k,
            filter_expr=f'space_id == "{space_id}"' if space_id else None,
        )

        if not results:
            return ""

        return "\n".join([r.get("content", "") for r in results if r.get("content")])

    except Exception as e:
        return ""
