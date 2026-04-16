"""
第三方系统集成API
"""
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field
from typing import Optional, List, Dict

from app.services.third_party_integration import third_party_integration, IntegrationType

router = APIRouter()


class WecomMessageRequest(BaseModel):
    corp_id: str = Field(..., description="企业ID")
    agent_secret: str = Field(..., description="应用Secret")
    agent_id: int = Field(..., description="应用AgentId")
    user_list: List[str] = Field(..., description="接收用户列表")
    title: str = Field(..., description="消息标题")
    content: str = Field(..., description="消息内容")
    msg_type: Optional[str] = Field("textcard", description="消息类型")


class DingtalkMessageRequest(BaseModel):
    app_key: str = Field(..., description="应用Key")
    app_secret: str = Field(..., description="应用Secret")
    agent_id: str = Field(..., description="应用AgentId")
    user_ids: List[str] = Field(..., description="接收用户ID列表")
    title: str = Field(..., description="消息标题")
    content: str = Field(..., description="消息内容")


class WebhookRequest(BaseModel):
    url: str = Field(..., description="Webhook URL")
    payload: Dict = Field(..., description="请求体")
    secret: Optional[str] = Field("", description="签名密钥")
    method: Optional[str] = Field("POST", description="HTTP方法")
    headers: Optional[Dict[str, str]] = Field(None, description="自定义请求头")


class OAProcessRequest(BaseModel):
    oa_type: str = Field(..., description="OA类型: OA_FANWEI/OA_ZHIYUAN")
    config: Dict = Field(..., description="OA配置(api_url/token/workflow_id等)")
    process_data: Dict = Field(..., description="流程数据")


@router.post("/wecom/message")
async def send_wecom_message(req: WecomMessageRequest):
    """发送企业微信消息"""
    result = await third_party_integration.send_wecom_message(
        corp_id=req.corp_id,
        agent_secret=req.agent_secret,
        agent_id=req.agent_id,
        user_list=req.user_list,
        title=req.title,
        content=req.content,
        msg_type=req.msg_type,
    )
    return result


@router.post("/dingtalk/message")
async def send_dingtalk_message(req: DingtalkMessageRequest):
    """发送钉钉工作通知"""
    result = await third_party_integration.send_dingtalk_message(
        app_key=req.app_key,
        app_secret=req.app_secret,
        agent_id=req.agent_id,
        user_ids=req.user_ids,
        title=req.title,
        content=req.content,
    )
    return result


@router.post("/webhook")
async def send_webhook(req: WebhookRequest):
    """发送通用Webhook"""
    result = await third_party_integration.send_webhook(
        url=req.url,
        payload=req.payload,
        secret=req.secret,
        method=req.method,
        headers=req.headers,
    )
    return result


@router.post("/oa/process")
async def create_oa_process(req: OAProcessRequest):
    """创建OA流程"""
    try:
        oa_type = IntegrationType(req.oa_type)
    except ValueError:
        raise HTTPException(status_code=400, detail=f"不支持的OA类型: {req.oa_type}")

    result = await third_party_integration.create_oa_process(
        oa_type=oa_type,
        config=req.config,
        process_data=req.process_data,
    )
    return result


@router.get("/webhook/logs")
async def get_webhook_logs(limit: int = 50):
    """获取Webhook调用日志"""
    return {"logs": third_party_integration.get_webhook_logs(limit)}
