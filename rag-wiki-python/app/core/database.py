"""
数据库连接工具 - 安全的资源管理
"""
import logging
from contextlib import contextmanager
from typing import Generator
import mysql.connector
from mysql.connector import Error
from app.core.config import settings

logger = logging.getLogger(__name__)


@contextmanager
def get_db_cursor() -> Generator:
    """
    安全的数据库连接上下文管理器
    自动处理连接关闭和异常回滚
    
    Usage:
        with get_db_cursor() as (conn, cursor):
            cursor.execute("SELECT ...")
            conn.commit()  # 需要手动commit以保持灵活性
    """
    conn = None
    cursor = None
    try:
        conn = mysql.connector.connect(
            host=settings.MYSQL_HOST,
            port=settings.MYSQL_PORT,
            user=settings.MYSQL_USER,
            password=settings.MYSQL_PASSWORD,
            database=settings.MYSQL_DATABASE,
            charset='utf8mb4'
        )
        cursor = conn.cursor()
        yield conn, cursor
    except Error as e:
        logger.error(f"数据库操作失败: {e}")
        if conn:
            conn.rollback()
        raise
    finally:
        if cursor:
            cursor.close()
        if conn:
            conn.close()
