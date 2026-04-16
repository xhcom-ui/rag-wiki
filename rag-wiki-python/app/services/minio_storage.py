"""
MinIO文件存储服务 - 文件上传/下载/删除/预签名URL
"""
import logging
import io
from typing import Optional, BinaryIO
from datetime import timedelta

from app.core.config import settings

logger = logging.getLogger(__name__)


class MinIOStorageService:
    """MinIO对象存储服务"""

    def __init__(self):
        self._client = None
        self._bucket = settings.MINIO_BUCKET

    def _get_client(self):
        """延迟初始化MinIO客户端"""
        if self._client is None:
            try:
                from minio import Minio
                self._client = Minio(
                    endpoint=settings.MINIO_ENDPOINT,
                    access_key=settings.MINIO_ACCESS_KEY,
                    secret_key=settings.MINIO_SECRET_KEY,
                    secure=settings.MINIO_SECURE,
                )
                # 确保bucket存在
                if not self._client.bucket_exists(self._bucket):
                    self._client.make_bucket(self._bucket)
                    logger.info(f"创建MinIO Bucket: {self._bucket}")
                logger.info(f"MinIO连接成功: {settings.MINIO_ENDPOINT}")
            except ImportError:
                raise ImportError("minio未安装，请执行: pip install minio")
        return self._client

    def upload_file(
        self,
        object_name: str,
        data: BinaryIO,
        length: int,
        content_type: str = "application/octet-stream",
    ) -> str:
        """
        上传文件到MinIO

        Args:
            object_name: 对象路径 (如: documents/space_id/doc_id/file.pdf)
            data: 文件数据流
            length: 数据长度
            content_type: MIME类型

        Returns:
            对象存储路径
        """
        client = self._get_client()
        client.put_object(
            bucket_name=self._bucket,
            object_name=object_name,
            data=data,
            length=length,
            content_type=content_type,
        )
        logger.info(f"文件上传成功: {object_name}")
        return object_name

    def upload_bytes(
        self,
        object_name: str,
        data: bytes,
        content_type: str = "application/octet-stream",
    ) -> str:
        """上传字节数据"""
        return self.upload_file(
            object_name=object_name,
            data=io.BytesIO(data),
            length=len(data),
            content_type=content_type,
        )

    def download_file(self, object_name: str) -> bytes:
        """
        下载文件

        Returns:
            文件字节数据
        """
        client = self._get_client()
        response = client.get_object(
            bucket_name=self._bucket,
            object_name=object_name,
        )
        try:
            return response.read()
        finally:
            response.close()
            response.release_conn()

    def get_presigned_url(self, object_name: str, expires: int = 3600) -> str:
        """
        获取预签名URL

        Args:
            object_name: 对象路径
            expires: 过期时间(秒)

        Returns:
            预签名URL
        """
        client = self._get_client()
        url = client.presigned_get_object(
            bucket_name=self._bucket,
            object_name=object_name,
            expires=timedelta(seconds=expires),
        )
        return url

    def delete_file(self, object_name: str) -> bool:
        """删除文件"""
        client = self._get_client()
        client.remove_object(
            bucket_name=self._bucket,
            object_name=object_name,
        )
        logger.info(f"文件删除成功: {object_name}")
        return True

    def delete_files_by_prefix(self, prefix: str) -> int:
        """
        按前缀批量删除文件

        Returns:
            删除的文件数量
        """
        client = self._get_client()
        objects = client.list_objects(
            bucket_name=self._bucket,
            prefix=prefix,
            recursive=True,
        )
        count = 0
        for obj in objects:
            client.remove_object(self._bucket, obj.object_name)
            count += 1
        logger.info(f"批量删除完成: prefix={prefix}, count={count}")
        return count

    def file_exists(self, object_name: str) -> bool:
        """检查文件是否存在"""
        client = self._get_client()
        try:
            client.stat_object(self._bucket, object_name)
            return True
        except Exception:
            return False

    def get_file_info(self, object_name: str) -> Optional[dict]:
        """获取文件元信息"""
        client = self._get_client()
        try:
            stat = client.stat_object(self._bucket, object_name)
            return {
                "object_name": stat.object_name,
                "size": stat.size,
                "content_type": stat.content_type,
                "last_modified": stat.last_modified.isoformat() if stat.last_modified else None,
                "etag": stat.etag,
            }
        except Exception:
            return None


# 全局服务实例
minio_service = MinIOStorageService()
