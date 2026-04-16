package com.zhiwiki.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiwiki.auth.entity.UserApiKey;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户API密钥Mapper
 */
@Mapper
public interface UserApiKeyMapper extends BaseMapper<UserApiKey> {
}
