package com.zhiwiki.document.controller;

import com.zhiwiki.common.model.Result;
import com.zhiwiki.document.entity.BrowseHistory;
import com.zhiwiki.document.entity.DocumentComment;
import com.zhiwiki.document.entity.DocumentFavorite;
import com.zhiwiki.document.service.DocumentInteractionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 文档互动控制器 - 收藏/点赞/评论/浏览历史
 */
@RestController
@RequestMapping("/api/document/interaction")
@RequiredArgsConstructor
@Tag(name = "文档互动", description = "收藏、点赞、评论、浏览历史")
public class DocumentInteractionController {

    private final DocumentInteractionService interactionService;

    // ==================== 收藏 ====================

    @PostMapping("/favorite/{documentId}")
    @Operation(summary = "切换收藏状态")
    public Result<Map<String, Object>> toggleFavorite(@PathVariable String documentId) {
        String userId = cn.dev33.satoken.stp.StpUtil.getLoginIdAsString();
        boolean favorited = interactionService.toggleFavorite(userId, documentId);
        return Result.success(Map.of("favorited", favorited));
    }

    @GetMapping("/favorite/check/{documentId}")
    @Operation(summary = "检查是否已收藏")
    public Result<Map<String, Object>> checkFavorite(@PathVariable String documentId) {
        String userId = cn.dev33.satoken.stp.StpUtil.getLoginIdAsString();
        return Result.success(Map.of("favorited", interactionService.isFavorited(userId, documentId)));
    }

    @GetMapping("/favorite/list")
    @Operation(summary = "获取我的收藏列表")
    public Result<List<DocumentFavorite>> getMyFavorites() {
        String userId = cn.dev33.satoken.stp.StpUtil.getLoginIdAsString();
        return Result.success(interactionService.getUserFavorites(userId));
    }

    // ==================== 点赞 ====================

    @PostMapping("/like/{documentId}")
    @Operation(summary = "切换点赞状态")
    public Result<Map<String, Object>> toggleLike(@PathVariable String documentId) {
        String userId = cn.dev33.satoken.stp.StpUtil.getLoginIdAsString();
        boolean liked = interactionService.toggleLike(userId, documentId);
        long count = interactionService.getLikeCount(documentId);
        return Result.success(Map.of("liked", liked, "likeCount", count));
    }

    @GetMapping("/like/count/{documentId}")
    @Operation(summary = "获取点赞数")
    public Result<Map<String, Object>> getLikeCount(@PathVariable String documentId) {
        String userId = cn.dev33.satoken.stp.StpUtil.getLoginIdAsString();
        return Result.success(Map.of(
                "likeCount", interactionService.getLikeCount(documentId),
                "liked", interactionService.isLiked(userId, documentId)));
    }

    // ==================== 评论 ====================

    @PostMapping("/comment")
    @Operation(summary = "添加评论")
    public Result<DocumentComment> addComment(
            @RequestParam String documentId,
            @RequestParam String content,
            @RequestParam(required = false) Long parentId) {
        String userId = cn.dev33.satoken.stp.StpUtil.getLoginIdAsString();
        return Result.success(interactionService.addComment(userId, documentId, content, parentId));
    }

    @GetMapping("/comment/{documentId}")
    @Operation(summary = "获取文档评论列表")
    public Result<List<DocumentComment>> getComments(@PathVariable String documentId) {
        return Result.success(interactionService.getDocumentComments(documentId));
    }

    @DeleteMapping("/comment/{commentId}")
    @Operation(summary = "删除评论")
    public Result<Void> deleteComment(@PathVariable String commentId) {
        String userId = cn.dev33.satoken.stp.StpUtil.getLoginIdAsString();
        interactionService.deleteComment(commentId, userId);
        return Result.success();
    }

    // ==================== 浏览历史 ====================

    @PostMapping("/browse/{documentId}")
    @Operation(summary = "记录浏览历史")
    public Result<Void> recordBrowse(@PathVariable String documentId) {
        String userId = cn.dev33.satoken.stp.StpUtil.getLoginIdAsString();
        interactionService.recordBrowse(userId, documentId);
        return Result.success();
    }

    @GetMapping("/browse/history")
    @Operation(summary = "获取我的浏览历史")
    public Result<List<BrowseHistory>> getBrowseHistory(
            @RequestParam(defaultValue = "20") int limit) {
        String userId = cn.dev33.satoken.stp.StpUtil.getLoginIdAsString();
        return Result.success(interactionService.getUserBrowseHistory(userId, limit));
    }

    @DeleteMapping("/browse/history")
    @Operation(summary = "清空浏览历史")
    public Result<Void> clearBrowseHistory() {
        String userId = cn.dev33.satoken.stp.StpUtil.getLoginIdAsString();
        interactionService.clearBrowseHistory(userId);
        return Result.success();
    }
}
