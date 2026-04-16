package com.zhiwiki.document.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zhiwiki.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("document_comment")
public class DocumentComment extends BaseEntity {

    @TableField("comment_id")
    private String commentId;

    @TableField("user_id")
    private String userId;

    @TableField("document_id")
    private String documentId;

    @TableField("content")
    private String content;

    @TableField("parent_id")
    private Long parentId;

    @TableField("is_deleted")
    private Integer isDeleted;
}
