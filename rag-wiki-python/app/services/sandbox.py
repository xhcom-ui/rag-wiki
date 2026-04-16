"""
代码沙箱服务 - Docker容器池 + AST安全检测 + 执行管理
"""
import ast
import json
import logging
import uuid
import time
from typing import List, Dict, Any, Optional

from app.core.config import settings

logger = logging.getLogger(__name__)


class ASTSecurityChecker:
    """AST静态安全检测"""

    # 危险模块
    DANGEROUS_MODULES = {
        "os", "subprocess", "socket", "shutil", "sys", "ctypes",
        "multiprocessing", "threading", "signal", "resource",
        "importlib", "pickle", "shelve", "marshal", "compile",
    }

    # 危险函数
    DANGEROUS_FUNCTIONS = {
        "eval", "exec", "compile", "open", "input",
        "__import__", "globals", "locals", "vars",
        "getattr", "setattr", "delattr", "type",
    }

    # 危险属性
    DANGEROUS_ATTRS = {
        "__class__", "__bases__", "__subclasses__", "__globals__",
        "__code__", "__closure__", "__dict__",
    }

    def check(self, code: str) -> Dict[str, Any]:
        """
        AST静态安全检测

        Returns:
            {is_safe, risks, risk_level}
        """
        risks = []
        risk_level = "safe"  # safe / warning / danger

        # 1. 正则快速扫描（不可执行代码的模式）
        quick_risks = self._quick_scan(code)
        risks.extend(quick_risks)

        # 2. AST深度分析
        try:
            tree = ast.parse(code)
            ast_risks = self._analyze_ast(tree)
            risks.extend(ast_risks)
        except SyntaxError as e:
            risks.append({"type": "syntax_error", "message": f"语法错误: {e}", "severity": "danger"})
            risk_level = "danger"
            return {"is_safe": False, "risks": risks, "risk_level": risk_level}

        # 3. 判定风险等级
        if any(r.get("severity") == "danger" for r in risks):
            risk_level = "danger"
        elif any(r.get("severity") == "warning" for r in risks):
            risk_level = "warning"

        return {
            "is_safe": risk_level == "safe",
            "risks": risks,
            "risk_level": risk_level,
        }

    def _quick_scan(self, code: str) -> List[Dict]:
        """快速正则扫描"""
        risks = []
        patterns = {
            "os.system": ("danger", "检测到os.system调用，可能执行系统命令"),
            "subprocess": ("danger", "检测到subprocess模块，可能执行外部命令"),
            "socket": ("danger", "检测到socket模块，可能进行网络访问"),
            "eval(": ("danger", "检测到eval()调用，可能执行任意代码"),
            "exec(": ("danger", "检测到exec()调用，可能执行任意代码"),
            "__import__": ("danger", "检测到__import__()调用，可能动态导入模块"),
            "open('/": ("warning", "检测到绝对路径文件操作"),
            "shutil": ("danger", "检测到shutil模块，可能进行文件系统操作"),
            "sys.exit": ("warning", "检测到sys.exit()调用，可能终止进程"),
            "rm -rf": ("danger", "检测到危险shell命令模式"),
            "os.remove": ("danger", "检测到文件删除操作"),
            "pickle": ("warning", "检测到pickle模块，可能存在反序列化漏洞"),
        }
        for pattern, (severity, message) in patterns.items():
            if pattern in code:
                risks.append({"type": "pattern_match", "pattern": pattern,
                              "message": message, "severity": severity})
        return risks

    def _analyze_ast(self, tree: ast.AST) -> List[Dict]:
        """AST深度分析"""
        risks = []

        for node in ast.walk(tree):
            # 检查import语句
            if isinstance(node, ast.Import):
                for alias in node.names:
                    if alias.name in self.DANGEROUS_MODULES:
                        risks.append({
                            "type": "dangerous_import",
                            "module": alias.name,
                            "message": f"检测到危险模块导入: {alias.name}",
                            "severity": "danger",
                        })

            # 检查from...import语句
            elif isinstance(node, ast.ImportFrom):
                if node.module in self.DANGEROUS_MODULES:
                    risks.append({
                        "type": "dangerous_import",
                        "module": node.module,
                        "message": f"检测到危险模块导入: from {node.module}",
                        "severity": "danger",
                    })

            # 检查函数调用
            elif isinstance(node, ast.Call):
                func_name = self._get_func_name(node)
                if func_name in self.DANGEROUS_FUNCTIONS:
                    risks.append({
                        "type": "dangerous_call",
                        "function": func_name,
                        "message": f"检测到危险函数调用: {func_name}",
                        "severity": "warning",
                    })

            # 检查属性访问
            elif isinstance(node, ast.Attribute):
                if node.attr in self.DANGEROUS_ATTRS:
                    risks.append({
                        "type": "dangerous_attr",
                        "attribute": node.attr,
                        "message": f"检测到危险属性访问: {node.attr}",
                        "severity": "warning",
                    })

        return risks

    @staticmethod
    def _get_func_name(node: ast.Call) -> str:
        """获取函数名"""
        if isinstance(node.func, ast.Name):
            return node.func.id
        elif isinstance(node.func, ast.Attribute):
            return node.func.attr
        return ""


class DockerSandboxPool:
    """Docker容器池管理"""

    def __init__(self):
        self._client = None
        self._pool: List[str] = []  # 可用容器ID列表
        self._in_use: Dict[str, Dict] = {}  # 正在使用的容器
        self._pool_size = settings.SANDBOX_POOL_SIZE

    def _get_client(self):
        if self._client is None:
            try:
                import docker
                self._client = docker.from_env()
                logger.info("Docker客户端初始化成功")
            except Exception as e:
                logger.warning(f"Docker客户端初始化失败: {e}，沙箱功能不可用")
        return self._client

    async def get_container(self) -> Optional[str]:
        """从池中获取一个容器"""
        client = self._get_client()
        if not client:
            return None

        if self._pool:
            container_id = self._pool.pop()
            self._in_use[container_id] = {"allocated_at": time.time()}
            return container_id

        # 创建新容器
        try:
            container = client.containers.run(
                image=settings.SANDBOX_DOCKER_IMAGE,
                detach=True,
                mem_limit=settings.SANDBOX_MEMORY_LIMIT,
                cpu_quota=settings.SANDBOX_CPU_QUOTA,
                network_disabled=True,
                tty=True,
                stdin_open=True,
            )
            container_id = container.id
            self._in_use[container_id] = {"allocated_at": time.time()}
            logger.info(f"创建沙箱容器: {container_id[:12]}")
            return container_id
        except Exception as e:
            logger.error(f"创建沙箱容器失败: {e}")
            return None

    async def release_container(self, container_id: str):
        """释放容器回池中"""
        if container_id in self._in_use:
            del self._in_use[container_id]

        # 如果池已满，销毁容器
        if len(self._pool) >= self._pool_size:
            try:
                client = self._get_client()
                if client:
                    container = client.containers.get(container_id)
                    container.remove(force=True)
                    logger.info(f"销毁沙箱容器: {container_id[:12]}")
            except Exception as e:
                logger.warning(f"销毁容器失败: {e}")
        else:
            # 重置容器状态后放回池中
            try:
                client = self._get_client()
                if client:
                    container = client.containers.get(container_id)
                    container.exec_run("sh -c 'rm -rf /tmp/*'")
                    self._pool.append(container_id)
            except Exception:
                pass

    async def execute_in_container(
        self, container_id: str, code: str, timeout: int = 30
    ) -> Dict[str, Any]:
        """在容器中执行代码"""
        client = self._get_client()
        if not client:
            return {"status": "error", "output": "", "error": "Docker不可用"}

        try:
            container = client.containers.get(container_id)

            # 写入代码到容器
            exec_result = container.exec_run(
                cmd=f"sh -c 'cat > /tmp/code.py << \"EOF\"\n{code}\nEOF\npython /tmp/code.py'",
                workdir="/tmp",
                demux=True,
            )

            exit_code = exec_result.exit_code
            stdout = exec_result.output[0].decode("utf-8", errors="replace") if exec_result.output[0] else ""
            stderr = exec_result.output[1].decode("utf-8", errors="replace") if exec_result.output[1] else ""

            return {
                "status": "completed" if exit_code == 0 else "error",
                "output": stdout,
                "error": stderr if exit_code != 0 else None,
                "exit_code": exit_code,
            }

        except Exception as e:
            logger.error(f"沙箱执行失败: {e}")
            return {"status": "error", "output": "", "error": str(e)}


class SandboxService:
    """代码沙箱服务 - 统一入口"""

    def __init__(self):
        self.security_checker = ASTSecurityChecker()
        self.pool = DockerSandboxPool()
        self._results: Dict[str, Dict] = {}

    def security_check(self, code: str) -> Dict[str, Any]:
        """代码安全检测"""
        return self.security_checker.check(code)

    async def execute_code(
        self,
        user_request: str,
        user_id: str,
        language: str = "python",
        timeout: int = 30,
    ) -> Dict[str, Any]:
        """
        安全代码执行流程:
        1. LLM生成代码
        2. AST安全检测
        3. Docker沙箱执行
        4. 结果安全校验
        """
        task_id = str(uuid.uuid4())[:12]

        # Step 1: LLM生成代码
        from app.services.rag_engine import llm_service
        code_result = await llm_service.chat_completion(
            messages=[
                {"role": "system", "content": "根据用户需求生成Python代码。只输出代码，不要解释。"},
                {"role": "user", "content": user_request},
            ],
            temperature=0.2,
        )

        # 提取代码
        code = self._extract_code(code_result)

        # Step 2: AST安全检测
        security = self.security_check(code)
        if not security["is_safe"]:
            result = {
                "task_id": task_id,
                "status": "REJECTED",
                "code": code,
                "security_check": security,
                "output": None,
                "error": f"代码未通过安全检测: {', '.join(r['message'] for r in security['risks'])}",
            }
            self._results[task_id] = result
            return result

        # Step 3: Docker沙箱执行
        container_id = await self.pool.get_container()
        if not container_id:
            result = {
                "task_id": task_id,
                "status": "UNAVAILABLE",
                "code": code,
                "security_check": security,
                "output": None,
                "error": "沙箱环境不可用",
            }
            self._results[task_id] = result
            return result

        try:
            exec_result = await self.pool.execute_in_container(container_id, code, timeout)
        finally:
            await self.pool.release_container(container_id)

        # Step 4: 结果安全校验
        output = exec_result.get("output", "")
        if self._contains_sensitive_data(output):
            output = "[输出已被安全过滤]"

        result = {
            "task_id": task_id,
            "status": exec_result.get("status", "UNKNOWN").upper(),
            "code": code,
            "security_check": security,
            "output": output[:10000],  # 限制输出长度
            "error": exec_result.get("error"),
        }
        self._results[task_id] = result
        return result

    def get_result(self, task_id: str) -> Optional[Dict]:
        """查询执行结果"""
        return self._results.get(task_id)

    @staticmethod
    def _extract_code(response: str) -> str:
        if "```python" in response:
            return response.split("```python")[1].split("```")[0]
        elif "```" in response:
            return response.split("```")[1].split("```")[0]
        return response

    @staticmethod
    def _contains_sensitive_data(text: str) -> bool:
        """检测输出中是否包含敏感数据"""
        sensitive_patterns = ["password", "secret", "token", "api_key", "private_key"]
        text_lower = text.lower()
        return any(p in text_lower for p in sensitive_patterns)


# 全局服务实例
sandbox_service = SandboxService()
