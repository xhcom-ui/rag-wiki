"""
智能工作流引擎 - 可视化编排

功能：
1. 工作流定义（DAG有向无环图）
2. 节点类型：LLM调用/条件分支/并行网关/人工审批/数据转换/API调用
3. 工作流执行引擎
4. 执行状态追踪与可视化
"""
import asyncio
import logging
import uuid
import time
from typing import List, Dict, Any, Optional, Callable
from dataclasses import dataclass, field
from enum import Enum

from app.services.llm_provider import llm_provider

logger = logging.getLogger(__name__)


class NodeType(Enum):
    START = "START"                     # 开始节点
    END = "END"                         # 结束节点
    LLM_CALL = "LLM_CALL"              # LLM调用
    CONDITION = "CONDITION"             # 条件分支
    PARALLEL_GATEWAY = "PARALLEL_GATEWAY"  # 并行网关
    MERGE_GATEWAY = "MERGE_GATEWAY"     # 合并网关
    HUMAN_APPROVAL = "HUMAN_APPROVAL"   # 人工审批
    DATA_TRANSFORM = "DATA_TRANSFORM"   # 数据转换
    API_CALL = "API_CALL"               # API调用
    KNOWLEDGE_SEARCH = "KNOWLEDGE_SEARCH"  # 知识检索
    CODE_EXECUTE = "CODE_EXECUTE"       # 代码执行


class NodeStatus(Enum):
    PENDING = "PENDING"
    RUNNING = "RUNNING"
    COMPLETED = "COMPLETED"
    FAILED = "FAILED"
    WAITING = "WAITING"   # 等待人工操作


@dataclass
class WorkflowNode:
    """工作流节点"""
    node_id: str
    node_type: NodeType
    name: str
    config: Dict[str, Any] = field(default_factory=dict)
    position: Dict[str, int] = field(default_factory=lambda: {"x": 0, "y": 0})
    next_nodes: List[str] = field(default_factory=list)     # 下一节点ID列表
    condition_expr: Optional[str] = None                      # 条件表达式（CONDITION类型）


@dataclass
class WorkflowDefinition:
    """工作流定义"""
    workflow_id: str
    name: str
    description: str
    nodes: Dict[str, WorkflowNode]          # nodeId -> node
    edges: List[Dict[str, str]] = field(default_factory=list)  # [{from, to, condition}]
    version: int = 1
    created_at: float = field(default_factory=time.time)


@dataclass
class WorkflowExecution:
    """工作流执行实例"""
    execution_id: str
    workflow_id: str
    status: str = "PENDING"
    current_nodes: List[str] = field(default_factory=list)
    node_results: Dict[str, Dict] = field(default_factory=dict)
    variables: Dict[str, Any] = field(default_factory=dict)
    started_at: Optional[float] = None
    completed_at: Optional[float] = None
    error: str = ""


# ==================== 预置工作流模板 ====================

WORKFLOW_TEMPLATES = {
    "rag_qa": {
        "name": "RAG智能问答",
        "description": "检索增强生成流程：问题理解→知识检索→重排序→LLM生成→质量校验",
        "nodes": [
            {"id": "start", "type": "START", "name": "开始"},
            {"id": "query_understand", "type": "LLM_CALL", "name": "问题理解",
             "config": {"prompt": "理解并改写用户问题，提取关键实体和意图", "temperature": 0.1}},
            {"id": "knowledge_search", "type": "KNOWLEDGE_SEARCH", "name": "知识检索",
             "config": {"top_k": 5, "score_threshold": 0.5}},
            {"id": "rerank", "type": "DATA_TRANSFORM", "name": "重排序",
             "config": {"method": "reranker"}},
            {"id": "llm_generate", "type": "LLM_CALL", "name": "答案生成",
             "config": {"prompt": "基于检索到的知识回答问题", "temperature": 0.3}},
            {"id": "quality_check", "type": "LLM_CALL", "name": "质量校验",
             "config": {"prompt": "检查答案是否忠实于上下文", "temperature": 0.1}},
            {"id": "condition", "type": "CONDITION", "name": "质量判断",
             "config": {"expression": "quality_score >= 0.7"}},
            {"id": "end", "type": "END", "name": "结束"},
        ],
        "edges": [
            {"from": "start", "to": "query_understand"},
            {"from": "query_understand", "to": "knowledge_search"},
            {"from": "knowledge_search", "to": "rerank"},
            {"from": "rerank", "to": "llm_generate"},
            {"from": "llm_generate", "to": "quality_check"},
            {"from": "quality_check", "to": "condition"},
            {"from": "condition", "to": "end", "condition": "pass"},
            {"from": "condition", "to": "knowledge_search", "condition": "fail"},
        ],
    },
    "document_review": {
        "name": "文档审批流程",
        "description": "文档提交→自动审核→人工审批→发布",
        "nodes": [
            {"id": "start", "type": "START", "name": "开始"},
            {"id": "auto_review", "type": "LLM_CALL", "name": "自动审核",
             "config": {"prompt": "审核文档内容是否合规", "temperature": 0.1}},
            {"id": "human_approval", "type": "HUMAN_APPROVAL", "name": "人工审批",
             "config": {"approvers": "admin", "timeout_hours": 24}},
            {"id": "end", "type": "END", "name": "结束"},
        ],
        "edges": [
            {"from": "start", "to": "auto_review"},
            {"from": "auto_review", "to": "human_approval"},
            {"from": "human_approval", "to": "end"},
        ],
    },
}


class WorkflowEngine:
    """智能工作流引擎"""

    def __init__(self):
        self._workflows: Dict[str, WorkflowDefinition] = {}
        self._executions: Dict[str, WorkflowExecution] = {}

    def create_workflow(self, definition: Dict[str, Any]) -> Dict[str, Any]:
        """创建工作流"""
        workflow_id = f"wf_{uuid.uuid4().hex[:8]}"

        nodes = {}
        for node_data in definition.get("nodes", []):
            node = WorkflowNode(
                node_id=node_data["id"],
                node_type=NodeType(node_data["type"]),
                name=node_data.get("name", node_data["id"]),
                config=node_data.get("config", {}),
                position=node_data.get("position", {"x": 0, "y": 0}),
            )
            nodes[node.node_id] = node

        # 构建边
        edges = definition.get("edges", [])
        for edge in edges:
            from_node = edge.get("from")
            to_node = edge.get("to")
            if from_node in nodes:
                nodes[from_node].next_nodes.append(to_node)

        workflow = WorkflowDefinition(
            workflow_id=workflow_id,
            name=definition.get("name", "未命名工作流"),
            description=definition.get("description", ""),
            nodes=nodes,
            edges=edges,
        )
        self._workflows[workflow_id] = workflow

        logger.info(f"工作流创建: {workflow_id}, name={workflow.name}")
        return {
            "workflow_id": workflow_id,
            "name": workflow.name,
            "node_count": len(nodes),
            "edge_count": len(edges),
        }

    async def execute_workflow(self, workflow_id: str, input_data: Dict[str, Any]) -> Dict[str, Any]:
        """执行工作流"""
        workflow = self._workflows.get(workflow_id)
        if not workflow:
            return {"error": f"工作流不存在: {workflow_id}"}

        execution = WorkflowExecution(
            execution_id=f"exec_{uuid.uuid4().hex[:8]}",
            workflow_id=workflow_id,
            variables={"input": input_data},
        )
        self._executions[execution.execution_id] = execution
        execution.started_at = time.time()
        execution.status = "RUNNING"

        try:
            # 找到开始节点
            start_node = self._find_start_node(workflow)
            if not start_node:
                raise ValueError("工作流无开始节点")

            execution.current_nodes = [start_node.node_id]
            await self._execute_from_node(workflow, execution, start_node, input_data)

            execution.status = "COMPLETED"
            execution.completed_at = time.time()

            logger.info(f"工作流执行完成: {execution.execution_id}")
            return {
                "execution_id": execution.execution_id,
                "status": "COMPLETED",
                "results": execution.node_results,
                "variables": execution.variables,
                "elapsed": round(execution.completed_at - execution.started_at, 2),
            }

        except Exception as e:
            execution.status = "FAILED"
            execution.error = str(e)
            logger.error(f"工作流执行失败: {e}")
            return {"execution_id": execution.execution_id, "status": "FAILED", "error": str(e)}

    async def _execute_from_node(
        self,
        workflow: WorkflowDefinition,
        execution: WorkflowExecution,
        node: WorkflowNode,
        input_data: Any,
    ):
        """从指定节点开始执行"""
        result = await self._execute_node(node, input_data, execution)
        execution.node_results[node.node_id] = result

        # 根据节点类型决定下一步
        if node.node_type == NodeType.END:
            return

        if node.node_type == NodeType.CONDITION:
            # 条件分支
            condition_result = result.get("result", "")
            for edge in workflow.edges:
                if edge["from"] == node.node_id:
                    next_node = workflow.nodes.get(edge["to"])
                    if next_node:
                        edge_condition = edge.get("condition", "")
                        if edge_condition == "pass" and "通过" in condition_result:
                            await self._execute_from_node(workflow, execution, next_node, result)
                            return
                        elif edge_condition == "fail" and "不通过" in condition_result:
                            await self._execute_from_node(workflow, execution, next_node, result)
                            return
            # 默认走第一条边
            if node.next_nodes:
                next_node = workflow.nodes.get(node.next_nodes[0])
                if next_node:
                    await self._execute_from_node(workflow, execution, next_node, result)
        elif node.node_type == NodeType.PARALLEL_GATEWAY:
            # 并行执行
            tasks = []
            for next_id in node.next_nodes:
                next_node = workflow.nodes.get(next_id)
                if next_node:
                    tasks.append(self._execute_from_node(workflow, execution, next_node, input_data))
            await asyncio.gather(*tasks)
        else:
            # 顺序执行
            for next_id in node.next_nodes:
                next_node = workflow.nodes.get(next_id)
                if next_node:
                    await self._execute_from_node(workflow, execution, next_node, result)

    async def _execute_node(self, node: WorkflowNode, input_data: Any, execution: WorkflowExecution) -> Dict:
        """执行单个节点"""
        node_status_key = f"node_status_{node.node_id}"

        try:
            if node.node_type == NodeType.START:
                return {"status": "COMPLETED", "result": input_data}

            elif node.node_type == NodeType.END:
                return {"status": "COMPLETED", "result": input_data}

            elif node.node_type == NodeType.LLM_CALL:
                prompt = node.config.get("prompt", "")
                temperature = node.config.get("temperature", 0.3)
                messages = [
                    {"role": "system", "content": prompt},
                    {"role": "user", "content": str(input_data)[:2000]},
                ]
                response = await llm_provider.chat(messages=messages, temperature=temperature, max_tokens=1500)
                return {"status": "COMPLETED", "result": response.get("content", "")}

            elif node.node_type == NodeType.KNOWLEDGE_SEARCH:
                from app.services.embedding import embedding_service
                from app.services.vector_db import vector_db
                query = str(input_data)[:500]
                query_embedding = await embedding_service.get_embedding(query)
                if query_embedding:
                    results = await vector_db.search("rag_wiki", query_embedding, top_k=node.config.get("top_k", 5))
                    return {"status": "COMPLETED", "result": results}
                return {"status": "COMPLETED", "result": []}

            elif node.node_type == NodeType.DATA_TRANSFORM:
                return {"status": "COMPLETED", "result": input_data}

            elif node.node_type == NodeType.HUMAN_APPROVAL:
                execution.status = "WAITING"
                return {"status": "WAITING", "message": "等待人工审批", "approvers": node.config.get("approvers", "")}

            elif node.node_type == NodeType.API_CALL:
                url = node.config.get("url", "")
                method = node.config.get("method", "GET")
                return {"status": "COMPLETED", "result": f"API {method} {url} called"}

            else:
                return {"status": "COMPLETED", "result": input_data}

        except Exception as e:
            logger.error(f"节点执行失败: {node.node_id}, error={e}")
            return {"status": "FAILED", "error": str(e)}

    def _find_start_node(self, workflow: WorkflowDefinition) -> Optional[WorkflowNode]:
        for node in workflow.nodes.values():
            if node.node_type == NodeType.START:
                return node
        return None

    def get_workflow(self, workflow_id: str) -> Optional[Dict]:
        wf = self._workflows.get(workflow_id)
        if not wf: return None
        return {
            "workflow_id": wf.workflow_id,
            "name": wf.name,
            "description": wf.description,
            "nodes": [{"id": n.node_id, "type": n.node_type.value, "name": n.name,
                        "position": n.position, "config": n.config}
                       for n in wf.nodes.values()],
            "edges": wf.edges,
        }

    def get_execution(self, execution_id: str) -> Optional[Dict]:
        ex = self._executions.get(execution_id)
        if not ex: return None
        return {
            "execution_id": ex.execution_id,
            "workflow_id": ex.workflow_id,
            "status": ex.status,
            "node_results": ex.node_results,
            "current_nodes": ex.current_nodes,
        }

    def get_templates(self) -> List[Dict]:
        return [{"id": k, **v} for k, v in WORKFLOW_TEMPLATES.items()]


# 全局实例
workflow_engine = WorkflowEngine()
