package com.zhiwiki.common.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 分页请求基类
 */
@Data
@Schema(description = "分页请求参数")
public class PageRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "当前页码", example = "1")
    private int pageNum = 1;

    @Schema(description = "每页条数", example = "10")
    private int pageSize = 10;

    @Schema(description = "排序字段")
    private String orderBy;

    @Schema(description = "排序方向 ASC/DESC", example = "DESC")
    private String orderDir = "DESC";

    public int getOffset() {
        return (pageNum - 1) * pageSize;
    }
}
