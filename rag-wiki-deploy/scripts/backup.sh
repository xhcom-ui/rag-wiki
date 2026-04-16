#!/bin/bash
# ============================================================
# RagWiki 数据库备份与灾难恢复脚本
# ============================================================
# 
# 使用方式:
#   ./backup.sh              # 执行全量备份
#   ./backup.sh restore 20240115_120000  # 从指定备份恢复
#   ./backup.sh list         # 列出所有备份
#   ./backup.sh cleanup      # 清理过期备份
#
# 自动备份: 添加到 crontab
#   0 2 * * * /opt/rag-wiki/backup.sh >> /var/log/rag-wiki-backup.log 2>&1

set -euo pipefail

# ==================== 配置 ====================
BACKUP_DIR="/opt/rag-wiki/backups"
MYSQL_HOST="${MYSQL_HOST:-localhost}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-}"
MYSQL_DATABASE="rag_wiki"

REDIS_HOST="${REDIS_HOST:-localhost}"
REDIS_PORT="${REDIS_PORT:-6379}"
REDIS_PASSWORD="${REDIS_PASSWORD:-}"

ES_HOST="${ES_HOST:-localhost}"
ES_PORT="${ES_PORT:-9200}"

# 保留策略
RETENTION_DAYS=30        # 保留30天备份
RETENTION_WEEKLY=8       # 保留8个周备份
RETENTION_MONTHLY=6      # 保留6个月备份

# ==================== 工具函数 ====================
log() { echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*"; }
error() { log "ERROR: $*" >&2; exit 1; }

TIMESTAMP=$(date '+%Y%m%d_%H%M%S')
BACKUP_PATH="${BACKUP_DIR}/${TIMESTAMP}"
mkdir -p "${BACKUP_PATH}"

# ==================== MySQL 备份 ====================
backup_mysql() {
    log "开始MySQL备份..."
    local dump_file="${BACKUP_PATH}/mysql_full.sql.gz"
    
    mysqldump \
        -h"${MYSQL_HOST}" \
        -P"${MYSQL_PORT}" \
        -u"${MYSQL_USER}" \
        ${MYSQL_PASSWORD:+-p"${MYSQL_PASSWORD}"} \
        --single-transaction \
        --routines \
        --triggers \
        --events \
        --set-gtid-purged=OFF \
        "${MYSQL_DATABASE}" | gzip > "${dump_file}"
    
    local size=$(du -h "${dump_file}" | cut -f1)
    log "MySQL备份完成: ${dump_file} (${size})"
}

# ==================== Redis 备份 ====================
backup_redis() {
    log "开始Redis备份..."
    
    # 触发BGSAVE
    redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" ${REDIS_PASSWORD:+-a "${REDIS_PASSWORD}"} BGSAVE
    
    # 等待BGSAVE完成
    local retries=30
    while [ $retries -gt 0 ]; do
        local status=$(redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" ${REDIS_PASSWORD:+-a "${REDIS_PASSWORD}"} LASTSAVE)
        sleep 2
        retries=$((retries - 1))
        # 简单等待
        if [ $retries -lt 25 ]; then break; fi
    done
    
    # 复制RDB文件
    local rdb_file="${BACKUP_PATH}/redis_dump.rdb"
    redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" ${REDIS_PASSWORD:+-a "${REDIS_PASSWORD}"} \
        --rdb "${rdb_file}" || true
    
    if [ -f "${rdb_file}" ]; then
        gzip "${rdb_file}"
        log "Redis备份完成: ${rdb_file}.gz"
    else
        log "Redis备份: RDB文件未生成（可能Redis为空或无权限）"
    fi
}

# ==================== Elasticsearch 备份 ====================
backup_elasticsearch() {
    log "开始Elasticsearch备份..."
    
    local snapshot_name="snapshot_${TIMESTAMP}"
    local repo_path="/opt/elasticsearch/backups"
    
    # 创建快照仓库（如果不存在）
    curl -s -X PUT "http://${ES_HOST}:${ES_PORT}/_snapshot/rag_wiki_backup" \
        -H 'Content-Type: application/json' \
        -d "{\"type\": \"fs\", \"settings\": {\"location\": \"${repo_path}\"}}" || true
    
    # 创建快照
    curl -s -X PUT "http://${ES_HOST}:${ES_PORT}/_snapshot/rag_wiki_backup/${snapshot_name}?wait_for_completion=true" \
        -H 'Content-Type: application/json' \
        -d '{"indices": "rag-wiki-*", "ignore_unavailable": true, "include_global_state": false}'
    
    log "Elasticsearch快照创建完成: ${snapshot_name}"
}

# ==================== 备份元信息 ====================
save_metadata() {
    cat > "${BACKUP_PATH}/metadata.json" << EOF
{
    "timestamp": "${TIMESTAMP}",
    "date": "$(date '+%Y-%m-%d %H:%M:%S')",
    "type": "full",
    "mysql_database": "${MYSQL_DATABASE}",
    "components": ["mysql", "redis", "elasticsearch"],
    "retention_days": ${RETENTION_DAYS}
}
EOF
    log "备份元信息已保存"
}

# ==================== 恢复 ====================
restore() {
    local backup_name="$1"
    local restore_path="${BACKUP_DIR}/${backup_name}"
    
    if [ ! -d "${restore_path}" ]; then
        error "备份不存在: ${restore_path}"
    fi
    
    log "===== 开始恢复: ${backup_name} ====="
    
    # 恢复MySQL
    if [ -f "${restore_path}/mysql_full.sql.gz" ]; then
        log "恢复MySQL..."
        gunzip -c "${restore_path}/mysql_full.sql.gz" | mysql \
            -h"${MYSQL_HOST}" \
            -P"${MYSQL_PORT}" \
            -u"${MYSQL_USER}" \
            ${MYSQL_PASSWORD:+-p"${MYSQL_PASSWORD}"} \
            "${MYSQL_DATABASE}"
        log "MySQL恢复完成"
    fi
    
    # 恢复Redis
    if [ -f "${restore_path}/redis_dump.rdb.gz" ]; then
        log "恢复Redis..."
        gunzip "${restore_path}/redis_dump.rdb.gz"
        cp "${restore_path}/redis_dump.rdb" /var/lib/redis/dump.rdb
        log "Redis恢复完成（需重启Redis服务）"
    fi
    
    # 恢复Elasticsearch
    local snapshot_name="snapshot_${backup_name}"
    curl -s -X POST "http://${ES_HOST}:${ES_PORT}/_snapshot/rag_wiki_backup/${snapshot_name}/_restore" \
        -H 'Content-Type: application/json' \
        -d '{"indices": "rag-wiki-*", "ignore_unavailable": true}' || true
    log "Elasticsearch恢复已触发"
    
    log "===== 恢复完成: ${backup_name} ====="
}

# ==================== 清理过期备份 ====================
cleanup() {
    log "清理过期备份（保留${RETENTION_DAYS}天）..."
    find "${BACKUP_DIR}" -maxdepth 1 -type d -mtime +${RETENTION_DAYS} -exec rm -rf {} +
    log "清理完成"
}

# ==================== 列出备份 ====================
list_backups() {
    log "现有备份列表:"
    for dir in "${BACKUP_DIR}"/*/; do
        if [ -f "${dir}/metadata.json" ]; then
            local name=$(basename "${dir}")
            local size=$(du -sh "${dir}" | cut -f1)
            local date=$(python3 -c "import json; print(json.load(open('${dir}metadata.json'))['date'])" 2>/dev/null || echo "unknown")
            echo "  ${name}  |  ${date}  |  ${size}"
        fi
    done
}

# ==================== 主逻辑 ====================
case "${1:-backup}" in
    backup)
        log "===== 开始全量备份 ====="
        backup_mysql
        backup_redis
        backup_elasticsearch
        save_metadata
        cleanup
        log "===== 全量备份完成 ====="
        ;;
    restore)
        [ -z "${2:-}" ] && error "请指定要恢复的备份名称"
        restore "$2"
        ;;
    list)
        list_backups
        ;;
    cleanup)
        cleanup
        ;;
    *)
        echo "Usage: $0 {backup|restore <name>|list|cleanup}"
        exit 1
        ;;
esac
