"""
增强型向量检索前置过滤器
支持：租户隔离 + 权限过滤 + 部门过滤 + 角色过滤
实现三层纵深防御架构
"""

from typing import List, Optional, Dict, Any
from dataclasses import dataclass
from enum import Enum
import logging

logger = logging.getLogger(__name__)


class TenantIsolationLevel(Enum):
    """租户隔离级别"""
    METADATA_FILTER = "metadata_filter"  # 元数据过滤（低）
    PARTITION = "partition"              # 分区隔离（中）
    COLLECTION = "collection"            # 集合隔离（高）


@dataclass
class UserPermission:
    """用户权限上下文"""
    user_id: str
    tenant_id: str
    dept_id: str
    role_ids: List[str]
    security_level: int  # 用户安全等级（1-5）
    allowed_space_ids: List[str]  # 允许访问的空间ID
    allowed_doc_ids: List[str] = None  # 允许访问的文档ID（行级权限）


@dataclass
class FilterConfig:
    """过滤配置"""
    isolation_level: TenantIsolationLevel = TenantIsolationLevel.METADATA_FILTER
    enable_dept_filter: bool = True
    enable_role_filter: bool = True
    enable_space_filter: bool = True
    enable_doc_filter: bool = False  # 行级权限（高性能消耗）


class VectorSearchFilter:
    """向量检索前置过滤器 - 核心安全组件"""
    
    def __init__(self, config: FilterConfig = None):
        self.config = config or FilterConfig()
    
    def build_filter_expression(
        self,
        permission: UserPermission,
        space_id: Optional[str] = None,
        document_id: Optional[str] = None,
    ) -> str:
        """
        构建完整的权限过滤表达式
        
        规则：
        1. 租户隔离（最高优先级）
        2. 安全等级过滤
        3. 空间权限过滤
        4. 部门权限过滤
        5. 角色权限过滤
        6. 文档级权限过滤（可选）
        """
        conditions = []
        
        # 1. 租户隔离（强制）
        tenant_filter = self._build_tenant_filter(permission.tenant_id)
        conditions.append(tenant_filter)
        
        # 2. 安全等级过滤（强制）
        conditions.append(f"security_level <= {permission.security_level}")
        
        # 3. 空间权限过滤
        if self.config.enable_space_filter:
            space_filter = self._build_space_filter(permission, space_id)
            if space_filter:
                conditions.append(space_filter)
        
        # 4. 部门权限过滤
        if self.config.enable_dept_filter and permission.dept_id:
            dept_filter = self._build_dept_filter(permission.dept_id)
            conditions.append(dept_filter)
        
        # 5. 角色权限过滤
        if self.config.enable_role_filter and permission.role_ids:
            role_filter = self._build_role_filter(permission.role_ids)
            if role_filter:
                conditions.append(role_filter)
        
        # 6. 文档级权限过滤（行级）
        if self.config.enable_doc_filter and permission.allowed_doc_ids:
            doc_filter = self._build_doc_filter(permission.allowed_doc_ids)
            if doc_filter:
                conditions.append(doc_filter)
        
        # 组合所有条件
        filter_expr = " and ".join(conditions)
        logger.debug(f"构建过滤表达式: {filter_expr}")
        return filter_expr
    
    def _build_tenant_filter(self, tenant_id: str) -> str:
        """租户隔离过滤"""
        if self.config.isolation_level == TenantIsolationLevel.COLLECTION:
            # 集合级别隔离：通过Collection名称隔离，无需过滤表达式
            return "1 == 1"
        elif self.config.isolation_level == TenantIsolationLevel.PARTITION:
            # 分区级别隔离：通过分区字段过滤
            return f'tenant_id == "{tenant_id}"'
        else:
            # 元数据过滤级别：通过tenant_id字段过滤
            return f'tenant_id == "{tenant_id}"'
    
    def _build_space_filter(self, permission: UserPermission, space_id: Optional[str]) -> Optional[str]:
        """空间权限过滤"""
        if space_id:
            # 指定空间：检查用户是否有权限
            if space_id not in permission.allowed_space_ids:
                raise PermissionError(f"用户 {permission.user_id} 无权访问空间 {space_id}")
            return f'space_id == "{space_id}"'
        else:
            # 未指定空间：限制在用户有权访问的空间范围内
            if permission.allowed_space_ids:
                space_list = ", ".join([f'"{sid}"' for sid in permission.allowed_space_ids])
                return f"space_id in [{space_list}]"
        return None
    
    def _build_dept_filter(self, dept_id: str) -> str:
        """
        部门权限过滤
        
        规则：
        - 可以查看本部门创建的文档
        - 可以查看公共文档（owning_dept_id为空）
        - 可以查看下级部门的文档（需要递归查询部门树）
        """
        # 简化实现：本部门 + 公共文档
        # 完整实现应该递归获取所有下级部门ID
        return f'(owning_dept_id == "{dept_id}" || owning_dept_id == "")'
    
    def _build_role_filter(self, role_ids: List[str]) -> Optional[str]:
        """
        角色权限过滤
        
        规则：
        - 文档的allowed_role_ids包含用户任一角色
        - 或者文档允许所有角色（allowed_role_ids为空）
        """
        if not role_ids:
            return None
        
        # 构建角色匹配表达式
        # 示例: (allowed_role_ids contains "role_1" || allowed_role_ids contains "role_2" || allowed_role_ids == "")
        role_conditions = [f'allowed_role_ids contains "{rid}"' for rid in role_ids]
        role_conditions.append('allowed_role_ids == ""')
        
        return "(" + " || ".join(role_conditions) + ")"
    
    def _build_doc_filter(self, allowed_doc_ids: List[str]) -> Optional[str]:
        """
        文档级权限过滤（行级权限）
        
        注意：此过滤性能消耗较大，仅在必要时启用
        """
        if not allowed_doc_ids:
            return None
        
        doc_list = ", ".join([f'"{did}"' for did in allowed_doc_ids])
        return f"document_id in [{doc_list}]"
    
    def get_collection_name(
        self,
        tenant_id: str,
        space_id: Optional[str] = None,
    ) -> str:
        """
        根据隔离级别获取Collection名称
        
        - 集合隔离：tenant_{tenant_id}_space_{space_id}
        - 分区/元数据隔离：knowledge_chunks
        """
        if self.config.isolation_level == TenantIsolationLevel.COLLECTION:
            if space_id:
                return f"tenant_{tenant_id}_space_{space_id}"
            return f"tenant_{tenant_id}"
        else:
            return "knowledge_chunks"
    
    def validate_permission(
        self,
        permission: UserPermission,
        target_space_id: Optional[str] = None,
        target_security_level: Optional[int] = None,
    ) -> bool:
        """
        前置权限验证（在检索前快速校验）
        
        Returns:
            bool: 是否有权限
        """
        # 验证安全等级
        if target_security_level and permission.security_level < target_security_level:
            logger.warning(
                f"用户 {permission.user_id} 安全等级不足: "
                f"user_level={permission.security_level}, required={target_security_level}"
            )
            return False
        
        # 验证空间权限
        if target_space_id and target_space_id not in permission.allowed_space_ids:
            logger.warning(
                f"用户 {permission.user_id} 无权访问空间 {target_space_id}"
            )
            return False
        
        return True


class FilterExpressionOptimizer:
    """过滤表达式优化器 - 提升检索性能"""
    
    @staticmethod
    def optimize(expr: str) -> str:
        """
        优化过滤表达式
        
        优化策略：
        1. 去除冗余条件
        2. 合并相同字段
        3. 调整条件顺序（高选择性条件优先）
        """
        if not expr:
            return expr
        
        # 解析条件
        conditions = expr.split(" and ")
        
        # 去重
        unique_conditions = list(dict.fromkeys(conditions))
        
        # 排序：等值判断优先，范围判断在后
        def condition_priority(cond):
            if "==" in cond:
                return 0  # 等值判断最快
            elif "<=" in cond or ">=" in cond:
                return 1  # 范围判断
            elif "in" in cond:
                return 2  # IN判断
            elif "contains" in cond:
                return 3  # 包含判断较慢
            else:
                return 4
        
        optimized = sorted(unique_conditions, key=condition_priority)
        
        return " and ".join(optimized)
    
    @staticmethod
    def validate(expr: str) -> bool:
        """验证过滤表达式语法"""
        try:
            # 简单语法检查
            if not expr:
                return False
            
            # 检查括号匹配
            if expr.count("(") != expr.count(")"):
                return False
            
            # 检查基本操作符
            valid_operators = ["==", "<=", ">=", "in", "contains", "and", "or", "||"]
            # 简化验证，实际应该使用AST解析
            
            return True
        except Exception:
            return False


# 全局过滤器实例
_filter_instance = None
_filter_config = FilterConfig()


def get_filter_instance(config: FilterConfig = None) -> VectorSearchFilter:
    """获取全局过滤器实例"""
    global _filter_instance, _filter_config
    
    if config:
        _filter_config = config
    
    if _filter_instance is None:
        _filter_instance = VectorSearchFilter(_filter_config)
    
    return _filter_instance


def create_user_permission(
    user_id: str,
    tenant_id: str,
    dept_id: str,
    role_ids: List[str],
    security_level: int,
    allowed_space_ids: List[str],
) -> UserPermission:
    """创建用户权限上下文（工厂方法）"""
    return UserPermission(
        user_id=user_id,
        tenant_id=tenant_id,
        dept_id=dept_id,
        role_ids=role_ids,
        security_level=security_level,
        allowed_space_ids=allowed_space_ids,
    )
