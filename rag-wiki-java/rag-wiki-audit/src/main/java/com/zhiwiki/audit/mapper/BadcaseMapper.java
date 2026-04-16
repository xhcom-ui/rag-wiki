package com.zhiwiki.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiwiki.audit.entity.Badcase;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

/**
 * Badcase Mapper
 */
@Mapper
public interface BadcaseMapper extends BaseMapper<Badcase> {

    /**
     * 获取统计信息
     */
    @Select("SELECT " +
            "SUM(CASE WHEN status = 'NEW' THEN 1 ELSE 0 END) as pending, " +
            "SUM(CASE WHEN status = 'IN_REVIEW' THEN 1 ELSE 0 END) as inReview, " +
            "SUM(CASE WHEN status = 'FIXED' THEN 1 ELSE 0 END) as fixed, " +
            "SUM(CASE WHEN status = 'WONT_FIX' THEN 1 ELSE 0 END) as wontFix, " +
            "SUM(CASE WHEN created_at >= DATE_SUB(NOW(), INTERVAL 7 DAY) THEN 1 ELSE 0 END) as weeklyNew " +
            "FROM rag_badcase")
    Map<String, Object> selectStats();
}