package com.zhiwiki.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhiwiki.audit.entity.SecurityAlert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 安全告警Mapper
 */
@Mapper
public interface SecurityAlertMapper extends BaseMapper<SecurityAlert> {

    /**
     * 根据严重程度统计
     */
    @Select("SELECT severity, COUNT(*) as count FROM security_alert " +
            "WHERE is_deleted = 0 AND status != 'IGNORED' GROUP BY severity")
    List<Map<String, Object>> countBySeverity();

    /**
     * 根据状态统计
     */
    @Select("SELECT status, COUNT(*) as count FROM security_alert " +
            "WHERE is_deleted = 0 GROUP BY status")
    List<Map<String, Object>> countByStatus();

    /**
     * 查询未处理的告警
     */
    @Select("SELECT * FROM security_alert WHERE status IN ('NEW', 'CONFIRMED') " +
            "AND is_deleted = 0 ORDER BY created_at DESC LIMIT #{limit}")
    List<SecurityAlert> selectUnhandled(@Param("limit") int limit);
}