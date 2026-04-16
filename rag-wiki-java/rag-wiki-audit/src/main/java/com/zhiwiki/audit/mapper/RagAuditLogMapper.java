package com.zhiwiki.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiwiki.audit.entity.RagAuditLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RagAuditLogMapper extends BaseMapper<RagAuditLog> {
}
