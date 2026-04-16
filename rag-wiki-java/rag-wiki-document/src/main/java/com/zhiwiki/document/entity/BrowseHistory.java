package com.zhiwiki.document.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zhiwiki.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("browse_history")
public class BrowseHistory extends BaseEntity {

    @TableField("user_id")
    private String userId;

    @TableField("document_id")
    private String documentId;

    @TableField("browse_count")
    private Integer browseCount;
}
