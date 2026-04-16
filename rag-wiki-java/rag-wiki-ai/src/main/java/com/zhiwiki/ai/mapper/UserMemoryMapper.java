package com.zhiwiki.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiwiki.ai.entity.UserMemory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户长期记忆Mapper
 */
@Mapper
public interface UserMemoryMapper extends BaseMapper<UserMemory> {

    /**
     * 根据用户ID查询记忆列表
     */
    @Select("SELECT * FROM user_memory WHERE user_id = #{userId} AND is_deleted = 0 ORDER BY created_at DESC")
    List<UserMemory> selectByUserId(@Param("userId") String userId);

    /**
     * 根据记忆类型查询
     */
    @Select("SELECT * FROM user_memory WHERE user_id = #{userId} AND memory_type = #{memoryType} AND is_deleted = 0")
    List<UserMemory> selectByUserIdAndType(@Param("userId") String userId, @Param("memoryType") String memoryType);

    /**
     * 查询过期记忆
     */
    @Select("SELECT * FROM user_memory WHERE ttl > 0 AND ttl < UNIX_TIMESTAMP() AND is_deleted = 0")
    List<UserMemory> selectExpiredMemories();
}