package com.zhiwiki.document.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiwiki.common.constant.SystemConstants;
import com.zhiwiki.common.exception.BusinessException;
import com.zhiwiki.common.model.PageRequest;
import com.zhiwiki.common.model.PageResult;
import com.zhiwiki.common.model.ResultCode;
import com.zhiwiki.document.entity.DocumentInfo;
import com.zhiwiki.document.entity.DocumentParseTask;
import com.zhiwiki.document.entity.KnowledgeSpace;
import com.zhiwiki.document.feign.AiServiceClient;
import com.zhiwiki.document.mapper.DocumentInfoMapper;
import com.zhiwiki.document.mapper.DocumentParseTaskMapper;
import com.zhiwiki.document.mapper.KnowledgeSpaceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final KnowledgeSpaceMapper spaceMapper;
    private final DocumentInfoMapper documentInfoMapper;
    private final DocumentParseTaskMapper parseTaskMapper;
    private final AiServiceClient aiServiceClient;
    private final RabbitTemplate rabbitTemplate;

    // ========== 知识库空间管理 ==========

    public KnowledgeSpace createSpace(KnowledgeSpace space) {
        space.setSpaceId(IdUtil.fastSimpleUUID());
        spaceMapper.insert(space);
        return space;
    }

    public KnowledgeSpace getSpace(String spaceId) {
        KnowledgeSpace space = spaceMapper.selectOne(
                new LambdaQueryWrapper<KnowledgeSpace>().eq(KnowledgeSpace::getSpaceId, spaceId)
        );
        if (space == null) {
            throw new BusinessException(ResultCode.DOC_SPACE_NOT_FOUND);
        }
        return space;
    }

    public PageResult<KnowledgeSpace> pageSpaces(PageRequest pageRequest) {
        Page<KnowledgeSpace> page = new Page<>(pageRequest.getPageNum(), pageRequest.getPageSize());
        Page<KnowledgeSpace> result = spaceMapper.selectPage(page,
                new LambdaQueryWrapper<KnowledgeSpace>().orderByDesc(KnowledgeSpace::getCreatedAt));
        return PageResult.of(result.getRecords(), result.getTotal(), pageRequest.getPageNum(), pageRequest.getPageSize());
    }

    // ========== 文档管理 ==========

    @Transactional
    public DocumentInfo createDocument(DocumentInfo document) {
        document.setDocumentId(IdUtil.fastSimpleUUID());
        document.setVersion("1.0.0");
        document.setStatus(SystemConstants.DOC_STATUS_DRAFT);
        documentInfoMapper.insert(document);
        return document;
    }

    public DocumentInfo getDocument(String documentId) {
        DocumentInfo doc = documentInfoMapper.selectOne(
                new LambdaQueryWrapper<DocumentInfo>().eq(DocumentInfo::getDocumentId, documentId)
        );
        if (doc == null) {
            throw new BusinessException(ResultCode.DOC_NOT_FOUND);
        }
        return doc;
    }

    public PageResult<DocumentInfo> pageDocuments(PageRequest pageRequest, String spaceId, String parentId) {
        Page<DocumentInfo> page = new Page<>(pageRequest.getPageNum(), pageRequest.getPageSize());
        LambdaQueryWrapper<DocumentInfo> wrapper = new LambdaQueryWrapper<>();
        if (spaceId != null) {
            wrapper.eq(DocumentInfo::getSpaceId, spaceId);
        }
        if (parentId != null) {
            wrapper.eq(DocumentInfo::getParentId, parentId);
        }
        wrapper.orderByDesc(DocumentInfo::getCreatedAt);
        Page<DocumentInfo> result = documentInfoMapper.selectPage(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), pageRequest.getPageNum(), pageRequest.getPageSize());
    }

    @Transactional
    public DocumentInfo updateDocument(DocumentInfo document) {
        documentInfoMapper.updateById(document);
        return document;
    }

    public void deleteDocument(Long id) {
        documentInfoMapper.deleteById(id);
    }

    /**
     * 上传文档并提交解析任务
     */
    @Transactional
    public DocumentParseTask uploadAndParse(String spaceId, String fileName, String fileType,
                                             String filePath, String creatorId, Integer securityLevel) {
        // 1. 创建文档记录
        DocumentInfo doc = new DocumentInfo();
        doc.setDocumentId(IdUtil.fastSimpleUUID());
        doc.setSpaceId(spaceId);
        doc.setDocumentName(fileName);
        doc.setDocumentType(fileType);
        doc.setFilePath(filePath);
        doc.setCreatorId(creatorId);
        doc.setSecurityLevel(securityLevel);
        doc.setStatus(SystemConstants.DOC_STATUS_DRAFT);
        documentInfoMapper.insert(doc);

        // 2. 创建解析任务
        DocumentParseTask task = new DocumentParseTask();
        task.setTaskId(IdUtil.fastSimpleUUID());
        task.setDocumentId(doc.getDocumentId());
        task.setFileName(fileName);
        task.setFileType(fileType);
        task.setSpaceId(spaceId);
        task.setStatus(SystemConstants.PARSE_STATUS_PENDING);
        parseTaskMapper.insert(task);

        // 3. 发送MQ消息触发异步解析
        Map<String, Object> message = new HashMap<>();
        message.put("taskId", task.getTaskId());
        message.put("documentId", doc.getDocumentId());
        message.put("filePath", filePath);
        message.put("fileType", fileType);
        message.put("spaceId", spaceId);
        message.put("securityLevel", securityLevel);
        rabbitTemplate.convertAndSend(SystemConstants.MQ_DOCUMENT_EXCHANGE,
                "document.parse", message);

        return task;
    }
}
