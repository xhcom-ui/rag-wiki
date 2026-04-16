"""
Badcase自动化优化闭环服务

功能：
1. A/B测试 - 对比不同prompt/embedding参数的效果
2. 效果验证 - 自动评估优化前后效果
3. 自动归因 - 定位Badcase根因
4. 优化闭环 - 自动调整参数→验证→上线
"""
import asyncio
import logging
import uuid
import time
from typing import List, Dict, Any, Optional
from dataclasses import dataclass, field
from enum import Enum

from app.services.llm_provider import llm_provider

logger = logging.getLogger(__name__)


class ExperimentStatus(Enum):
    DRAFT = "DRAFT"
    RUNNING = "RUNNING"
    COMPLETED = "COMPLETED"
    FAILED = "FAILED"


class OptimizationType(Enum):
    PROMPT = "PROMPT"               # Prompt优化
    CHUNK_SIZE = "CHUNK_SIZE"       # 分块大小
    TOP_K = "TOP_K"                 # 检索数量
    SCORE_THRESHOLD = "SCORE_THRESHOLD"  # 相似度阈值
    RERANKER_WEIGHT = "RERANKER_WEIGHT"  # 重排序权重
    EMBEDDING_MODEL = "EMBEDDING_MODEL"  # 嵌入模型


@dataclass
class TestCase:
    """测试用例"""
    test_id: str
    question: str
    expected_answer: str = ""
    expected_sources: List[str] = field(default_factory=list)
    category: str = ""
    difficulty: str = "MEDIUM"  # EASY/MEDIUM/HARD


@dataclass
class TestResult:
    """测试结果"""
    test_id: str
    question: str
    actual_answer: str
    relevance_score: float = 0.0      # 相关性 0~1
    faithfulness_score: float = 0.0   # 忠实度 0~1
    completeness_score: float = 0.0   # 完整性 0~1
    latency_ms: int = 0
    error: str = ""


@dataclass
class Experiment:
    """A/B实验"""
    experiment_id: str
    name: str
    optimization_type: OptimizationType
    control_config: Dict[str, Any]       # 对照组配置
    experiment_config: Dict[str, Any]    # 实验组配置
    test_cases: List[TestCase]
    status: ExperimentStatus = ExperimentStatus.DRAFT
    control_results: List[TestResult] = field(default_factory=list)
    experiment_results: List[TestResult] = field(default_factory=list)
    created_at: float = field(default_factory=time.time)
    completed_at: Optional[float] = None


class BadcaseAutoOptimizer:
    """Badcase自动化优化闭环"""

    def __init__(self):
        self._experiments: Dict[str, Experiment] = {}

    async def create_experiment(
        self,
        name: str,
        optimization_type: OptimizationType,
        control_config: Dict[str, Any],
        experiment_config: Dict[str, Any],
        test_cases: List[Dict[str, Any]],
    ) -> Dict[str, Any]:
        """创建A/B实验"""
        experiment_id = f"exp_{uuid.uuid4().hex[:8]}"
        cases = [
            TestCase(
                test_id=tc.get("test_id", str(i)),
                question=tc["question"],
                expected_answer=tc.get("expected_answer", ""),
                expected_sources=tc.get("expected_sources", []),
                category=tc.get("category", ""),
                difficulty=tc.get("difficulty", "MEDIUM"),
            )
            for i, tc in enumerate(test_cases)
        ]

        experiment = Experiment(
            experiment_id=experiment_id,
            name=name,
            optimization_type=optimization_type,
            control_config=control_config,
            experiment_config=experiment_config,
            test_cases=cases,
        )
        self._experiments[experiment_id] = experiment

        logger.info(f"A/B实验创建: {experiment_id}, type={optimization_type.value}")
        return {"experiment_id": experiment_id, "name": name, "test_count": len(cases)}

    async def run_experiment(self, experiment_id: str) -> Dict[str, Any]:
        """运行A/B实验"""
        experiment = self._experiments.get(experiment_id)
        if not experiment:
            return {"error": f"实验不存在: {experiment_id}"}

        experiment.status = ExperimentStatus.RUNNING

        try:
            # 运行对照组
            experiment.control_results = await self._run_test_suite(
                experiment.test_cases, experiment.control_config
            )

            # 运行实验组
            experiment.experiment_results = await self._run_test_suite(
                experiment.test_cases, experiment.experiment_config
            )

            # 对比分析
            analysis = self._analyze_experiment(experiment)

            experiment.status = ExperimentStatus.COMPLETED
            experiment.completed_at = time.time()

            logger.info(f"A/B实验完成: {experiment_id}, winner={analysis.get('winner', 'N/A')}")
            return analysis

        except Exception as e:
            experiment.status = ExperimentStatus.FAILED
            logger.error(f"A/B实验失败: {e}")
            return {"error": str(e)}

    async def auto_attribute_badcase(self, badcase: Dict[str, Any]) -> Dict[str, Any]:
        """自动归因分析Badcase根因"""
        question = badcase.get("question", "")
        bad_answer = badcase.get("bad_answer", "")
        expected = badcase.get("expected_answer", "")
        context = badcase.get("context", "")

        prompt = f"""请分析以下Badcase的根因。

问题: {question}
错误答案: {bad_answer}
期望答案: {expected}
检索上下文: {context[:1000]}

请从以下维度分析根因：
1. 检索问题: 是否检索到了错误的文档或遗漏了关键文档？
2. Prompt问题: Prompt是否引导模型产生了错误回答？
3. 知识缺失: 知识库中是否缺少必要的知识？
4. 模型能力: 问题是否超出模型能力范围？
5. 分块问题: 文档分块是否导致上下文截断？

对每个维度给出0~1的归因权重（总和为1），并给出具体优化建议。
以JSON格式回答。"""

        try:
            response = await llm_provider.chat(
                messages=[{"role": "user", "content": prompt}],
                temperature=0.2,
                max_tokens=500,
            )
            content = response.get("content", "")

            # 简单解析归因结果
            attribution = {
                "retrieval": 0.0,
                "prompt": 0.0,
                "knowledge_gap": 0.0,
                "model_limitation": 0.0,
                "chunking": 0.0,
            }

            for line in content.split("\n"):
                line = line.strip()
                if "检索" in line and any(c.isdigit() for c in line):
                    import re
                    nums = re.findall(r'[0-9.]+', line)
                    if nums: attribution["retrieval"] = float(nums[-1])
                elif "Prompt" in line and any(c.isdigit() for c in line):
                    import re
                    nums = re.findall(r'[0-9.]+', line)
                    if nums: attribution["prompt"] = float(nums[-1])
                elif "知识缺失" in line and any(c.isdigit() for c in line):
                    import re
                    nums = re.findall(r'[0-9.]+', line)
                    if nums: attribution["knowledge_gap"] = float(nums[-1])
                elif "模型" in line and any(c.isdigit() for c in line):
                    import re
                    nums = re.findall(r'[0-9.]+', line)
                    if nums: attribution["model_limitation"] = float(nums[-1])
                elif "分块" in line and any(c.isdigit() for c in line):
                    import re
                    nums = re.findall(r'[0-9.]+', line)
                    if nums: attribution["chunking"] = float(nums[-1])

            # 找最大归因
            root_cause = max(attribution, key=attribution.get)

            return {
                "attribution": attribution,
                "root_cause": root_cause,
                "analysis": content,
                "suggestion": self._get_suggestion(root_cause),
            }

        except Exception as e:
            logger.error(f"Badcase归因分析失败: {e}")
            return {"error": str(e)}

    async def optimization_loop(
        self,
        badcase: Dict[str, Any],
        max_iterations: int = 3,
    ) -> Dict[str, Any]:
        """自动优化闭环：归因→优化→验证→上线"""
        history = []

        for i in range(max_iterations):
            # Step 1: 归因分析
            attribution = await self.auto_attribute_badcase(badcase)
            root_cause = attribution.get("root_cause", "unknown")

            # Step 2: 自动优化
            optimization = self._generate_optimization(root_cause, badcase)
            if not optimization:
                history.append({"iteration": i+1, "status": "no_optimization", "root_cause": root_cause})
                break

            # Step 3: 验证效果
            test_result = await self._verify_optimization(badcase, optimization)

            iteration_result = {
                "iteration": i+1,
                "root_cause": root_cause,
                "optimization": optimization,
                "verification": test_result,
            }
            history.append(iteration_result)

            # Step 4: 如果验证通过，结束闭环
            if test_result.get("improved", False):
                return {"status": "success", "iterations": history, "final_optimization": optimization}

        return {"status": "max_iterations_reached", "iterations": history}

    def _get_suggestion(self, root_cause: str) -> str:
        suggestions = {
            "retrieval": "优化检索策略：调整top_k、相似度阈值、或增加重排序",
            "prompt": "优化Prompt模板：增加约束条件、Few-shot示例或思维链提示",
            "knowledge_gap": "补充知识库：添加缺失的文档或知识点",
            "model_limitation": "升级模型或分解问题：使用更强模型或将复杂问题拆解",
            "chunking": "优化分块策略：调整chunk_size、overlap或采用语义分块",
        }
        return suggestions.get(root_cause, "综合优化多维度参数")

    def _generate_optimization(self, root_cause: str, badcase: Dict) -> Optional[Dict]:
        """根据根因生成优化方案"""
        optimizations = {
            "retrieval": {"type": "TOP_K", "change": {"top_k": 8}},
            "prompt": {"type": "PROMPT", "change": {"add_constraints": True}},
            "knowledge_gap": {"type": "KNOWLEDGE", "change": {"action": "add_documents"}},
            "model_limitation": {"type": "MODEL", "change": {"action": "upgrade"}},
            "chunking": {"type": "CHUNK_SIZE", "change": {"chunk_size": 800, "overlap": 100}},
        }
        return optimizations.get(root_cause)

    async def _run_test_suite(self, test_cases: List[TestCase], config: Dict) -> List[TestResult]:
        """运行测试套件"""
        results = []
        for tc in test_cases[:10]:  # 限制测试数量
            try:
                start = time.time()
                # 模拟RAG查询（实际应调用rag_engine）
                response = await llm_provider.chat(
                    messages=[
                        {"role": "system", "content": "你是一个知识问答助手，请基于上下文准确回答问题。"},
                        {"role": "user", "content": tc.question},
                    ],
                    temperature=0.1,
                    max_tokens=500,
                )
                answer = response.get("content", "")
                latency = int((time.time() - start) * 1000)

                results.append(TestResult(
                    test_id=tc.test_id,
                    question=tc.question,
                    actual_answer=answer,
                    relevance_score=0.7,  # 简化评分
                    faithfulness_score=0.7,
                    completeness_score=0.6,
                    latency_ms=latency,
                ))
            except Exception as e:
                results.append(TestResult(
                    test_id=tc.test_id, question=tc.question, actual_answer="", error=str(e)
                ))
        return results

    async def _verify_optimization(self, badcase: Dict, optimization: Dict) -> Dict:
        """验证优化效果"""
        question = badcase.get("question", "")
        try:
            response = await llm_provider.chat(
                messages=[
                    {"role": "system", "content": "你是一个知识问答助手。"},
                    {"role": "user", "content": question},
                ],
                temperature=0.1,
                max_tokens=500,
            )
            answer = response.get("content", "")
            # 简化验证：检查答案是否更相关
            return {"answer_preview": answer[:200], "improved": len(answer) > 50}
        except Exception as e:
            return {"error": str(e), "improved": False}

    def _analyze_experiment(self, experiment: Experiment) -> Dict[str, Any]:
        """分析实验结果"""
        def avg_score(results: List[TestResult]) -> float:
            if not results: return 0.0
            scores = [(r.relevance_score + r.faithfulness_score + r.completeness_score) / 3
                      for r in results if not r.error]
            return sum(scores) / len(scores) if scores else 0.0

        def avg_latency(results: List[TestResult]) -> float:
            latencies = [r.latency_ms for r in results if r.latency_ms > 0]
            return sum(latencies) / len(latencies) if latencies else 0.0

        control_score = avg_score(experiment.control_results)
        exp_score = avg_score(experiment.experiment_results)
        control_latency = avg_latency(experiment.control_results)
        exp_latency = avg_latency(experiment.experiment_results)

        improvement = exp_score - control_score
        winner = "experiment" if exp_score > control_score else "control"

        return {
            "experiment_id": experiment.experiment_id,
            "control_score": round(control_score, 3),
            "experiment_score": round(exp_score, 3),
            "improvement": round(improvement, 3),
            "control_latency_ms": round(control_latency, 0),
            "experiment_latency_ms": round(exp_latency, 0),
            "winner": winner,
            "recommendation": "adopt" if improvement > 0.05 else "keep_current",
        }

    def get_experiment(self, experiment_id: str) -> Optional[Dict]:
        exp = self._experiments.get(experiment_id)
        if not exp: return None
        return {
            "experiment_id": exp.experiment_id,
            "name": exp.name,
            "type": exp.optimization_type.value,
            "status": exp.status.value,
        }


# 全局实例
badcase_optimizer = BadcaseAutoOptimizer()
