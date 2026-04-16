"""
向量检索前置过滤器测试
"""
import pytest
from app.services.vector_filter import (
    VectorSearchFilter,
    FilterConfig,
    UserPermission,
    TenantIsolationLevel,
    FilterExpressionOptimizer,
    create_user_permission,
    get_filter_instance,
)


class TestVectorSearchFilter:
    """向量检索前置过滤器测试"""
    
    @pytest.fixture
    def filter_config(self):
        return FilterConfig(
            isolation_level=TenantIsolationLevel.METADATA_FILTER,
            enable_dept_filter=True,
            enable_role_filter=True,
            enable_space_filter=True,
            enable_doc_filter=False,
        )
    
    @pytest.fixture
    def user_permission(self):
        return create_user_permission(
            user_id="user_001",
            tenant_id="tenant_001",
            dept_id="dept_001",
            role_ids=["role_admin", "role_editor"],
            security_level=4,
            allowed_space_ids=["space_001", "space_002"],
        )
    
    def test_build_tenant_filter(self, filter_config, user_permission):
        """测试租户隔离过滤"""
        filter_instance = VectorSearchFilter(filter_config)
        expr = filter_instance.build_filter_expression(user_permission)
        
        assert "tenant_id" in expr
        assert "tenant_001" in expr
    
    def test_build_security_level_filter(self, filter_config, user_permission):
        """测试安全等级过滤"""
        filter_instance = VectorSearchFilter(filter_config)
        expr = filter_instance.build_filter_expression(user_permission)
        
        assert "security_level <= 4" in expr
    
    def test_build_space_filter(self, filter_config, user_permission):
        """测试空间权限过滤"""
        filter_instance = VectorSearchFilter(filter_config)
        expr = filter_instance.build_filter_expression(
            user_permission,
            space_id="space_001"
        )
        
        assert 'space_id == "space_001"' in expr
    
    def test_build_dept_filter(self, filter_config, user_permission):
        """测试部门权限过滤"""
        filter_instance = VectorSearchFilter(filter_config)
        expr = filter_instance.build_filter_expression(user_permission)
        
        assert "owning_dept_id" in expr
    
    def test_build_role_filter(self, filter_config, user_permission):
        """测试角色权限过滤"""
        filter_instance = VectorSearchFilter(filter_config)
        expr = filter_instance.build_filter_expression(user_permission)
        
        assert "allowed_role_ids" in expr
        assert "role_admin" in expr
    
    def test_permission_validation_success(self, filter_config, user_permission):
        """测试权限验证 - 成功"""
        filter_instance = VectorSearchFilter(filter_config)
        
        result = filter_instance.validate_permission(
            user_permission,
            target_space_id="space_001",
            target_security_level=3
        )
        
        assert result is True
    
    def test_permission_validation_fail_space(self, filter_config, user_permission):
        """测试权限验证 - 空间无权"""
        filter_instance = VectorSearchFilter(filter_config)
        
        result = filter_instance.validate_permission(
            user_permission,
            target_space_id="space_999",  # 无权访问
        )
        
        assert result is False
    
    def test_permission_validation_fail_security(self, filter_config, user_permission):
        """测试权限验证 - 安全等级不足"""
        filter_instance = VectorSearchFilter(filter_config)
        
        result = filter_instance.validate_permission(
            user_permission,
            target_security_level=5  # 超过用户等级
        )
        
        assert result is False
    
    def test_collection_name_metadata_filter(self, filter_config):
        """测试Collection名称 - 元数据过滤"""
        filter_instance = VectorSearchFilter(filter_config)
        name = filter_instance.get_collection_name("tenant_001", "space_001")
        
        assert name == "knowledge_chunks"
    
    def test_collection_name_collection_isolation(self):
        """测试Collection名称 - 集合隔离"""
        config = FilterConfig(isolation_level=TenantIsolationLevel.COLLECTION)
        filter_instance = VectorSearchFilter(config)
        name = filter_instance.get_collection_name("tenant_001", "space_001")
        
        assert name == "tenant_tenant_001_space_space_001"


class TestFilterExpressionOptimizer:
    """过滤表达式优化器测试"""
    
    def test_optimize_remove_duplicates(self):
        """测试优化 - 去重"""
        expr = 'a == 1 and a == 1 and b == 2'
        optimized = FilterExpressionOptimizer.optimize(expr)
        
        # 应该去除重复条件
        assert optimized.count("a == 1") == 1
    
    def test_optimize_order(self):
        """测试优化 - 条件排序"""
        expr = 'a <= 10 and b == 2 and c in [1,2,3]'
        optimized = FilterExpressionOptimizer.optimize(expr)
        
        # 等值判断应该优先
        eq_pos = optimized.find("b == 2")
        le_pos = optimized.find("a <= 10")
        
        assert eq_pos < le_pos
    
    def test_validate_valid_expression(self):
        """测试验证 - 有效表达式"""
        expr = 'security_level <= 4 and space_id == "space_001"'
        assert FilterExpressionOptimizer.validate(expr) is True
    
    def test_validate_invalid_expression(self):
        """测试验证 - 无效表达式（括号不匹配）"""
        expr = '(security_level <= 4 and space_id == "space_001"'
        assert FilterExpressionOptimizer.validate(expr) is False


class TestFilterInstance:
    """全局过滤器实例测试"""
    
    def test_get_filter_instance(self):
        """测试获取全局实例"""
        instance1 = get_filter_instance()
        instance2 = get_filter_instance()
        
        # 应该是同一实例（单例）
        assert instance1 is instance2
    
    def test_create_user_permission(self):
        """测试创建用户权限"""
        perm = create_user_permission(
            user_id="user_001",
            tenant_id="tenant_001",
            dept_id="dept_001",
            role_ids=["role_admin"],
            security_level=4,
            allowed_space_ids=["space_001"],
        )
        
        assert perm.user_id == "user_001"
        assert perm.tenant_id == "tenant_001"
        assert len(perm.role_ids) == 1
