package com.zhiwiki.document.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiwiki.document.entity.DocumentComment;
import com.zhiwiki.document.entity.DocumentFavorite;
import com.zhiwiki.document.entity.DocumentLike;
import com.zhiwiki.document.mapper.DocumentCommentMapper;
import com.zhiwiki.document.mapper.DocumentFavoriteMapper;
import com.zhiwiki.document.mapper.DocumentLikeMapper;
import com.zhiwiki.document.mapper.BrowseHistoryMapper;
import com.zhiwiki.document.entity.BrowseHistory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 文档互动服务 - 收藏/点赞/评论/浏览历史
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentInteractionService {

    private final DocumentFavoriteMapper favoriteMapper;
    private final DocumentLikeMapper likeMapper;
    private final DocumentCommentMapper commentMapper;
    private final BrowseHistoryMapper browseHistoryMapper;

    // ==================== 收藏 ====================

    public boolean toggleFavorite(String userId, String documentId) {
        DocumentFavorite existing = favoriteMapper.selectOne(
                new LambdaQueryWrapper<DocumentFavorite>()
                        .eq(DocumentFavorite::getUserId, userId)
                        .eq(DocumentFavorite::getDocumentId, documentId));
        if (existing != null) {
            favoriteMapper.deleteById(existing.getId());
            log.info("取消收藏: userId={}, documentId={}", userId, documentId);
            return false;
        } else {
            DocumentFavorite fav = new DocumentFavorite();
            fav.setId(IdUtil.getSnowflakeNextId());
            fav.setUserId(userId);
            fav.setDocumentId(documentId);
            favoriteMapper.insert(fav);
            log.info("添加收藏: userId={}, documentId={}", userId, documentId);
            return true;
        }
    }

    public boolean isFavorited(String userId, String documentId) {
        return favoriteMapper.selectOne(
                new LambdaQueryWrapper<DocumentFavorite>()
                        .eq(DocumentFavorite::getUserId, userId)
                        .eq(DocumentFavorite::getDocumentId, documentId)) != null;
    }

    public List<DocumentFavorite> getUserFavorites(String userId) {
        return favoriteMapper.selectList(
                new LambdaQueryWrapper<DocumentFavorite>()
                        .eq(DocumentFavorite::getUserId, userId)
                        .orderByDesc(DocumentFavorite::getCreatedAt));
    }

    // ==================== 点赞 ====================

    public boolean toggleLike(String userId, String documentId) {
        DocumentLike existing = likeMapper.selectOne(
                new LambdaQueryWrapper<DocumentLike>()
                        .eq(DocumentLike::getUserId, userId)
                        .eq(DocumentLike::getDocumentId, documentId));
        if (existing != null) {
            likeMapper.deleteById(existing.getId());
            log.info("取消点赞: userId={}, documentId={}", userId, documentId);
            return false;
        } else {
            DocumentLike like = new DocumentLike();
            like.setId(IdUtil.getSnowflakeNextId());
            like.setUserId(userId);
            like.setDocumentId(documentId);
            likeMapper.insert(like);
            log.info("添加点赞: userId={}, documentId={}", userId, documentId);
            return true;
        }
    }

    public long getLikeCount(String documentId) {
        return likeMapper.selectCount(
                new LambdaQueryWrapper<DocumentLike>()
                        .eq(DocumentLike::getDocumentId, documentId));
    }

    public boolean isLiked(String userId, String documentId) {
        return likeMapper.selectOne(
                new LambdaQueryWrapper<DocumentLike>()
                        .eq(DocumentLike::getUserId, userId)
                        .eq(DocumentLike::getDocumentId, documentId)) != null;
    }

    // ==================== 评论 ====================

    public DocumentComment addComment(String userId, String documentId, String content, Long parentId) {
        DocumentComment comment = new DocumentComment();
        comment.setId(IdUtil.getSnowflakeNextId());
        comment.setCommentId(IdUtil.fastSimpleUUID());
        comment.setUserId(userId);
        comment.setDocumentId(documentId);
        comment.setContent(content);
        comment.setParentId(parentId);
        comment.setIsDeleted(0);
        commentMapper.insert(comment);
        log.info("添加评论: userId={}, documentId={}", userId, documentId);
        return comment;
    }

    public List<DocumentComment> getDocumentComments(String documentId) {
        return commentMapper.selectList(
                new LambdaQueryWrapper<DocumentComment>()
                        .eq(DocumentComment::getDocumentId, documentId)
                        .eq(DocumentComment::getIsDeleted, 0)
                        .orderByAsc(DocumentComment::getCreatedAt));
    }

    public void deleteComment(String commentId, String userId) {
        DocumentComment comment = commentMapper.selectOne(
                new LambdaQueryWrapper<DocumentComment>()
                        .eq(DocumentComment::getCommentId, commentId)
                        .eq(DocumentComment::getUserId, userId));
        if (comment != null) {
            comment.setIsDeleted(1);
            commentMapper.updateById(comment);
        }
    }

    // ==================== 浏览历史 ====================

    public void recordBrowse(String userId, String documentId) {
        // 去重：同一天同一文档只记录一次
        BrowseHistory existing = browseHistoryMapper.selectOne(
                new LambdaQueryWrapper<BrowseHistory>()
                        .eq(BrowseHistory::getUserId, userId)
                        .eq(BrowseHistory::getDocumentId, documentId)
                        .orderByDesc(BrowseHistory::getCreatedAt)
                        .last("LIMIT 1"));
        if (existing != null) {
            // 更新浏览时间
            existing.setBrowseCount(existing.getBrowseCount() + 1);
            browseHistoryMapper.updateById(existing);
        } else {
            BrowseHistory history = new BrowseHistory();
            history.setId(IdUtil.getSnowflakeNextId());
            history.setUserId(userId);
            history.setDocumentId(documentId);
            history.setBrowseCount(1);
            browseHistoryMapper.insert(history);
        }
    }

    public List<BrowseHistory> getUserBrowseHistory(String userId, int limit) {
        return browseHistoryMapper.selectList(
                new LambdaQueryWrapper<BrowseHistory>()
                        .eq(BrowseHistory::getUserId, userId)
                        .orderByDesc(BrowseHistory::getCreatedAt)
                        .last("LIMIT " + limit));
    }

    public void clearBrowseHistory(String userId) {
        browseHistoryMapper.delete(
                new LambdaQueryWrapper<BrowseHistory>()
                        .eq(BrowseHistory::getUserId, userId));
    }
}
