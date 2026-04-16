package com.zhiwiki.document.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiwiki.common.model.PageRequest;
import com.zhiwiki.common.model.PageResult;
import com.zhiwiki.common.model.Result;
import com.zhiwiki.document.entity.DocumentInfo;
import com.zhiwiki.document.entity.DocumentParseTask;
import com.zhiwiki.document.entity.DocumentPermission;
import com.zhiwiki.document.entity.DocumentVersion;
import com.zhiwiki.document.mapper.DocumentPermissionMapper;
import com.zhiwiki.document.mapper.DocumentVersionMapper;
import com.zhiwiki.document.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/document")
@RequiredArgsConstructor
@Tag(name = "文档管理", description = "文档CRUD、版本管理、文件上传")
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentVersionMapper versionMapper;
    private final DocumentPermissionMapper permissionMapper;

    @PostMapping
    @Operation(summary = "创建文档")
    public Result<DocumentInfo> create(@RequestBody DocumentInfo document) {
        return Result.success(documentService.createDocument(document));
    }

    @GetMapping("/{documentId}")
    @Operation(summary = "查询文档详情")
    public Result<DocumentInfo> get(@PathVariable String documentId) {
        return Result.success(documentService.getDocument(documentId));
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询文档")
    public Result<PageResult<DocumentInfo>> page(PageRequest pageRequest,
                                                  @RequestParam(required = false) String spaceId,
                                                  @RequestParam(required = false) String parentId) {
        return Result.success(documentService.pageDocuments(pageRequest, spaceId, parentId));
    }

    @PutMapping
    @Operation(summary = "更新文档")
    public Result<DocumentInfo> update(@RequestBody DocumentInfo document) {
        return Result.success(documentService.updateDocument(document));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除文档")
    public Result<Void> delete(@PathVariable Long id) {
        documentService.deleteDocument(id);
        return Result.success();
    }

    @PostMapping("/upload")
    @Operation(summary = "上传文档并提交解析")
    public Result<DocumentParseTask> uploadAndParse(
            @RequestParam String spaceId,
            @RequestParam String fileName,
            @RequestParam String fileType,
            @RequestParam String filePath,
            @RequestParam String creatorId,
            @RequestParam(defaultValue = "1") Integer securityLevel) {
        return Result.success(documentService.uploadAndParse(spaceId, fileName, fileType, filePath, creatorId, securityLevel));
    }

    // ==================== 版本历史 ====================

    @GetMapping("/{documentId}/versions")
    @Operation(summary = "获取文档版本历史")
    public Result<List<DocumentVersion>> getVersions(@PathVariable String documentId) {
        List<DocumentVersion> versions = versionMapper.selectList(
                new LambdaQueryWrapper<DocumentVersion>()
                        .eq(DocumentVersion::getDocumentId, documentId)
                        .orderByDesc(DocumentVersion::getCreatedAt));
        return Result.success(versions);
    }

    // ==================== 权限管理 ====================

    @GetMapping("/{documentId}/permissions")
    @Operation(summary = "获取文档权限列表")
    public Result<List<DocumentPermission>> getPermissions(@PathVariable String documentId) {
        List<DocumentPermission> permissions = permissionMapper.selectList(
                new LambdaQueryWrapper<DocumentPermission>()
                        .eq(DocumentPermission::getDocumentId, documentId)
                        .orderByDesc(DocumentPermission::getCreatedAt));
        return Result.success(permissions);
    }

    @PostMapping("/permission")
    @Operation(summary = "添加文档权限")
    public Result<DocumentPermission> addPermission(@RequestBody DocumentPermission permission) {
        permissionMapper.insert(permission);
        return Result.success(permission);
    }

    @DeleteMapping("/permission/{id}")
    @Operation(summary = "删除文档权限")
    public Result<Void> removePermission(@PathVariable Long id) {
        permissionMapper.deleteById(id);
        return Result.success();
    }
}
