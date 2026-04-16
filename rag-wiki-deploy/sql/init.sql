-- ============================================================
-- 智维Wiki 数据库初始化脚本
-- ============================================================

CREATE DATABASE IF NOT EXISTS `rag_wiki` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `rag_wiki`;

-- ========== 用户与权限相关表 ==========

-- 用户表
CREATE TABLE IF NOT EXISTS `sys_user` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `user_id` VARCHAR(64) UNIQUE NOT NULL COMMENT '用户唯一标识',
    `username` VARCHAR(64) NOT NULL COMMENT '用户名',
    `password` VARCHAR(255) NOT NULL COMMENT '加密密码',
    `real_name` VARCHAR(64) COMMENT '真实姓名',
    `email` VARCHAR(128) COMMENT '邮箱',
    `phone` VARCHAR(32) COMMENT '手机号',
    `dept_id` BIGINT COMMENT '所属部门ID',
    `status` TINYINT DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
    `security_level` TINYINT DEFAULT 1 COMMENT '安全等级：1-公开 2-内部 3-敏感 4-机密',
    `tenant_id` VARCHAR(64) DEFAULT 'default' COMMENT '租户ID',
    `is_deleted` TINYINT DEFAULT 0 COMMENT '是否删除',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_dept_id` (`dept_id`),
    INDEX `idx_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

-- 部门表
CREATE TABLE IF NOT EXISTS `sys_dept` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `dept_id` VARCHAR(64) UNIQUE NOT NULL,
    `dept_name` VARCHAR(128) NOT NULL,
    `parent_id` BIGINT DEFAULT 0 COMMENT '父部门ID',
    `sort` INT DEFAULT 0 COMMENT '排序',
    `status` TINYINT DEFAULT 1,
    `tenant_id` VARCHAR(64) DEFAULT 'default',
    `is_deleted` TINYINT DEFAULT 0,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门表';

-- 角色表
CREATE TABLE IF NOT EXISTS `sys_role` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `role_id` VARCHAR(64) UNIQUE NOT NULL,
    `role_name` VARCHAR(64) NOT NULL,
    `role_code` VARCHAR(64) NOT NULL COMMENT '角色标识',
    `role_level` INT DEFAULT 0 COMMENT '角色等级',
    `description` VARCHAR(255),
    `status` TINYINT DEFAULT 1,
    `tenant_id` VARCHAR(64) DEFAULT 'default',
    `is_deleted` TINYINT DEFAULT 0,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- 用户角色关联表
CREATE TABLE IF NOT EXISTS `sys_user_role` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `user_id` VARCHAR(64) NOT NULL,
    `role_id` VARCHAR(64) NOT NULL,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

-- 文档权限表
CREATE TABLE IF NOT EXISTS `document_permission` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `document_id` VARCHAR(64) NOT NULL COMMENT '文档ID',
    `space_id` VARCHAR(64) NOT NULL COMMENT '知识库空间ID',
    `security_level` TINYINT DEFAULT 1 COMMENT '安全等级',
    `owning_dept_id` VARCHAR(64) COMMENT '所属部门ID',
    `allowed_dept_ids` JSON COMMENT '允许访问的部门列表',
    `allowed_role_ids` JSON COMMENT '允许访问的角色列表',
    `min_role_level` INT DEFAULT 0 COMMENT '最低角色等级',
    `allowed_user_ids` JSON COMMENT '用户白名单',
    `creator_id` VARCHAR(64) NOT NULL COMMENT '创建者ID',
    `valid_from` DATETIME COMMENT '生效时间',
    `valid_to` DATETIME COMMENT '失效时间',
    `requires_approval` TINYINT DEFAULT 0 COMMENT '是否需要审批',
    `is_deleted` TINYINT DEFAULT 0,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_document_id` (`document_id`),
    INDEX `idx_space_id` (`space_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档权限表';

-- ========== 文档与知识库相关表 ==========

-- 知识库空间表
CREATE TABLE IF NOT EXISTS `knowledge_space` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `space_id` VARCHAR(64) UNIQUE NOT NULL,
    `space_name` VARCHAR(128) NOT NULL,
    `description` VARCHAR(255),
    `space_type` TINYINT DEFAULT 1 COMMENT '1-私有 2-部门 3-公共',
    `owner_id` VARCHAR(64) NOT NULL COMMENT '所属者ID',
    `dept_id` VARCHAR(64) COMMENT '所属部门ID',
    `storage_quota` BIGINT DEFAULT 0 COMMENT '存储配额，0为无限制',
    `status` TINYINT DEFAULT 1,
    `tenant_id` VARCHAR(64) DEFAULT 'default',
    `is_deleted` TINYINT DEFAULT 0,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库空间表';

-- 文档主表
CREATE TABLE IF NOT EXISTS `document_info` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `document_id` VARCHAR(64) UNIQUE NOT NULL,
    `space_id` VARCHAR(64) NOT NULL,
    `parent_id` VARCHAR(64) DEFAULT '0' COMMENT '父文件夹ID',
    `document_name` VARCHAR(255) NOT NULL,
    `document_type` VARCHAR(32) COMMENT '文档类型',
    `file_path` VARCHAR(512) COMMENT '源文件存储路径',
    `file_size` BIGINT DEFAULT 0 COMMENT '文件大小，单位字节',
    `version` VARCHAR(32) DEFAULT '1.0.0' COMMENT '当前版本',
    `creator_id` VARCHAR(64) NOT NULL,
    `last_editor_id` VARCHAR(64),
    `status` TINYINT DEFAULT 0 COMMENT '0-草稿 1-已发布 2-已归档',
    `is_folder` TINYINT DEFAULT 0 COMMENT '0-文档 1-文件夹',
    `security_level` TINYINT DEFAULT 1 COMMENT '安全等级',
    `tenant_id` VARCHAR(64) DEFAULT 'default',
    `is_deleted` TINYINT DEFAULT 0,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_space_parent` (`space_id`, `parent_id`),
    INDEX `idx_creator_id` (`creator_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档主表';

-- 文档版本表
CREATE TABLE IF NOT EXISTS `document_version` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `version_id` VARCHAR(64) UNIQUE NOT NULL,
    `document_id` VARCHAR(64) NOT NULL,
    `version` VARCHAR(32) NOT NULL,
    `content` LONGTEXT COMMENT '文档内容',
    `content_type` VARCHAR(32) DEFAULT 'markdown' COMMENT '内容类型: markdown/html/json',
    `change_log` VARCHAR(512) COMMENT '变更说明',
    `editor_id` VARCHAR(64) NOT NULL,
    `word_count` INT DEFAULT 0 COMMENT '字数统计',
    `is_deleted` TINYINT DEFAULT 0,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_document_id` (`document_id`),
    INDEX `idx_version` (`document_id`, `version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档版本表';

-- 文档内容块表（支持块级存储，类似Notion结构）
CREATE TABLE IF NOT EXISTS `document_block` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `block_id` VARCHAR(64) UNIQUE NOT NULL,
    `document_id` VARCHAR(64) NOT NULL,
    `parent_id` VARCHAR(64) DEFAULT '0' COMMENT '父块ID',
    `block_type` VARCHAR(32) NOT NULL COMMENT '块类型: paragraph/heading/list/code/table/image',
    `content` JSON COMMENT '块内容',
    `props` JSON COMMENT '块属性',
    `order_num` INT DEFAULT 0 COMMENT '排序序号',
    `version_id` VARCHAR(64) COMMENT '所属版本',
    `is_deleted` TINYINT DEFAULT 0,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_document_id` (`document_id`),
    INDEX `idx_parent_id` (`parent_id`),
    INDEX `idx_version` (`version_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档内容块表';

-- 文档解析任务表
CREATE TABLE IF NOT EXISTS `document_parse_task` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `task_id` VARCHAR(64) UNIQUE NOT NULL,
    `document_id` VARCHAR(64) NOT NULL,
    `file_name` VARCHAR(255) NOT NULL,
    `file_type` VARCHAR(32) NOT NULL,
    `space_id` VARCHAR(64) NOT NULL,
    `status` ENUM('PENDING', 'PARSING', 'VECTORIZING', 'COMPLETED', 'FAILED', 'NEED_REVIEW') NOT NULL,
    `parser_type` VARCHAR(32),
    `quality_score` FLOAT DEFAULT 0.0,
    `chunk_count` INT DEFAULT 0,
    `table_count` INT DEFAULT 0,
    `error_message` TEXT,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_status_space` (`status`, `space_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档解析任务表';

-- ========== AI与审计相关表 ==========

-- RAG Badcase表
CREATE TABLE IF NOT EXISTS `rag_badcase` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `case_id` VARCHAR(64) UNIQUE NOT NULL,
    `query` TEXT NOT NULL,
    `answer` TEXT,
    `expected_answer` TEXT,
    `badcase_type` ENUM('RETRIEVAL_FAILURE', 'HALLUCINATION', 'ROUTING_ERROR', 'KNOWLEDGE_GAP') NOT NULL,
    `source` ENUM('USER_FEEDBACK', 'CUSTOMER_TICKET', 'AUTO_DETECTION') NOT NULL,
    `user_id` VARCHAR(64),
    `session_id` VARCHAR(64),
    `confidence_score` FLOAT,
    `status` ENUM('NEW', 'IN_REVIEW', 'FIXED', 'WONT_FIX') DEFAULT 'NEW',
    `assigned_to` VARCHAR(64),
    `fix_strategy` VARCHAR(255),
    `resolved_at` TIMESTAMP,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_type_status` (`badcase_type`, `status`),
    INDEX `idx_source_created` (`source`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG Badcase表';

-- RAG审计日志表
CREATE TABLE IF NOT EXISTS `rag_audit_log` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `audit_id` VARCHAR(64) UNIQUE NOT NULL,
    `user_id` VARCHAR(64) NOT NULL,
    `dept_id` VARCHAR(64),
    `user_role` VARCHAR(64),
    `security_level` TINYINT,
    `query` TEXT NOT NULL,
    `retrieved_doc_ids` JSON,
    `retrieved_security_levels` JSON,
    `retrieved_dept_ids` JSON,
    `session_id` VARCHAR(64),
    `client_ip` VARCHAR(64),
    `user_agent` VARCHAR(512),
    `response_time` BIGINT COMMENT '响应耗时，单位毫秒',
    `is_success` TINYINT DEFAULT 1,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG审计日志表';

-- 系统操作日志表
CREATE TABLE IF NOT EXISTS `sys_operation_log` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `log_id` VARCHAR(64) UNIQUE NOT NULL,
    `user_id` VARCHAR(64),
    `username` VARCHAR(64),
    `dept_id` VARCHAR(64),
    `operation_type` VARCHAR(32) COMMENT 'CREATE/UPDATE/DELETE/QUERY/UPLOAD/APPROVE/EXPORT',
    `module` VARCHAR(32) COMMENT 'document/space/user/role/dept/approval/rag',
    `target_id` VARCHAR(64) COMMENT '操作对象ID',
    `target_name` VARCHAR(255) COMMENT '操作对象名称',
    `description` VARCHAR(512) COMMENT '操作描述',
    `request_method` VARCHAR(16) COMMENT 'GET/POST/PUT/DELETE',
    `request_url` VARCHAR(512),
    `request_params` TEXT COMMENT '请求参数JSON',
    `response_code` INT,
    `client_ip` VARCHAR(64),
    `user_agent` VARCHAR(512),
    `cost_time` BIGINT COMMENT '耗时毫秒',
    `is_success` TINYINT DEFAULT 1,
    `is_deleted` TINYINT DEFAULT 0,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_module_type` (`module`, `operation_type`),
    INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统操作日志表';

-- ========== 审批相关表 ==========

CREATE TABLE IF NOT EXISTS `approval_flow` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `flow_id` VARCHAR(64) UNIQUE NOT NULL,
    `document_id` VARCHAR(64) NOT NULL,
    `space_id` VARCHAR(64) NOT NULL,
    `security_level` INT DEFAULT 1,
    `submitter_id` VARCHAR(64) NOT NULL,
    `current_step` INT DEFAULT 1,
    `total_steps` INT DEFAULT 1,
    `status` VARCHAR(32) DEFAULT 'PENDING',
    `result` VARCHAR(255),
    `is_deleted` TINYINT DEFAULT 0,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批流程表';

CREATE TABLE IF NOT EXISTS `approval_task` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `task_id` VARCHAR(64) UNIQUE NOT NULL,
    `flow_id` VARCHAR(64) NOT NULL,
    `step` INT NOT NULL,
    `approver_id` VARCHAR(64),
    `approver_type` VARCHAR(32) COMMENT 'DEPT_HEAD/COMPLIANCE/ADMIN',
    `status` VARCHAR(32) DEFAULT 'PENDING',
    `comment` TEXT,
    `approved_at` VARCHAR(64),
    `is_deleted` TINYINT DEFAULT 0,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批任务表';

-- ========== 初始数据 ==========

-- 初始管理员（密码: admin123）
INSERT INTO `sys_user` (`user_id`, `username`, `password`, `real_name`, `security_level`, `status`) VALUES
('admin001', 'admin', '$2a$10$N.ZOn9G6/YLFixAOPMg/h.z7pCu6v2XyFDtC4q.jeeGM/TEZyj3Cq', '系统管理员', 4, 1);

-- 初始部门
INSERT INTO `sys_dept` (`dept_id`, `dept_name`, `parent_id`, `sort`) VALUES
('dept001', '总公司', 0, 1),
('dept002', '技术部', 1, 1),
('dept003', '产品部', 1, 2),
('dept004', '安全部', 1, 3);

-- 初始角色
INSERT INTO `sys_role` (`role_id`, `role_name`, `role_code`, `role_level`, `description`) VALUES
('role001', '超级管理员', 'SUPER_ADMIN', 99, '拥有所有权限'),
('role002', '知识库管理员', 'SPACE_ADMIN', 50, '知识库空间管理权限'),
('role003', '编辑者', 'EDITOR', 30, '文档编辑权限'),
('role004', '普通用户', 'VIEWER', 10, '只读查看权限');

-- 管理员角色关联
INSERT INTO `sys_user_role` (`user_id`, `role_id`) VALUES ('admin001', 'role001');

-- ========== 安全告警表 ==========

CREATE TABLE IF NOT EXISTS `security_alert` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `alert_id` VARCHAR(64) UNIQUE NOT NULL,
    `rule_id` VARCHAR(64) COMMENT '触发规则ID',
    `alert_type` VARCHAR(32) NOT NULL COMMENT 'HIGH_FREQUENCY_QUERY/CROSS_DEPT_ACCESS/HIGH_LEVEL_DOC_ACCESS/ABNORMAL_TIME_ACCESS/PERMISSION_VIOLATION/BATCH_DOWNLOAD/FAILED_LOGIN_ATTEMPT/TOKEN_ANOMALY/DATA_EXPORT/SQL_INJECTION/CUSTOM',
    `severity` VARCHAR(16) NOT NULL COMMENT 'INFO/LOW/MEDIUM/HIGH/CRITICAL',
    `title` VARCHAR(255) NOT NULL,
    `description` TEXT,
    `content` TEXT COMMENT '告警内容',
    `user_id` VARCHAR(64),
    `username` VARCHAR(64),
    `client_ip` VARCHAR(64),
    `source_ip` VARCHAR(64) COMMENT '来源IP',
    `target_id` VARCHAR(64) COMMENT '目标对象ID',
    `evidence` JSON COMMENT '证据数据',
    `extra_data` TEXT COMMENT '扩展数据JSON',
    `status` VARCHAR(32) DEFAULT 'NEW' COMMENT 'NEW/CONFIRMED/HANDLING/HANDLED/RESOLVED/IGNORED',
    `handled_by` VARCHAR(64) COMMENT '处理人ID',
    `handler_id` VARCHAR(64) COMMENT '处理人ID',
    `handle_remark` TEXT COMMENT '处理备注',
    `handle_result` TEXT COMMENT '处理结果',
    `handled_at` TIMESTAMP NULL COMMENT '处理时间',
    `is_deleted` TINYINT DEFAULT 0,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_rule_id` (`rule_id`),
    INDEX `idx_alert_type` (`alert_type`),
    INDEX `idx_severity` (`severity`),
    INDEX `idx_status` (`status`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='安全告警表';

-- ========== 菜单权限表 ==========

CREATE TABLE IF NOT EXISTS `sys_menu` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `menu_id` VARCHAR(64) UNIQUE NOT NULL,
    `parent_id` BIGINT DEFAULT 0 COMMENT '父菜单ID',
    `menu_name` VARCHAR(64) NOT NULL COMMENT '菜单名称',
    `menu_code` VARCHAR(64) COMMENT '菜单标识',
    `menu_type` TINYINT DEFAULT 1 COMMENT '1-目录 2-菜单 3-按钮',
    `path` VARCHAR(255) COMMENT '路由路径',
    `component` VARCHAR(255) COMMENT '组件路径',
    `icon` VARCHAR(64) COMMENT '图标',
    `sort` INT DEFAULT 0 COMMENT '排序',
    `visible` TINYINT DEFAULT 1 COMMENT '是否显示',
    `status` TINYINT DEFAULT 1,
    `is_deleted` TINYINT DEFAULT 0,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜单权限表';

-- 初始菜单数据
INSERT INTO `sys_menu` (`menu_id`, `parent_id`, `menu_name`, `menu_code`, `menu_type`, `path`, `icon`, `sort`) VALUES
('menu001', 0, '知识库', 'knowledge', 1, '/knowledge', 'folder', 1),
('menu002', 0, 'AI助手', 'ai', 1, '/ai', 'robot', 2),
('menu003', 0, '管理中心', 'admin', 1, '/admin', 'setting', 3),
('menu004', 'menu001', '文档管理', 'document', 2, '/knowledge/document', 'file', 1),
('menu005', 'menu001', '知识空间', 'space', 2, '/knowledge/space', 'database', 2),
('menu006', 'menu002', '智能问答', 'chat', 2, '/ai/chat', 'message', 1),
('menu007', 'menu003', '用户管理', 'user', 2, '/admin/user', 'user', 1),
('menu008', 'menu003', '角色管理', 'role', 2, '/admin/role', 'team', 2),
('menu009', 'menu003', '部门管理', 'dept', 2, '/admin/dept', 'apartment', 3),
('menu010', 'menu003', '审计日志', 'audit', 2, '/admin/audit', 'file-text', 4),
('menu011', 'menu003', '安全告警', 'alert', 2, '/admin/security-alert', 'warning', 5),
('menu012', 'menu003', '统计报表', 'statistics', 2, '/admin/statistics', 'bar-chart', 6),
('menu013', 'menu003', 'AI模型配置', 'llm-config', 2, '/admin/llm-config', 'api', 7),
('menu014', 'menu003', '向量库管理', 'vector', 2, '/admin/vector', 'server', 8),
('menu015', 'menu003', '租户管理', 'tenant', 2, '/admin/tenant', 'business', 9),
('menu016', 'menu003', 'Badcase管理', 'badcase', 2, '/admin/badcase', 'bug', 10);

-- ========== AI模型配置表 ==========

-- LLM模型配置表
CREATE TABLE IF NOT EXISTS `ai_llm_config` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `config_id` VARCHAR(64) UNIQUE NOT NULL COMMENT '配置唯一标识',
    `config_name` VARCHAR(128) NOT NULL COMMENT '配置名称',
    `provider` VARCHAR(32) NOT NULL COMMENT '提供商: openai/deepseek/qwen/gemini/glm/kimi/local',
    `model` VARCHAR(64) NOT NULL COMMENT '模型名称',
    `api_key` VARCHAR(255) NOT NULL COMMENT 'API密钥',
    `api_base` VARCHAR(255) COMMENT 'API基础URL',
    `temperature` DECIMAL(3,2) DEFAULT 0.70 COMMENT '温度参数',
    `max_tokens` INT DEFAULT 4096 COMMENT '最大Token数',
    `is_default` TINYINT DEFAULT 0 COMMENT '是否默认配置',
    `is_enabled` TINYINT DEFAULT 1 COMMENT '是否启用',
    `priority` INT DEFAULT 0 COMMENT '优先级(用于重试顺序)',
    `description` VARCHAR(512) COMMENT '配置描述',
    `extra_config` JSON COMMENT '扩展配置JSON',
    `tenant_id` VARCHAR(64) DEFAULT 'default' COMMENT '租户ID',
    `is_deleted` TINYINT DEFAULT 0,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_provider` (`provider`),
    INDEX `idx_is_default` (`is_default`),
    INDEX `idx_is_enabled` (`is_enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LLM模型配置表';

-- Embedding模型配置表
CREATE TABLE IF NOT EXISTS `ai_embedding_config` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `config_id` VARCHAR(64) UNIQUE NOT NULL,
    `config_name` VARCHAR(128) NOT NULL,
    `provider` VARCHAR(32) NOT NULL COMMENT 'openai/local/huggingface',
    `model` VARCHAR(64) NOT NULL,
    `api_key` VARCHAR(255),
    `api_base` VARCHAR(255),
    `dimension` INT DEFAULT 1536 COMMENT '向量维度',
    `is_default` TINYINT DEFAULT 0,
    `is_enabled` TINYINT DEFAULT 1,
    `description` VARCHAR(512),
    `tenant_id` VARCHAR(64) DEFAULT 'default',
    `is_deleted` TINYINT DEFAULT 0,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Embedding模型配置表';

-- 初始LLM配置数据（示例）
INSERT INTO `ai_llm_config` (`config_id`, `config_name`, `provider`, `model`, `api_key`, `api_base`, `temperature`, `max_tokens`, `is_default`, `is_enabled`, `priority`, `description`) VALUES
('llm001', 'DeepSeek默认配置', 'deepseek', 'deepseek-chat', '', 'https://api.deepseek.com/v1', 0.70, 4096, 1, 1, 1, 'DeepSeek智能对话模型'),
('llm002', '千问-Turbo', 'qwen', 'qwen-turbo', '', 'https://dashscope.aliyuncs.com/compatible-mode/v1', 0.70, 4096, 0, 1, 2, '阿里千问Turbo模型'),
('llm003', '智普GLM-4', 'glm', 'glm-4', '', 'https://open.bigmodel.cn/api/paas/v4', 0.70, 4096, 0, 1, 3, '智普GLM-4模型'),
('llm004', 'Kimi助手', 'kimi', 'moonshot-v1-8k', '', 'https://api.moonshot.cn/v1', 0.70, 8192, 0, 0, 4, 'Moonshot Kimi模型'),
('llm005', 'OpenAI GPT-4', 'openai', 'gpt-4o', '', 'https://api.openai.com/v1', 0.70, 4096, 0, 0, 5, 'OpenAI GPT-4模型');

-- 初始Embedding配置数据
INSERT INTO `ai_embedding_config` (`config_id`, `config_name`, `provider`, `model`, `api_key`, `api_base`, `dimension`, `is_default`, `is_enabled`, `description`) VALUES
('emb001', 'OpenAI Embedding', 'openai', 'text-embedding-ada-002', '', 'https://api.openai.com/v1', 1536, 1, 1, 'OpenAI标准Embedding模型'),
('emb002', '千问Embedding', 'qwen', 'text-embedding-v2', '', 'https://dashscope.aliyuncs.com/compatible-mode/v1', 1536, 0, 1, '阿里千问Embedding模型');

-- ========== 角色权限关联表 ==========

-- 角色菜单权限表
CREATE TABLE IF NOT EXISTS `sys_role_menu` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `role_id` VARCHAR(64) NOT NULL,
    `menu_id` VARCHAR(64) NOT NULL,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_role_menu` (`role_id`, `menu_id`),
    INDEX `idx_role_id` (`role_id`),
    INDEX `idx_menu_id` (`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色菜单权限表';

-- ========== 消息通知表 ==========

-- 消息通知表
CREATE TABLE IF NOT EXISTS `sys_notification` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `notification_id` VARCHAR(64) UNIQUE NOT NULL,
    `user_id` VARCHAR(64) NOT NULL COMMENT '接收用户ID',
    `type` VARCHAR(32) NOT NULL COMMENT '消息类型: SYSTEM/APPROVAL/DOCUMENT/SECURITY',
    `title` VARCHAR(255) NOT NULL,
    `content` TEXT,
    `is_read` TINYINT DEFAULT 0 COMMENT '是否已读',
    `read_time` TIMESTAMP NULL COMMENT '阅读时间',
    `sender_id` VARCHAR(64) COMMENT '发送者ID',
    `sender_name` VARCHAR(64) COMMENT '发送者名称',
    `target_id` VARCHAR(64) COMMENT '关联业务ID',
    `target_type` VARCHAR(32) COMMENT '关联业务类型',
    `extra_data` JSON COMMENT '扩展数据',
    `is_deleted` TINYINT DEFAULT 0,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_type` (`type`),
    INDEX `idx_is_read` (`is_read`),
    INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息通知表';

-- 初始角色菜单权限数据（管理员拥有所有权限）
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
('role001', 'menu001'), ('role001', 'menu002'), ('role001', 'menu003'),
('role001', 'menu004'), ('role001', 'menu005'), ('role001', 'menu006'),
('role001', 'menu007'), ('role001', 'menu008'), ('role001', 'menu009'),
('role001', 'menu010'), ('role001', 'menu011'), ('role001', 'menu012'),
('role001', 'menu013'), ('role001', 'menu014'), ('role001', 'menu015'),
('role001', 'menu016');

-- ========== 智能记忆管理表 ==========

-- 用户长期记忆表
CREATE TABLE IF NOT EXISTS `user_memory` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `memory_id` VARCHAR(64) UNIQUE NOT NULL COMMENT '记忆唯一标识',
    `user_id` VARCHAR(64) NOT NULL COMMENT '用户ID',
    `content` TEXT NOT NULL COMMENT '记忆内容',
    `memory_type` VARCHAR(32) NOT NULL COMMENT '记忆类型: SEMANTIC/EPISODIC/PROCEDURAL',
    `embedding` TEXT COMMENT '向量表示(JSON格式存储)',
    `metadata` JSON COMMENT '元数据',
    `source_session_id` VARCHAR(64) COMMENT '来源会话ID',
    `confidence_score` FLOAT DEFAULT 1.0 COMMENT '置信度',
    `ttl` BIGINT DEFAULT -1 COMMENT '过期时间戳，-1为永久',
    `operation_type` VARCHAR(32) DEFAULT 'ADD' COMMENT '操作类型: ADD/UPDATE/DELETE/NOOP',
    `is_deleted` TINYINT DEFAULT 0,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_memory_type` (`memory_type`),
    INDEX `idx_ttl` (`ttl`),
    INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户长期记忆表';

-- 会话短期记忆表
CREATE TABLE IF NOT EXISTS `session_memory` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `session_id` VARCHAR(64) NOT NULL COMMENT '会话ID',
    `user_id` VARCHAR(64) NOT NULL COMMENT '用户ID',
    `message_role` VARCHAR(32) NOT NULL COMMENT '消息角色: user/assistant/system',
    `content` TEXT NOT NULL COMMENT '消息内容',
    `message_index` INT NOT NULL COMMENT '消息序号',
    `metadata` JSON COMMENT '元数据',
    `is_deleted` TINYINT DEFAULT 0,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_session_id` (`session_id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话短期记忆表';

-- 记忆冲突检测表
CREATE TABLE IF NOT EXISTS `memory_conflict` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `conflict_id` VARCHAR(64) UNIQUE NOT NULL,
    `memory_id_1` VARCHAR(64) NOT NULL COMMENT '记忆1ID',
    `memory_id_2` VARCHAR(64) NOT NULL COMMENT '记忆2ID',
    `conflict_type` VARCHAR(32) NOT NULL COMMENT '冲突类型: CONTRADICTORY/UPDATE/DUPLICATE',
    `conflict_score` FLOAT COMMENT '冲突分数',
    `resolution` VARCHAR(32) COMMENT '解决方式: MERGE/REPLACE/KEEP_BOTH/DELETE',
    `status` VARCHAR(32) DEFAULT 'PENDING' COMMENT '状态: PENDING/RESOLVED/IGNORED',
    `is_deleted` TINYINT DEFAULT 0,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `resolved_at` TIMESTAMP NULL,
    INDEX `idx_memory_id_1` (`memory_id_1`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='记忆冲突检测表';

-- ========== 安全告警中心表 ==========

-- 安全告警规则表
CREATE TABLE IF NOT EXISTS `security_alert_rule` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `rule_id` VARCHAR(64) UNIQUE NOT NULL,
    `rule_name` VARCHAR(128) NOT NULL COMMENT '规则名称',
    `rule_type` VARCHAR(32) NOT NULL COMMENT '规则类型: FREQUENCY/ACCESS/CONTENT/ANOMALY',
    `description` VARCHAR(512) COMMENT '规则描述',
    `condition_config` JSON NOT NULL COMMENT '触发条件配置',
    `severity` VARCHAR(32) NOT NULL COMMENT '严重程度: LOW/MEDIUM/HIGH/CRITICAL',
    `status` TINYINT DEFAULT 1 COMMENT '状态: 0-禁用 1-启用',
    `notify_channels` JSON COMMENT '通知渠道',
    `is_deleted` TINYINT DEFAULT 0,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_rule_type` (`rule_type`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='安全告警规则表';

-- 初始化安全告警规则
INSERT INTO `security_alert_rule` (`rule_id`, `rule_name`, `rule_type`, `description`, `condition_config`, `severity`, `notify_channels`) VALUES
('rule001', '高频查询检测', 'FREQUENCY', '单个用户1分钟内查询超过20次', '{"timeWindow": 60, "threshold": 20, "metric": "query_count"}', 'MEDIUM', '["SITE_MESSAGE", "EMAIL"]'),
('rule002', '高密级文档批量访问', 'ACCESS', '用户1小时内访问5个以上机密文档', '{"timeWindow": 3600, "threshold": 5, "securityLevel": 4}', 'HIGH', '["SITE_MESSAGE", "EMAIL", "DINGTALK"]'),
('rule003', '非工作时间访问', 'ANOMALY', '用户在非工作时间(22:00-06:00)访问系统', '{"startTime": "22:00", "endTime": "06:00"}', 'LOW', '["SITE_MESSAGE"]'),
('rule004', '跨部门越权访问', 'ACCESS', '用户访问非所属部门的高密级文档', '{"checkDept": true, "securityLevel": 3}', 'CRITICAL', '["SITE_MESSAGE", "EMAIL", "DINGTALK"]'),
('rule005', '异常SQL注入尝试', 'CONTENT', '检测到可能的SQL注入攻击', '{"patterns": ["union select", "drop table", "delete from"]}', 'CRITICAL', '["SITE_MESSAGE", "EMAIL", "DINGTALK"]');

-- ========== 第三阶段：多租户管理 ==========

-- 租户表
CREATE TABLE IF NOT EXISTS `sys_tenant` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `tenant_id` VARCHAR(64) UNIQUE NOT NULL COMMENT '租户唯一标识',
    `tenant_name` VARCHAR(128) NOT NULL COMMENT '租户名称',
    `tenant_code` VARCHAR(64) UNIQUE NOT NULL COMMENT '租户编码',
    `contact_name` VARCHAR(64) COMMENT '联系人',
    `contact_phone` VARCHAR(32) COMMENT '联系电话',
    `contact_email` VARCHAR(128) COMMENT '联系邮箱',
    `isolation_level` VARCHAR(32) DEFAULT 'metadata_filter' COMMENT '隔离级别: metadata_filter/partition/collection',
    `status` TINYINT DEFAULT 1 COMMENT '状态：0-禁用 1-启用 2-试用',
    `storage_quota_mb` BIGINT DEFAULT 10240 COMMENT '存储配额(MB)',
    `storage_used_mb` BIGINT DEFAULT 0 COMMENT '已用存储(MB)',
    `max_users` INT DEFAULT 100 COMMENT '最大用户数',
    `max_spaces` INT DEFAULT 20 COMMENT '最大知识库数',
    `expire_time` VARCHAR(32) COMMENT '到期时间',
    `domain` VARCHAR(200) COMMENT '域名绑定',
    `logo_url` VARCHAR(500) COMMENT 'Logo URL',
    `extra_config` JSON COMMENT '扩展配置',
    `is_deleted` TINYINT DEFAULT 0,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_tenant_code` (`tenant_code`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户表';

-- 初始化默认租户
INSERT INTO `sys_tenant` (`tenant_id`, `tenant_name`, `tenant_code`, `isolation_level`, `status`, `storage_quota_mb`, `max_users`, `max_spaces`) VALUES
('default', '默认租户', 'default', 'metadata_filter', 1, 102400, 1000, 100);

-- ========== 文档互动相关表 ==========

-- 文档收藏表
CREATE TABLE IF NOT EXISTS `document_favorite` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `user_id` VARCHAR(64) NOT NULL COMMENT '用户ID',
    `document_id` VARCHAR(64) NOT NULL COMMENT '文档ID',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX `uk_user_document` (`user_id`, `document_id`),
    INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档收藏表';

-- 文档点赞表
CREATE TABLE IF NOT EXISTS `document_like` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `user_id` VARCHAR(64) NOT NULL COMMENT '用户ID',
    `document_id` VARCHAR(64) NOT NULL COMMENT '文档ID',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX `uk_user_document` (`user_id`, `document_id`),
    INDEX `idx_document_id` (`document_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档点赞表';

-- 文档评论表
CREATE TABLE IF NOT EXISTS `document_comment` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `comment_id` VARCHAR(64) UNIQUE NOT NULL COMMENT '评论唯一标识',
    `user_id` VARCHAR(64) NOT NULL COMMENT '评论用户ID',
    `document_id` VARCHAR(64) NOT NULL COMMENT '文档ID',
    `content` TEXT NOT NULL COMMENT '评论内容',
    `parent_id` BIGINT COMMENT '父评论ID（支持楼中楼）',
    `is_deleted` TINYINT DEFAULT 0 COMMENT '是否删除',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_document_id` (`document_id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档评论表';

-- 浏览历史表
CREATE TABLE IF NOT EXISTS `browse_history` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `user_id` VARCHAR(64) NOT NULL COMMENT '用户ID',
    `document_id` VARCHAR(64) NOT NULL COMMENT '文档ID',
    `browse_count` INT DEFAULT 1 COMMENT '浏览次数',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_user_document` (`user_id`, `document_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='浏览历史表';

-- ========== 系统配置相关表 ==========

-- 系统配置表
CREATE TABLE IF NOT EXISTS `sys_config` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `config_key` VARCHAR(128) UNIQUE NOT NULL COMMENT '配置键',
    `config_value` TEXT COMMENT '配置值',
    `config_type` VARCHAR(32) DEFAULT 'STRING' COMMENT '值类型: STRING/NUMBER/BOOLEAN/JSON',
    `config_group` VARCHAR(64) DEFAULT 'DEFAULT' COMMENT '配置分组',
    `description` VARCHAR(512) COMMENT '配置说明',
    `is_system` TINYINT DEFAULT 0 COMMENT '是否系统内置(不可删除)',
    `is_readonly` TINYINT DEFAULT 0 COMMENT '是否只读',
    `sort_order` INT DEFAULT 0 COMMENT '排序',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_config_group` (`config_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置表';

-- 初始化默认系统配置
INSERT INTO `sys_config` (`config_key`, `config_value`, `config_type`, `config_group`, `description`, `is_system`, `sort_order`) VALUES
('site.name', '智维Wiki', 'STRING', 'BASIC', '站点名称', 1, 1),
('site.logo', '', 'STRING', 'BASIC', '站点Logo URL', 0, 2),
('site.description', '企业级智能知识库', 'STRING', 'BASIC', '站点描述', 0, 3),
('security.password.minLength', '8', 'NUMBER', 'SECURITY', '密码最小长度', 1, 10),
('security.password.requireUpper', 'true', 'BOOLEAN', 'SECURITY', '密码是否需要大写字母', 0, 11),
('security.password.requireLower', 'true', 'BOOLEAN', 'SECURITY', '密码是否需要小写字母', 0, 12),
('security.password.requireDigit', 'true', 'BOOLEAN', 'SECURITY', '密码是否需要数字', 0, 13),
('security.password.requireSpecial', 'false', 'BOOLEAN', 'SECURITY', '密码是否需要特殊字符', 0, 14),
('security.login.maxRetry', '5', 'NUMBER', 'SECURITY', '登录最大重试次数', 1, 15),
('security.login.lockMinutes', '30', 'NUMBER', 'SECURITY', '账号锁定时长(分钟)', 1, 16),
('security.login.enable2FA', 'false', 'BOOLEAN', 'SECURITY', '是否强制双因素认证', 0, 17),
('security.session.timeout', '7200', 'NUMBER', 'SECURITY', '会话超时时间(秒)', 0, 20),
('notification.email.enable', 'false', 'BOOLEAN', 'NOTIFICATION', '是否启用邮件通知', 0, 30),
('notification.email.smtpHost', '', 'STRING', 'NOTIFICATION', 'SMTP服务器地址', 0, 31),
('notification.email.smtpPort', '465', 'NUMBER', 'NOTIFICATION', 'SMTP端口', 0, 32),
('notification.email.sender', '', 'STRING', 'NOTIFICATION', '发件人地址', 0, 33),
('notification.template.welcome', '欢迎加入{siteName}！您的账号已创建成功。', 'STRING', 'NOTIFICATION', '欢迎邮件模板', 0, 40),
('notification.template.passwordReset', '您正在重置密码，验证码：{code}，有效期{expire}分钟。', 'STRING', 'NOTIFICATION', '密码重置模板', 0, 41),
('upload.maxFileSize', '100', 'NUMBER', 'UPLOAD', '上传文件大小限制(MB)', 0, 50),
('upload.allowedTypes', 'pdf,doc,docx,xlsx,pptx,txt,md,csv', 'STRING', 'UPLOAD', '允许上传的文件类型', 0, 51),
('rag.chunkSize', '500', 'NUMBER', 'RAG', '默认分块大小', 0, 60),
('rag.chunkOverlap', '50', 'NUMBER', 'RAG', '分块重叠字符数', 0, 61),
('rag.topK', '5', 'NUMBER', 'RAG', '检索返回文档数', 0, 62),
('rag.scoreThreshold', '0.5', 'NUMBER', 'RAG', '相似度阈值', 0, 63);

-- 用户扩展信息字段（OAuth2绑定、2FA等）
ALTER TABLE `sys_user` ADD COLUMN IF NOT EXISTS `ext_info` JSON COMMENT '扩展信息(OAuth2绑定/2FA等)';

-- ========== 用户API密钥表 ==========

CREATE TABLE IF NOT EXISTS `user_api_key` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `key_id` VARCHAR(64) UNIQUE NOT NULL COMMENT '密钥ID',
    `user_id` VARCHAR(64) NOT NULL COMMENT '所属用户ID',
    `key_name` VARCHAR(128) NOT NULL COMMENT '密钥名称',
    `key_hash` VARCHAR(255) NOT NULL COMMENT 'SHA-256哈希值',
    `key_prefix` VARCHAR(32) COMMENT '密钥前缀(展示用: sk-xxxx...xxxx)',
    `is_enabled` TINYINT DEFAULT 1 COMMENT '是否启用',
    `expires_at` DATETIME COMMENT '过期时间',
    `last_used_at` DATETIME COMMENT '最后使用时间',
    `rate_limit` INT DEFAULT 30 COMMENT '速率限制(QPS)',
    `allowed_scopes` JSON COMMENT '允许的权限范围',
    `is_deleted` TINYINT DEFAULT 0,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_key_hash` (`key_hash`(64))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户API密钥表';