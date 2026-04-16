package com.zhiwiki.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiwiki.auth.entity.Tenant;
import org.apache.ibatis.annotations.Mapper;

/**
 * 租户Mapper
 */
@Mapper
public interface TenantMapper extends BaseMapper<Tenant> {
}
