"""
安全代码沙箱服务
基于Docker实现隔离的代码执行环境
支持静态安全检测、资源限制、超时控制
"""
import ast
import logging
import re
import tempfile
import os
import shutil
import uuid
from dataclasses import dataclass
from typing import Dict, List, Optional, Any
from enum import Enum

logger = logging.getLogger(__name__)


class SecurityLevel(Enum):
    """安全等级"""
    SAFE = "safe"           # 无危险操作
    WARNING = "warning"     # 有潜在风险
    DANGEROUS = "dangerous" # 明确危险


@dataclass
class SecurityCheckResult:
    """安全检查结果"""
    is_safe: bool
    level: SecurityLevel
    issues: List[str]
    suggestions: List[str]


@dataclass
class ExecutionResult:
    """代码执行结果"""
    success: bool
    output: str
    error: Optional[str]
    execution_time: float
    memory_usage: float
    exit_code: int


class CodeSecurityChecker:
    """代码安全检查器"""
    
    # 危险模块黑名单
    DANGEROUS_MODULES = {
        'os', 'sys', 'subprocess', 'socket', 'requests', 'urllib', 'http',
        'ftplib', 'telnetlib', 'pickle', 'marshal', 'ctypes', 'multiprocessing',
        'threading', 'asyncio', 'importlib', 'imp', 'builtins', '__builtin__',
        'eval', 'exec', 'compile', 'open', 'input', 'raw_input'
    }
    
    # 危险函数黑名单
    DANGEROUS_FUNCTIONS = {
        'eval', 'exec', 'compile', '__import__', 'open', 'input', 'raw_input',
        'execfile', 'file', 'reload'
    }
    
    # 危险属性/方法
    DANGEROUS_ATTRIBUTES = {
        '__import__', '__subclasses__', '__bases__', '__globals__', '__locals__',
        '__code__', '__closure__', '__defaults__', '__getattribute__', '__setattr__',
        '__delattr__', '__dict__', '__class__', '__module__'
    }
    
    def check(self, code: str) -> SecurityCheckResult:
        """执行完整的安全检查"""
        issues = []
        suggestions = []
        
        # 1. AST语法分析
        try:
            tree = ast.parse(code)
        except SyntaxError as e:
            return SecurityCheckResult(
                is_safe=False,
                level=SecurityLevel.DANGEROUS,
                issues=[f"语法错误: {e}"],
                suggestions=["请检查代码语法"]
            )
        
        # 2. 遍历AST检查危险操作
        for node in ast.walk(tree):
            # 检查危险导入
            if isinstance(node, ast.Import):
                for alias in node.names:
                    if alias.name.split('.')[0] in self.DANGEROUS_MODULES:
                        issues.append(f"禁止导入危险模块: {alias.name}")
            
            elif isinstance(node, ast.ImportFrom):
                if node.module and node.module.split('.')[0] in self.DANGEROUS_MODULES:
                    issues.append(f"禁止从危险模块导入: {node.module}")
            
            # 检查危险函数调用
            elif isinstance(node, ast.Call):
                if isinstance(node.func, ast.Name):
                    if node.func.id in self.DANGEROUS_FUNCTIONS:
                        issues.append(f"禁止调用危险函数: {node.func.id}()")
                elif isinstance(node.func, ast.Attribute):
                    if node.func.attr in self.DANGEROUS_ATTRIBUTES:
                        issues.append(f"禁止访问危险属性: {node.func.attr}")
            
            # 检查危险表达式
            elif isinstance(node, ast.Call):
                # 检查eval/exec的直接调用
                if isinstance(node.func, ast.Name) and node.func.id in ('eval', 'exec'):
                    issues.append(f"禁止动态执行代码: {node.func.id}()")
        
        # 3. 正则表达式检查
        # 检查文件操作
        if re.search(r'\bopen\s*\(', code):
            issues.append("检测到文件操作，将在沙箱中受限执行")
        
        # 检查网络操作
        if re.search(r'\b(socket|requests|urllib|http)\b', code):
            issues.append("检测到网络操作，将在沙箱中被禁止")
        
        # 检查系统命令
        if re.search(r'\b(os\.system|subprocess|popen)\b', code):
            issues.append("检测到系统命令调用，将被禁止")
        
        # 4. 评估安全等级
        if issues:
            dangerous_count = sum(1 for i in issues if '禁止' in i)
            if dangerous_count > 0:
                level = SecurityLevel.DANGEROUS
                is_safe = False
            else:
                level = SecurityLevel.WARNING
                is_safe = True
        else:
            level = SecurityLevel.SAFE
            is_safe = True
        
        # 5. 生成建议
        if not is_safe:
            suggestions.append("请移除危险操作，使用安全的替代方案")
            suggestions.append("如需文件操作，请使用提供的安全API")
        
        return SecurityCheckResult(
            is_safe=is_safe,
            level=level,
            issues=issues,
            suggestions=suggestions
        )


class DockerSandbox:
    """Docker沙箱执行器"""
    
    def __init__(self):
        self.security_checker = CodeSecurityChecker()
        self.container_pool = []  # 容器预热池
        self.max_pool_size = 3
        
    def execute(self, code: str, timeout: int = 30, 
                memory_limit: str = "512m",
                cpu_limit: float = 0.5) -> ExecutionResult:
        """在沙箱中执行代码"""
        import time
        
        start_time = time.time()
        
        # 1. 安全检查
        security_result = self.security_checker.check(code)
        if not security_result.is_safe:
            return ExecutionResult(
                success=False,
                output="",
                error=f"安全检查未通过:\n" + "\n".join(security_result.issues),
                execution_time=0,
                memory_usage=0,
                exit_code=-1
            )
        
        # 2. 准备执行环境
        temp_dir = tempfile.mkdtemp(prefix="sandbox_")
        code_file = os.path.join(temp_dir, "user_code.py")
        
        try:
            # 写入代码文件
            with open(code_file, 'w', encoding='utf-8') as f:
                f.write(code)
            
            # 3. Docker执行
            result = self._execute_in_docker(
                temp_dir, timeout, memory_limit, cpu_limit
            )
            
            execution_time = time.time() - start_time
            result.execution_time = round(execution_time, 2)
            
            return result
            
        except Exception as e:
            logger.error(f"沙箱执行失败: {e}")
            return ExecutionResult(
                success=False,
                output="",
                error=f"执行失败: {str(e)}",
                execution_time=time.time() - start_time,
                memory_usage=0,
                exit_code=-1
            )
        finally:
            # 清理临时文件
            shutil.rmtree(temp_dir, ignore_errors=True)
    
    def _execute_in_docker(self, code_dir: str, timeout: int,
                          memory_limit: str, cpu_limit: float) -> ExecutionResult:
        """在Docker容器中执行"""
        try:
            import docker
            client = docker.from_env()
            
            # 容器配置
            container_config = {
                'image': 'python:3.11-slim',
                'command': ['python', '/code/user_code.py'],
                'volumes': {
                    code_dir: {'bind': '/code', 'mode': 'ro'}
                },
                'network_mode': 'none',  # 禁用网络
                'mem_limit': memory_limit,
                'cpu_quota': int(cpu_limit * 100000),  # CPU限制
                'cpu_period': 100000,
                'detach': True,
                'stdin_open': False,
                'tty': False,
            }
            
            # 创建并启动容器
            container = client.containers.run(**container_config)
            
            try:
                # 等待执行完成
                result = container.wait(timeout=timeout)
                
                # 获取输出
                stdout = container.logs(stdout=True, stderr=False).decode('utf-8', errors='replace')
                stderr = container.logs(stdout=False, stderr=True).decode('utf-8', errors='replace')
                
                exit_code = result.get('StatusCode', -1)
                
                return ExecutionResult(
                    success=exit_code == 0,
                    output=stdout,
                    error=stderr if stderr else None,
                    execution_time=0,  # 外部计算
                    memory_usage=0,    # 需要额外监控
                    exit_code=exit_code
                )
                
            finally:
                # 清理容器
                try:
                    container.stop(timeout=1)
                    container.remove(force=True)
                except:
                    pass
                    
        except ImportError:
            logger.warning("Docker SDK未安装，使用本地模拟执行")
            return self._execute_locally(code_dir, timeout)
        except Exception as e:
            logger.error(f"Docker执行失败: {e}")
            return ExecutionResult(
                success=False,
                output="",
                error=f"Docker执行失败: {str(e)}",
                execution_time=0,
                memory_usage=0,
                exit_code=-1
            )
    
    def _execute_locally(self, code_dir: str, timeout: int) -> ExecutionResult:
        """本地模拟执行（用于测试环境）"""
        import subprocess
        import time
        
        code_file = os.path.join(code_dir, "user_code.py")
        
        try:
            start_time = time.time()
            
            result = subprocess.run(
                ['python', code_file],
                capture_output=True,
                text=True,
                timeout=timeout
            )
            
            execution_time = time.time() - start_time
            
            return ExecutionResult(
                success=result.returncode == 0,
                output=result.stdout,
                error=result.stderr if result.stderr else None,
                execution_time=round(execution_time, 2),
                memory_usage=0,
                exit_code=result.returncode
            )
            
        except subprocess.TimeoutExpired:
            return ExecutionResult(
                success=False,
                output="",
                error=f"执行超时(>{timeout}秒)",
                execution_time=timeout,
                memory_usage=0,
                exit_code=-1
            )
        except Exception as e:
            return ExecutionResult(
                success=False,
                output="",
                error=str(e),
                execution_time=0,
                memory_usage=0,
                exit_code=-1
            )


class CodeInterpreterService:
    """代码解释器服务 - 对外提供API"""
    
    def __init__(self):
        self.sandbox = DockerSandbox()
    
    def analyze_data(self, user_request: str, data_files: List[Dict],
                    generated_code: str) -> Dict[str, Any]:
        """数据分析主入口"""
        logger.info(f"数据分析请求: {user_request[:50]}...")
        
        # 1. 安全检查
        security_result = self.sandbox.security_checker.check(generated_code)
        
        if security_result.level == SecurityLevel.DANGEROUS:
            return {
                "success": False,
                "error": "代码安全检查未通过",
                "security_issues": security_result.issues,
                "suggestions": security_result.suggestions
            }
        
        # 2. 执行代码
        execution_result = self.sandbox.execute(generated_code)
        
        # 3. 构建响应
        return {
            "success": execution_result.success,
            "output": execution_result.output,
            "error": execution_result.error,
            "execution_time": execution_result.execution_time,
            "security_warnings": security_result.issues if security_result.level == SecurityLevel.WARNING else [],
            "exit_code": execution_result.exit_code
        }
    
    def check_code(self, code: str) -> Dict[str, Any]:
        """仅检查代码安全性"""
        result = self.sandbox.security_checker.check(code)
        return {
            "is_safe": result.is_safe,
            "level": result.level.value,
            "issues": result.issues,
            "suggestions": result.suggestions
        }
    
    def execute_code(self, code: str, timeout: int = 30) -> Dict[str, Any]:
        """执行代码"""
        result = self.sandbox.execute(code, timeout=timeout)
        return {
            "success": result.success,
            "output": result.output,
            "error": result.error,
            "execution_time": result.execution_time,
            "exit_code": result.exit_code
        }


# 全局服务实例
sandbox_service = CodeInterpreterService()