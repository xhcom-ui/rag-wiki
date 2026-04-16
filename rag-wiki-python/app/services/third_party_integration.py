"""
第三方系统集成服务

支持：
1. 企业微信消息推送/事件回调
2. 钉钉消息推送/审批集成
3. 通用Webhook集成
4. OA系统对接（泛微/致远等）
"""
import asyncio
import hashlib
import hmac
import json
import logging
import time
import uuid
from typing import List, Dict, Any, Optional
from enum import Enum

logger = logging.getLogger(__name__)


class IntegrationType(Enum):
    WECOM = "WECOM"
    DINGTALK = "DINGTALK"
    WEBHOOK = "WEBHOOK"
    OA_FANWEI = "OA_FANWEI"
    OA_ZHIYUAN = "OA_ZHIYUAN"


class ThirdPartyIntegrationService:
    """第三方系统集成服务"""

    def __init__(self):
        self._configs: Dict[str, Dict] = {}
        self._webhook_logs: List[Dict] = []

    # ==================== 企业微信 ====================

    async def send_wecom_message(
        self,
        corp_id: str,
        agent_secret: str,
        agent_id: int,
        user_list: List[str],
        title: str,
        content: str,
        msg_type: str = "textcard",
    ) -> Dict[str, Any]:
        """发送企业微信消息"""
        try:
            import aiohttp
            # 获取access_token
            token_url = f"https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid={corp_id}&corpsecret={agent_secret}"
            async with aiohttp.ClientSession() as session:
                async with session.get(token_url) as resp:
                    token_data = await resp.json()
                    access_token = token_data.get("access_token")

                if not access_token:
                    return {"success": False, "error": "获取access_token失败"}

                # 发送消息
                send_url = f"https://qyapi.weixin.qq.com/cgi-bin/message/send?access_token={access_token}"
                payload = {
                    "touser": "|".join(user_list),
                    "msgtype": msg_type,
                    "agentid": agent_id,
                    msg_type: {
                        "title": title,
                        "description": content[:512],
                        "url": "",
                    },
                }
                async with session.post(send_url, json=payload) as resp:
                    result = await resp.json()

                return {"success": result.get("errcode") == 0, "data": result}

        except ImportError:
            logger.warning("aiohttp未安装，无法发送企业微信消息")
            return {"success": False, "error": "aiohttp未安装"}
        except Exception as e:
            logger.error(f"企业微信消息发送失败: {e}")
            return {"success": False, "error": str(e)}

    # ==================== 钉钉 ====================

    async def send_dingtalk_message(
        self,
        app_key: str,
        app_secret: str,
        agent_id: str,
        user_ids: List[str],
        title: str,
        content: str,
    ) -> Dict[str, Any]:
        """发送钉钉工作通知"""
        try:
            import aiohttp
            token_url = "https://api.dingtalk.com/v1.0/oauth2/accessToken"
            token_body = {"appKey": app_key, "appSecret": app_secret}

            async with aiohttp.ClientSession() as session:
                async with session.post(token_url, json=token_body) as resp:
                    token_data = await resp.json()
                    access_token = token_data.get("accessToken")

                if not access_token:
                    return {"success": False, "error": "获取access_token失败"}

                # 发送工作通知
                send_url = "https://oapi.dingtalk.com/topapi/message/corpconversation/asyncsend_v2"
                params = {"access_token": access_token}
                payload = {
                    "agent_id": agent_id,
                    "userid_list": user_ids,
                    "msg": {
                        "msgtype": "oa",
                        "oa": {
                            "head": {"text": title, "bgcolor": "FFBBBBBB"},
                            "body": {"title": title, "content": content[:500]},
                        },
                    },
                }
                async with session.post(send_url, params=params, json=payload) as resp:
                    result = await resp.json()

                return {"success": result.get("errcode") == 0, "data": result}

        except ImportError:
            return {"success": False, "error": "aiohttp未安装"}
        except Exception as e:
            logger.error(f"钉钉消息发送失败: {e}")
            return {"success": False, "error": str(e)}

    # ==================== 通用Webhook ====================

    async def send_webhook(
        self,
        url: str,
        payload: Dict[str, Any],
        secret: str = "",
        method: str = "POST",
        headers: Dict[str, str] = None,
    ) -> Dict[str, Any]:
        """发送通用Webhook"""
        try:
            import aiohttp

            req_headers = {"Content-Type": "application/json"}
            if headers:
                req_headers.update(headers)

            # 如果配置了签名密钥，生成签名
            if secret:
                timestamp = str(int(time.time()))
                sign = self._generate_webhook_signature(timestamp, secret)
                req_headers["X-Timestamp"] = timestamp
                req_headers["X-Signature"] = sign

            async with aiohttp.ClientSession() as session:
                if method.upper() == "POST":
                    async with session.post(url, json=payload, headers=req_headers) as resp:
                        status = resp.status
                        body = await resp.text()
                else:
                    async with session.get(url, headers=req_headers) as resp:
                        status = resp.status
                        body = await resp.text()

            log_entry = {
                "webhook_id": str(uuid.uuid4())[:8],
                "url": url,
                "method": method,
                "status": status,
                "timestamp": time.time(),
            }
            self._webhook_logs.append(log_entry)

            return {"success": 200 <= status < 300, "status": status, "body": body[:500]}

        except ImportError:
            return {"success": False, "error": "aiohttp未安装"}
        except Exception as e:
            logger.error(f"Webhook发送失败: {e}")
            return {"success": False, "error": str(e)}

    # ==================== OA系统 ====================

    async def create_oa_process(
        self,
        oa_type: IntegrationType,
        config: Dict[str, Any],
        process_data: Dict[str, Any],
    ) -> Dict[str, Any]:
        """创建OA流程（泛微/致远）"""
        if oa_type == IntegrationType.OA_FANWEI:
            return await self._create_fanwei_process(config, process_data)
        elif oa_type == IntegrationType.OA_ZHIYUAN:
            return await self._create_zhiyuan_process(config, process_data)
        else:
            return {"success": False, "error": f"不支持的OA类型: {oa_type}"}

    async def _create_fanwei_process(self, config: Dict, data: Dict) -> Dict[str, Any]:
        """创建泛微OA流程"""
        try:
            import aiohttp
            url = config.get("api_url", "")
            token = config.get("token", "")
            workflow_id = config.get("workflow_id", "")

            payload = {
                "workflowId": workflow_id,
                "requestName": data.get("title", "RagWiki审批"),
                "creatorId": data.get("creator_id", ""),
                "mainData": data.get("form_data", {}),
            }
            headers = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}

            async with aiohttp.ClientSession() as session:
                async with session.post(url, json=payload, headers=headers) as resp:
                    result = await resp.json()

            return {"success": True, "data": result}
        except Exception as e:
            return {"success": False, "error": str(e)}

    async def _create_zhiyuan_process(self, config: Dict, data: Dict) -> Dict[str, Any]:
        """创建致远OA流程"""
        try:
            import aiohttp
            url = config.get("api_url", "")
            token = config.get("token", "")
            template_id = config.get("template_id", "")

            payload = {
                "templateId": template_id,
                "subject": data.get("title", "RagWiki审批"),
                "sender": data.get("creator_id", ""),
                "formData": data.get("form_data", {}),
            }
            headers = {"Authorization": token, "Content-Type": "application/json"}

            async with aiohttp.ClientSession() as session:
                async with session.post(url, json=payload, headers=headers) as resp:
                    result = await resp.json()

            return {"success": True, "data": result}
        except Exception as e:
            return {"success": False, "error": str(e)}

    def _generate_webhook_signature(self, timestamp: str, secret: str) -> str:
        """生成Webhook签名"""
        string_to_sign = f"{timestamp}\n{secret}"
        hmac_code = hmac.new(string_to_sign.encode("utf-8"), digestmod=hashlib.sha256).digest()
        import base64
        return base64.b64encode(hmac_code).decode("utf-8")

    def get_webhook_logs(self, limit: int = 50) -> List[Dict]:
        return self._webhook_logs[-limit:]


# 全局实例
third_party_integration = ThirdPartyIntegrationService()
