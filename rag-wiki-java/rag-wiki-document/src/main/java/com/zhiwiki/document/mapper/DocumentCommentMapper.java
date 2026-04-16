package com.zhiwiki.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiwiki.document.entity.DocumentComment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DocumentCommentMapper extends BaseMapper<DocumentComment> {
}
