package com.zhiwiki.common.tenant;

/**
 * 租户隔离级别枚举
 * 
 * 三种隔离粒度，安全等级递增：
 * 1. METADATA_FILTER - 元数据过滤（低安全）：所有租户共享表/集合，通过tenant_id字段过滤
 * 2. PARTITION - 分区隔离（中安全）：同一表/集合下，每个租户独立Partition
 * 3. COLLECTION - 集合/库隔离（高安全）：每个租户独立的向量Collection/MySQL数据库
 */
public enum TenantIsolationLevel {

    /**
     * 元数据过滤隔离
     * 适用场景：中小企业、集团内非敏感业务部门
     * 实现：所有SQL自动追加 tenant_id = ? 条件
     */
    METADATA_FILTER("metadata_filter", "元数据过滤", 1),

    /**
     * 分区隔离
     * 适用场景：中大型企业、多分支机构
     * 实现：MySQL表分区 / 向量库Partition
     */
    PARTITION("partition", "分区隔离", 2),

    /**
     * 集合/库隔离
     * 适用场景：金融、政府等强监管行业、SaaS平台
     * 实现：独立数据库Schema / 独立向量Collection
     */
    COLLECTION("collection", "集合隔离", 3);

    private final String code;
    private final String description;
    private final int securityLevel;

    TenantIsolationLevel(String code, String description, int securityLevel) {
        this.code = code;
        this.description = description;
        this.securityLevel = securityLevel;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public int getSecurityLevel() {
        return securityLevel;
    }

    /**
     * 根据code获取枚举
     */
    public static TenantIsolationLevel fromCode(String code) {
        for (TenantIsolationLevel level : values()) {
            if (level.code.equals(code)) {
                return level;
            }
        }
        return METADATA_FILTER; // 默认元数据过滤
    }
}
