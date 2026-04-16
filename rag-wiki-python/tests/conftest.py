"""
Python AI服务 - 自动化测试配置
"""
import pytest
from httpx import AsyncClient, ASGITransport
from app.main import app


@pytest.fixture
async def client():
    """测试客户端"""
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        yield ac


@pytest.fixture
def mock_user_permission():
    """模拟用户权限"""
    return {
        "user_id": "test_user_001",
        "tenant_id": "tenant_001",
        "dept_id": "dept_001",
        "role_ids": ["role_admin", "role_editor"],
        "security_level": 4,
        "allowed_space_ids": ["space_001", "space_002"],
    }


@pytest.fixture
def mock_document_chunks():
    """模拟文档分块数据"""
    return [
        {
            "chunk_id": "chunk_001",
            "document_id": "doc_001",
            "document_name": "测试文档",
            "space_id": "space_001",
            "content": "这是测试内容，用于验证检索功能",
            "chunk_index": 0,
            "page_num": 1,
            "security_level": 3,
            "owning_dept_id": "dept_001",
            "allowed_role_ids": "role_admin,role_editor",
        }
    ]
