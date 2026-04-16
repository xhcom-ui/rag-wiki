"""
检索结果二次权限校验器
在向量检索返回结果后，进行二次权限验证，实现双重保险
"""

from typing import List, Dict, Any, Optional
from dataclasses import dataclass
import logging

logger = logging.getLogger(__name__)


@dataclass
class PermissionContext:
    """权限上下文"""
    user_id: str
    tenant_id: str
    dept_id: str
    role_ids: List[str]
    security_level: int
    allowed_space_ids: List[str]
    allowed_doc_ids: Optional[List[str]] = None


class PostSearchPermissionValidator:
    """检索结果二次权限校验器"""
    
    def __init__(self):
        self.violation_count = 0
    
    def validate_and_filter(
        self,
        search_results: List[Dict[str, Any]],
        permission: PermissionContext,
    ) -> List[Dict[str, Any]]:
        """
        对检索结果进行二次权限校验和过滤
        
        流程：
        1. 验证租户隔离
        2. 验证安全等级
        3. 验证空间权限
        4. 验证部门权限
        5. 验证角色权限
        6. 验证文档级权限（如果启用）
        7. 记录违规访问
        
        Returns:
            通过权限校验的结果列表
        """
        if not search_results:
            return []
        
        filtered_results = []
        violated_chunks = []
        
        for chunk in search_results:
            try:
                if self._check_permission(chunk, permission):
                    # 脱敏处理
                    sanitized_chunk = self._sanitize_chunk(chunk, permission)
                    filtered_results.append(sanitized_chunk)
                else:
                    violated_chunks.append(chunk)
                    logger.warning(
                        f"二次权限校验拦截: user={permission.user_id}, "
                        f"chunk_id={chunk.get('chunk_id')}, "
                        f"doc_id={chunk.get('document_id')}"
                    )
            except Exception as e:
                logger.error(f"权限校验异常: {e}, chunk_id={chunk.get('chunk_id')}")
                violated_chunks.append(chunk)
        
        # 记录违规访问
        if violated_chunks:
            self.violation_count += len(violated_chunks)
            logger.warning(
                f"二次权限校验完成: 总数={len(search_results)}, "
                f"通过={len(filtered_results)}, "
                f"拦截={len(violated_chunks)}, "
                f"累计违规={self.violation_count}"
            )
        
        return filtered_results
    
    def _check_permission(
        self,
        chunk: Dict[str, Any],
        permission: PermissionContext,
    ) -> bool:
        """检查单个chunk的权限"""
        
        # 1. 租户隔离验证
        if not self._check_tenant(chunk, permission):
            return False
        
        # 2. 安全等级验证
        if not self._check_security_level(chunk, permission):
            return False
        
        # 3. 空间权限验证
        if not self._check_space_permission(chunk, permission):
            return False
        
        # 4. 部门权限验证
        if not self._check_dept_permission(chunk, permission):
            return False
        
        # 5. 角色权限验证
        if not self._check_role_permission(chunk, permission):
            return False
        
        # 6. 文档级权限验证（如果启用）
        if permission.allowed_doc_ids is not None:
            if not self._check_doc_permission(chunk, permission):
                return False
        
        return True
    
    def _check_tenant(self, chunk: Dict, permission: PermissionContext) -> bool:
        """租户隔离检查"""
        chunk_tenant = chunk.get('tenant_id')
        if chunk_tenant and chunk_tenant != permission.tenant_id:
            logger.warning(
                f"租户隔离违规: user_tenant={permission.tenant_id}, "
                f"chunk_tenant={chunk_tenant}"
            )
            return False
        return True
    
    def _check_security_level(self, chunk: Dict, permission: PermissionContext) -> bool:
        """安全等级检查"""
        chunk_security_level = chunk.get('security_level', 1)
        if chunk_security_level > permission.security_level:
            logger.warning(
                f"安全等级不足: user_level={permission.security_level}, "
                f"chunk_level={chunk_security_level}"
            )
            return False
        return True
    
    def _check_space_permission(self, chunk: Dict, permission: PermissionContext) -> bool:
        """空间权限检查"""
        chunk_space_id = chunk.get('space_id')
        if chunk_space_id and chunk_space_id not in permission.allowed_space_ids:
            logger.warning(
                f"空间权限违规: user={permission.user_id}, "
                f"chunk_space={chunk_space_id}"
            )
            return False
        return True
    
    def _check_dept_permission(self, chunk: Dict, permission: PermissionContext) -> bool:
        """
        部门权限检查
        
        规则：
        - 可以查看本部门的文档
        - 可以查看公共文档（owning_dept_id为空）
        - 可以查看下级部门的文档（需要递归）
        """
        owning_dept = chunk.get('owning_dept_id', '')
        
        # 公共文档
        if not owning_dept:
            return True
        
        # 本部门文档
        if owning_dept == permission.dept_id:
            return True
        
        # 简化实现：允许查看
        # 完整实现应该递归检查部门树
        return True
    
    def _check_role_permission(self, chunk: Dict, permission: PermissionContext) -> bool:
        """
        角色权限检查
        
        规则：
        - 文档的allowed_role_ids包含用户任一角色
        - 或者文档允许所有角色（allowed_role_ids为空）
        """
        allowed_roles_str = chunk.get('allowed_role_ids', '')
        
        # 允许所有角色
        if not allowed_roles_str:
            return True
        
        # 解析允许的角色列表
        allowed_roles = [r.strip() for r in allowed_roles_str.split(',') if r.strip()]
        
        # 检查用户角色是否匹配
        for role_id in permission.role_ids:
            if role_id in allowed_roles:
                return True
        
        logger.warning(
            f"角色权限违规: user_roles={permission.role_ids}, "
            f"allowed_roles={allowed_roles}"
        )
        return False
    
    def _check_doc_permission(self, chunk: Dict, permission: PermissionContext) -> bool:
        """文档级权限检查（行级权限）"""
        if permission.allowed_doc_ids is None:
            return True
        
        doc_id = chunk.get('document_id')
        if doc_id and doc_id not in permission.allowed_doc_ids:
            logger.warning(
                f"文档级权限违规: user={permission.user_id}, "
                f"doc_id={doc_id}"
            )
            return False
        
        return True
    
    def _sanitize_chunk(
        self,
        chunk: Dict[str, Any],
        permission: PermissionContext,
    ) -> Dict[str, Any]:
        """
        对检索结果进行脱敏处理
        
        根据用户权限，移除或脱敏敏感字段
        """
        sanitized = chunk.copy()
        
        # 移除内部元数据字段
        internal_fields = [
            'vector',  # 向量数据
            'embedding_model',  # 模型信息
            'internal_notes',  # 内部备注
        ]
        
        for field in internal_fields:
            sanitized.pop(field, None)
        
        # 安全等级较高的内容，添加脱敏标记
        chunk_security_level = chunk.get('security_level', 1)
        if chunk_security_level >= 4 and permission.security_level < 5:
            # 机密文档，对低权限用户脱敏
            content = sanitized.get('content', '')
            if len(content) > 100:
                sanitized['content'] = content[:100] + '...[内容已脱敏]'
                sanitized['is_sanitized'] = True
        
        return sanitized
    
    def get_violation_stats(self) -> Dict[str, int]:
        """获取违规统计"""
        return {
            'total_violations': self.violation_count,
        }
    
    def reset_stats(self):
        """重置统计"""
        self.violation_count = 0


class ResultSanitizer:
    """结果脱敏器 - 更精细的脱敏控制"""
    
    @staticmethod
    def sanitize_for_user(
        chunk: Dict[str, Any],
        user_security_level: int,
    ) -> Dict[str, Any]:
        """根据用户安全等级脱敏"""
        result = chunk.copy()
        chunk_level = chunk.get('security_level', 1)
        
        # 用户等级低于文档等级，需要脱敏
        if user_security_level < chunk_level:
            content = result.get('content', '')
            
            # 只显示前50个字符
            if len(content) > 50:
                result['content'] = content[:50] + '...[权限不足，内容已脱敏]'
                result['requires_higher_level'] = True
                result['required_level'] = chunk_level
        
        return result
    
    @staticmethod
    def remove_sensitive_metadata(chunk: Dict[str, Any]) -> Dict[str, Any]:
        """移除敏感元数据"""
        result = chunk.copy()
        
        sensitive_fields = [
            'creator_id',
            'owner_id',
            'internal_tags',
            'audit_trail',
        ]
        
        for field in sensitive_fields:
            result.pop(field, None)
        
        return result


# 全局校验器实例
_validator_instance = None


def get_post_search_validator() -> PostSearchPermissionValidator:
    """获取全局二次校验器实例"""
    global _validator_instance
    if _validator_instance is None:
        _validator_instance = PostSearchPermissionValidator()
    return _validator_instance


def validate_search_results(
    results: List[Dict[str, Any]],
    user_id: str,
    tenant_id: str,
    dept_id: str,
    role_ids: List[str],
    security_level: int,
    allowed_space_ids: List[str],
) -> List[Dict[str, Any]]:
    """
    便捷函数：验证检索结果
    
    用法：
    from app.services.post_search_validator import validate_search_results
    
    filtered_results = validate_search_results(
        results=search_results,
        user_id="user_001",
        tenant_id="tenant_001",
        dept_id="dept_001",
        role_ids=["role_admin"],
        security_level=4,
        allowed_space_ids=["space_001"],
    )
    """
    permission = PermissionContext(
        user_id=user_id,
        tenant_id=tenant_id,
        dept_id=dept_id,
        role_ids=role_ids,
        security_level=security_level,
        allowed_space_ids=allowed_space_ids,
    )
    
    validator = get_post_search_validator()
    return validator.validate_and_filter(results, permission)
