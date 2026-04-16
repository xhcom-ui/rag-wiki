package com.zhiwiki.document.service;

import io.minio.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * MinIO文件存储服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MinIOService {

    private final MinioClient minioClient;
    private final com.zhiwiki.document.config.MinIOConfig minIOConfig;

    /**
     * 确保Bucket存在
     */
    public void ensureBucket() {
        try {
            String bucket = minIOConfig.getBucket();
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("创建MinIO Bucket: {}", bucket);
            }
        } catch (Exception e) {
            log.error("检查Bucket失败: {}", e.getMessage());
        }
    }

    /**
     * 上传文件
     */
    public String uploadFile(String objectName, MultipartFile file) {
        try {
            ensureBucket();
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(minIOConfig.getBucket())
                    .object(objectName)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build()
            );
            log.info("文件上传成功: {}", objectName);
            return objectName;
        } catch (Exception e) {
            log.error("文件上传失败: {}", e.getMessage());
            throw new RuntimeException("文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 下载文件
     */
    public InputStream downloadFile(String objectName) {
        try {
            return minioClient.getObject(
                GetObjectArgs.builder()
                    .bucket(minIOConfig.getBucket())
                    .object(objectName)
                    .build()
            );
        } catch (Exception e) {
            log.error("文件下载失败: {}", e.getMessage());
            throw new RuntimeException("文件下载失败: " + e.getMessage());
        }
    }

    /**
     * 获取预签名URL
     */
    public String getPresignedUrl(String objectName, int expiryMinutes) {
        try {
            return minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(minIOConfig.getBucket())
                    .object(objectName)
                    .expiry(expiryMinutes, TimeUnit.MINUTES)
                    .build()
            );
        } catch (Exception e) {
            log.error("获取预签名URL失败: {}", e.getMessage());
            throw new RuntimeException("获取预签名URL失败: " + e.getMessage());
        }
    }

    /**
     * 删除文件
     */
    public boolean deleteFile(String objectName) {
        try {
            minioClient.removeObject(
                RemoveObjectArgs.builder()
                    .bucket(minIOConfig.getBucket())
                    .object(objectName)
                    .build()
            );
            log.info("文件删除成功: {}", objectName);
            return true;
        } catch (Exception e) {
            log.error("文件删除失败: {}", e.getMessage());
            return false;
        }
    }
}
