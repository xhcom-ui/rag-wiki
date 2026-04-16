"""
通用数据校验工具
提供跨模块的通用校验函数
"""

import re
from typing import Optional, List, Any
from urllib.parse import urlparse


class ValidationError(Exception):
    """校验异常"""
    def __init__(self, field: str, message: str):
        self.field = field
        self.message = message
        super().__init__(f"{field}: {message}")


class CommonValidator:
    """通用校验器"""
    
    # ID格式：字母数字下划线，长度1-128
    ID_PATTERN = re.compile(r'^[a-zA-Z0-9_-]{1,128}$')
    
    # 用户名格式：字母数字下划线，3-50
    USERNAME_PATTERN = re.compile(r'^[a-zA-Z0-9_]{3,50}$')
    
    # 邮箱格式
    EMAIL_PATTERN = re.compile(r'^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$')
    
    # 手机号格式（中国大陆）
    PHONE_PATTERN = re.compile(r'^1[3-9]\d{9}$')
    
    # MD5格式
    MD5_PATTERN = re.compile(r'^[a-fA-F0-9]{32}$')
    
    @staticmethod
    def validate_id(value: str, field_name: str = "ID") -> str:
        """校验ID格式"""
        if not value:
            raise ValidationError(field_name, "不能为空")
        if not CommonValidator.ID_PATTERN.match(value):
            raise ValidationError(field_name, "格式不正确，只能包含字母、数字、下划线和连字符")
        return value
    
    @staticmethod
    def validate_username(value: str) -> str:
        """校验用户名格式"""
        if not value:
            raise ValidationError("username", "用户名不能为空")
        if not CommonValidator.USERNAME_PATTERN.match(value):
            raise ValidationError("username", "用户名只能包含字母、数字和下划线，长度3-50")
        return value
    
    @staticmethod
    def validate_email(value: Optional[str]) -> Optional[str]:
        """校验邮箱格式"""
        if not value:
            return value
        if not CommonValidator.EMAIL_PATTERN.match(value):
            raise ValidationError("email", "邮箱格式不正确")
        return value
    
    @staticmethod
    def validate_phone(value: Optional[str]) -> Optional[str]:
        """校验手机号格式"""
        if not value:
            return value
        if not CommonValidator.PHONE_PATTERN.match(value):
            raise ValidationError("phone", "手机号格式不正确")
        return value
    
    @staticmethod
    def validate_security_level(value: int, field_name: str = "security_level") -> int:
        """校验安全等级（1-4）"""
        if not isinstance(value, int):
            raise ValidationError(field_name, "安全等级必须是整数")
        if value < 1 or value > 4:
            raise ValidationError(field_name, "安全等级必须在1-4之间")
        return value
    
    @staticmethod
    def validate_url(value: Optional[str], field_name: str = "url") -> Optional[str]:
        """校验URL格式"""
        if not value:
            return value
        try:
            result = urlparse(value)
            if not result.scheme or not result.netloc:
                raise ValidationError(field_name, "URL格式不正确")
            if result.scheme not in ('http', 'https'):
                raise ValidationError(field_name, "URL只支持http/https协议")
        except Exception:
            raise ValidationError(field_name, "URL格式不正确")
        return value
    
    @staticmethod
    def validate_string_length(
        value: Optional[str],
        field_name: str,
        min_length: int = 0,
        max_length: int = 500,
    ) -> Optional[str]:
        """校验字符串长度"""
        if not value:
            if min_length > 0:
                raise ValidationError(field_name, f"不能为空")
            return value
        if len(value) < min_length:
            raise ValidationError(field_name, f"长度不能小于{min_length}")
        if len(value) > max_length:
            raise ValidationError(field_name, f"长度不能超过{max_length}")
        return value
    
    @staticmethod
    def validate_enum(
        value: str,
        allowed_values: List[str],
        field_name: str = "enum",
    ) -> str:
        """校验枚举值"""
        if not value:
            raise ValidationError(field_name, "不能为空")
        if value not in allowed_values:
            raise ValidationError(
                field_name, 
                f"值不合法，允许: {', '.join(allowed_values)}"
            )
        return value
    
    @staticmethod
    def validate_page_params(
        page_num: int = 1,
        page_size: int = 10,
    ) -> tuple:
        """校验分页参数"""
        if page_num < 1:
            raise ValidationError("page_num", "页码不能小于1")
        if page_size < 1 or page_size > 100:
            raise ValidationError("page_size", "每页数量必须在1-100之间")
        return page_num, page_size
    
    @staticmethod
    def validate_file_size(
        file_size: int,
        max_size_mb: int = 500,
    ) -> int:
        """校验文件大小"""
        if file_size < 0:
            raise ValidationError("file_size", "文件大小不能为负数")
        max_bytes = max_size_mb * 1024 * 1024
        if file_size > max_bytes:
            raise ValidationError("file_size", f"文件大小不能超过{max_size_mb}MB")
        return file_size
    
    @staticmethod
    def validate_document_type(doc_type: str) -> str:
        """校验文档类型"""
        allowed = ["PDF", "WORD", "EXCEL", "PPT", "TXT", "HTML", "MARKDOWN"]
        return CommonValidator.validate_enum(doc_type.upper(), allowed, "document_type")
    
    @staticmethod
    def sanitize_string(value: str) -> str:
        """清理字符串（去除首尾空格、特殊字符）"""
        if not value:
            return value
        # 去除首尾空格
        value = value.strip()
        # 去除控制字符
        value = re.sub(r'[\x00-\x08\x0b\x0c\x0e-\x1f\x7f]', '', value)
        return value


# 便捷函数
def validate_id(value: str, field_name: str = "ID") -> str:
    return CommonValidator.validate_id(value, field_name)

def validate_security_level(value: int) -> int:
    return CommonValidator.validate_security_level(value)

def validate_enum(value: str, allowed: List[str], field: str = "enum") -> str:
    return CommonValidator.validate_enum(value, allowed, field)
