package com.zhiwiki.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiwiki.ai.entity.SessionMemory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 会话短期记忆Mapper
 */
@Mapper
public interface SessionMemoryMapper extends BaseMapper<SessionMemory> {

    /**
     * 根据会话ID查询消息列表
     */
    @Select("SELECT * FROM session_memory WHERE session_id = #{sessionId} AND is_deleted = 0 ORDER BY message_index ASC")
    List<SessionMemory> selectBySessionId(@Param("sessionId") String sessionId);

    /**
     * 查询用户的最近会话
     */
    @Select("SELECT DISTINCT session_id FROM session_memory WHERE user_id = #{userId} AND is_deleted = 0 ORDER BY created_at DESC LIMIT #{limit}")
    List<String> selectRecentSessions(@Param("userId") String userId, @Param("limit") int limit);

    /**
     * 获取会话最新消息序号
     */
    @Select("SELECT COALESCE(MAX(message_index), 0) FROM session_memory WHERE session_id = #{sessionId}")
    int getMaxMessageIndex(@Param("sessionId") String sessionId);
}