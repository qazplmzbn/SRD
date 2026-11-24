-- MySQL 8: import once into an empty application database.

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `app_homepage_cards` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `card_key` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '卡片标识',
  `card_label` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '卡片显示名称',
  `card_icon` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Lucide图标名称',
  `card_color` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '图标颜色样式',
  `card_bg` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '背景颜色样式',
  `card_route` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '点击跳转路由',
  `card_description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '卡片描述',
  `card_order` int DEFAULT '0' COMMENT '显示顺序',
  `is_active` tinyint(1) DEFAULT '1' COMMENT '是否启用',
  `is_new` tinyint(1) DEFAULT '0' COMMENT '是否标记为新增',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `card_key` (`card_key`),
  KEY `idx_card_key` (`card_key`),
  KEY `idx_card_order` (`card_order`),
  KEY `idx_is_active` (`is_active`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='应用首页功能卡片表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `app_users` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户名（登录账号）',
  `nickname` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '昵称（展示用）',
  `password_hash` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'bcrypt 加密密码',
  `email` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '邮箱（可选）',
  `avatar_color` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '#C19A83' COMMENT '头像背景色',
  `role` enum('admin','user','researcher','doctor') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'user',
  `is_active` tinyint(1) DEFAULT '1' COMMENT '账户是否启用',
  `last_login_at` datetime DEFAULT NULL COMMENT '最后登录时间',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `username` (`username`),
  UNIQUE KEY `email` (`email`),
  KEY `idx_username` (`username`),
  KEY `idx_email` (`email`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=225 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='应用用户账户表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `archive_import_batch` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `batch_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '批次编号',
  `dataset_key` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '数据集标识',
  `original_filename` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '原始文件名',
  `file_format` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '文件格式',
  `file_size` bigint DEFAULT '0' COMMENT '文件大小',
  `total_rows` int DEFAULT '0' COMMENT '文件总行数',
  `unique_users` int DEFAULT '0' COMMENT '唯一用户数',
  `unique_posts` int DEFAULT '0' COMMENT '唯一帖子数',
  `fine_risk_distribution` json DEFAULT NULL COMMENT '细粒度风险分布',
  `fine_class_count` int DEFAULT NULL COMMENT '细粒度分类数量',
  `fine_labels` json DEFAULT NULL COMMENT '细粒度标签定义',
  `coarse_risk_mapping` json DEFAULT NULL COMMENT '粗粒度映射规则',
  `coarse_risk_distribution` json DEFAULT NULL COMMENT '粗粒度风险分布',
  `post_count` int DEFAULT '1' COMMENT '帖子数量',
  `is_manual_annotation` tinyint(1) DEFAULT '0' COMMENT '是否手工标注',
  `has_timestamp` tinyint(1) DEFAULT '0' COMMENT '是否有时间戳字段',
  `has_emojis` tinyint(1) DEFAULT '0' COMMENT '是否有表情序列字段',
  `selected_fields` json DEFAULT NULL COMMENT '导入的字段列表',
  `accepted_rows` int DEFAULT '0' COMMENT '接受的数据条数',
  `rejected_rows` int DEFAULT '0' COMMENT '拒绝的数据条数',
  `status` enum('uploading','reviewing','committed','failed') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'uploading' COMMENT '状态',
  `error_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '错误信息',
  `committed_at` datetime DEFAULT NULL COMMENT '提交时间',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `batch_code` (`batch_code`),
  KEY `idx_batch_code` (`batch_code`),
  KEY `idx_dataset_key` (`dataset_key`),
  KEY `idx_status` (`status`),
  KEY `idx_created_at` (`created_at`),
  CONSTRAINT `archive_import_batch_ibfk_1` FOREIGN KEY (`dataset_key`) REFERENCES `dataset_profile` (`dataset_key`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='心理档案导入批次表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `care_analysis_grants` (
  `appointment_id` varchar(36) NOT NULL,
  PRIMARY KEY (`appointment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `care_appointments` (
  `id` varchar(36) NOT NULL,
  `patient_id` bigint NOT NULL,
  `doctor_id` bigint NOT NULL,
  `state` varchar(12) NOT NULL,
  `shared` tinyint(1) NOT NULL,
  `scheduled_at` timestamp NOT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `snapshot` mediumtext,
  PRIMARY KEY (`id`),
  KEY `care_patient` (`patient_id`),
  KEY `care_doctor` (`doctor_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `care_booking_consent` (
  `appointment_id` varchar(36) NOT NULL,
  PRIMARY KEY (`appointment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `care_doctors` (
  `doctor_id` bigint NOT NULL,
  `certified_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`doctor_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `care_live_grants` (
  `appointment_id` varchar(36) NOT NULL,
  `source_key` varchar(36) NOT NULL,
  PRIMARY KEY (`appointment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `chat_document_references` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `session_id` bigint NOT NULL COMMENT '关联会话ID',
  `message_id` bigint DEFAULT NULL COMMENT '关联消息ID',
  `doc_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '文档ID',
  `doc_title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文档标题',
  `doc_type` enum('pdf','docx','txt','md') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '文档类型',
  `topic` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '主题分类',
  `sub_topic` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '子主题分类',
  `reference_page` int DEFAULT NULL COMMENT '引用页码',
  `reference_snippet` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '引用片段内容',
  `reference_context` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '引用上下文',
  `relevance_score` decimal(5,4) DEFAULT NULL COMMENT '相关性评分',
  `click_count` int DEFAULT '0' COMMENT '点击次数',
  `preview_count` int DEFAULT '0' COMMENT '预览次数',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_session_id` (`session_id`),
  KEY `idx_message_id` (`message_id`),
  KEY `idx_doc_id` (`doc_id`),
  KEY `idx_relevance_score` (`relevance_score`),
  KEY `idx_created_at` (`created_at`),
  CONSTRAINT `chat_document_references_ibfk_1` FOREIGN KEY (`session_id`) REFERENCES `chat_sessions` (`id`) ON DELETE CASCADE,
  CONSTRAINT `chat_document_references_ibfk_2` FOREIGN KEY (`message_id`) REFERENCES `chat_messages` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档引用表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `chat_knowledge_items` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `session_id` bigint NOT NULL COMMENT '关联会话ID',
  `message_id` bigint DEFAULT NULL COMMENT '关联消息ID',
  `item_type` enum('concept_map','knowledge_table','evidence_quote','background_knowledge') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '知识项类型',
  `concept_nodes` json DEFAULT NULL COMMENT '概念图节点',
  `concept_edges` json DEFAULT NULL COMMENT '概念图边',
  `table_data` json DEFAULT NULL COMMENT '表格数据',
  `quote_content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '引用内容',
  `quote_source` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '引用来源',
  `knowledge_term` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '术语名称',
  `knowledge_definition` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '术语定义',
  `expand_count` int DEFAULT '0' COMMENT '展开次数',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_session_id` (`session_id`),
  KEY `idx_message_id` (`message_id`),
  KEY `idx_item_type` (`item_type`),
  KEY `idx_created_at` (`created_at`),
  CONSTRAINT `chat_knowledge_items_ibfk_1` FOREIGN KEY (`session_id`) REFERENCES `chat_sessions` (`id`) ON DELETE CASCADE,
  CONSTRAINT `chat_knowledge_items_ibfk_2` FOREIGN KEY (`message_id`) REFERENCES `chat_messages` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识清单表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `chat_messages` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `session_id` bigint NOT NULL COMMENT '关联会话ID',
  `role` enum('user','ai','system') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '消息内容',
  `content_type` enum('text','html','markdown') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'text' COMMENT '内容类型',
  `attachments` json DEFAULT NULL COMMENT '附件列表',
  `has_image` tinyint(1) DEFAULT '0' COMMENT '是否包含图片',
  `has_file` tinyint(1) DEFAULT '0' COMMENT '是否包含文件',
  `ai_model` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '使用的AI模型',
  `ai_mode` enum('deep_think','risk_assessment','intervention','scale_interpret') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'AI模式',
  `tokens_used` int DEFAULT NULL COMMENT '本次使用Token数',
  `processing_time_ms` int DEFAULT NULL COMMENT '处理时间',
  `rag_context` json DEFAULT NULL COMMENT 'RAG检索上下文',
  `retrieval_sources` json DEFAULT NULL COMMENT '检索来源文档列表',
  `is_generating` tinyint(1) DEFAULT '0' COMMENT '是否正在生成中',
  `is_streaming` tinyint(1) DEFAULT '0' COMMENT '是否正在流式输出',
  `is_error` tinyint(1) DEFAULT '0' COMMENT '是否错误',
  `error_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '错误信息',
  `references_json` json DEFAULT NULL COMMENT '文档引用',
  `parent_message_id` bigint DEFAULT NULL COMMENT '父消息ID',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_session_id` (`session_id`),
  KEY `idx_role` (`role`),
  KEY `idx_parent_message_id` (`parent_message_id`),
  KEY `idx_created_at` (`created_at`),
  CONSTRAINT `chat_messages_ibfk_1` FOREIGN KEY (`session_id`) REFERENCES `chat_sessions` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=281 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对话消息表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `chat_recommended_questions` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `question` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '问题内容',
  `ai_mode` enum('deep_think','risk_assessment','intervention','scale_interpret','all') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'all' COMMENT '适用AI模式',
  `category` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '问题分类',
  `keywords` json DEFAULT NULL COMMENT '关键词',
  `sort_order` int DEFAULT '0' COMMENT '排序顺序',
  `usage_count` int DEFAULT '0' COMMENT '使用次数',
  `is_active` tinyint(1) DEFAULT '1' COMMENT '是否启用',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_ai_mode` (`ai_mode`),
  KEY `idx_category` (`category`),
  KEY `idx_sort_order` (`sort_order`),
  KEY `idx_usage_count` (`usage_count`),
  KEY `idx_is_active` (`is_active`)
) ENGINE=InnoDB AUTO_INCREMENT=29 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='推荐问题表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `chat_sessions` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `session_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '会话编号',
  `user_id` bigint DEFAULT NULL COMMENT '关联用户ID',
  `user_hash` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '脱敏用户标识',
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '会话标题',
  `archive_id` bigint DEFAULT NULL COMMENT '关联档案ID',
  `data_source` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '数据来源',
  `ai_mode` enum('deep_think','risk_assessment','intervention','scale_interpret') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'deep_think' COMMENT 'AI模式',
  `context_type` enum('general','knowledge_base','archive','scale') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'general' COMMENT '上下文类型',
  `knowledge_sources` json DEFAULT NULL COMMENT '知识库来源',
  `rag_keywords` json DEFAULT NULL COMMENT 'RAG检索关键词',
  `message_count` int DEFAULT '0' COMMENT '消息数量',
  `total_tokens` int DEFAULT '0' COMMENT '总Token数',
  `status` enum('active','archived','deleted') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'active' COMMENT '状态',
  `is_pinned` tinyint(1) DEFAULT '0' COMMENT '是否置顶',
  `last_message_at` datetime DEFAULT NULL COMMENT '最后消息时间',
  `last_ai_response_at` datetime DEFAULT NULL COMMENT '最后AI回复时间',
  `archived_at` datetime DEFAULT NULL COMMENT '归档时间',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `session_code` (`session_code`),
  KEY `idx_session_code` (`session_code`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_user_hash` (`user_hash`),
  KEY `idx_archive_id` (`archive_id`),
  KEY `idx_data_source` (`data_source`),
  KEY `idx_ai_mode` (`ai_mode`),
  KEY `idx_context_type` (`context_type`),
  KEY `idx_status` (`status`),
  KEY `idx_is_pinned` (`is_pinned`),
  KEY `idx_last_message_at` (`last_message_at`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=114 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对话会话表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `custom_dataset_meta` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '数据集名称',
  `table_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '存储表名',
  `mode_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'all' COMMENT '模式类型',
  `row_count` int DEFAULT '0' COMMENT '记录数',
  `columns_info` json DEFAULT NULL COMMENT '字段映射信息',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dataset_profile` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `dataset_key` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '数据集唯一标识',
  `display_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '显示名称',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '系列描述',
  `icon` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'database' COMMENT 'Lucide 图标名称',
  `color` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '#C19A83' COMMENT '主色',
  `bg_color` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'bg-orange-100' COMMENT '背景色',
  `text_color` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'text-orange-700' COMMENT '文字色',
  `language` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '数据语言',
  `class_system` enum('binary','multi-class') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'binary' COMMENT '分类体系',
  `class_count` int DEFAULT '2' COMMENT '分类数量',
  `fine_labels` json DEFAULT NULL COMMENT '细粒度标签定义',
  `coarse_risk_mapping` json DEFAULT NULL COMMENT '粗粒度映射',
  `total_users` int DEFAULT '0' COMMENT '累计用户数',
  `total_posts` int DEFAULT '0' COMMENT '累计帖子数',
  `total_archives` int DEFAULT '0' COMMENT '累计已导入档案数',
  `is_builtin` tinyint(1) DEFAULT '1' COMMENT '是否内置数据集',
  `is_active` tinyint(1) DEFAULT '1' COMMENT '是否启用',
  `sort_order` int DEFAULT '0' COMMENT '排序顺序',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `dataset_key` (`dataset_key`),
  KEY `idx_dataset_key` (`dataset_key`),
  KEY `idx_language` (`language`),
  KEY `idx_class_system` (`class_system`),
  KEY `idx_sort_order` (`sort_order`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据集档案表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `detection_task_types` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `type_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '类型代码',
  `type_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '类型名称',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '类型描述',
  `icon` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '图标',
  `color` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '#1890ff' COMMENT '主题色',
  `sort_order` int DEFAULT '0' COMMENT '排序顺序',
  `is_active` tinyint(1) DEFAULT '1' COMMENT '是否启用',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `type_code` (`type_code`),
  KEY `idx_type_code` (`type_code`),
  KEY `idx_sort_order` (`sort_order`),
  KEY `idx_is_active` (`is_active`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='检测任务类型表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `doctor_application_events` (
  `id` varchar(36) NOT NULL,
  `application_id` varchar(36) NOT NULL,
  `actor_id` bigint NOT NULL,
  `action` varchar(30) NOT NULL,
  `reason` varchar(1000) NOT NULL,
  `created_at` timestamp NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `doctor_applications` (
  `id` varchar(36) NOT NULL,
  `applicant_id` bigint NOT NULL,
  `real_name` varchar(80) NOT NULL,
  `institution` varchar(200) NOT NULL,
  `qualification` varchar(100) NOT NULL,
  `credential_number` varchar(120) NOT NULL,
  `notes` varchar(1000) NOT NULL,
  `status` varchar(20) NOT NULL,
  `submitted_at` timestamp NOT NULL,
  `reviewed_at` timestamp NULL DEFAULT NULL,
  `reviewer_id` bigint DEFAULT NULL,
  `review_reason` varchar(1000) DEFAULT NULL,
  `proof_type` varchar(50) NOT NULL,
  `proof_content` longblob NOT NULL,
  `consent_version` varchar(40) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `douyin_auth_state` (
  `owner_id` bigint NOT NULL,
  `nonce` varchar(64) NOT NULL,
  `expires` bigint NOT NULL,
  PRIMARY KEY (`owner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `douyin_connections` (
  `owner_id` bigint NOT NULL,
  `open_id` varchar(128) NOT NULL,
  `credentials` text NOT NULL,
  `generation` varchar(36) NOT NULL,
  `page_cursor` bigint NOT NULL,
  `next_run` bigint NOT NULL,
  `status` varchar(20) NOT NULL,
  `last_success` bigint NOT NULL,
  PRIMARY KEY (`owner_id`),
  KEY `next_run` (`next_run`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `douyin_metric_checks` (
  `post_id` varchar(36) NOT NULL,
  `next_check` bigint NOT NULL,
  `result` varchar(20) NOT NULL,
  PRIMARY KEY (`post_id`),
  CONSTRAINT `douyin_metric_checks_ibfk_1` FOREIGN KEY (`post_id`) REFERENCES `personal_posts` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `douyin_sync_coverage` (
  `owner_id` bigint NOT NULL,
  `generation` varchar(36) NOT NULL,
  `pages` int NOT NULL,
  `item_count` int NOT NULL,
  `oldest_create_time` bigint DEFAULT NULL,
  `newest_create_time` bigint DEFAULT NULL,
  `upstream_has_more` tinyint(1) NOT NULL,
  `finished` tinyint(1) NOT NULL,
  PRIMARY KEY (`owner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `homepage_summary_stats` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `stat_key` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '统计项标识',
  `stat_label` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '统计项标签',
  `stat_value` int DEFAULT '0' COMMENT '统计数值',
  `stat_unit` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '统计单位',
  `stat_icon` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '图标名称',
  `stat_color` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '#C19A83' COMMENT '主题色',
  `stat_type` enum('count','percentage','currency','text') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'count' COMMENT '统计类型',
  `stat_category` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '所属分类',
  `stat_order` int DEFAULT '0' COMMENT '排序顺序',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `stat_key` (`stat_key`),
  KEY `idx_stat_key` (`stat_key`),
  KEY `idx_stat_category` (`stat_category`),
  KEY `idx_stat_order` (`stat_order`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='首页统计汇总表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `knowledge_document_keywords` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `document_id` bigint NOT NULL COMMENT '关联文档ID',
  `keyword` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '关键词',
  `weight` decimal(3,2) DEFAULT '1.00' COMMENT '权重',
  `is_auto_extracted` tinyint(1) DEFAULT '0' COMMENT '是否自动提取',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_doc_keyword` (`document_id`,`keyword`),
  KEY `idx_document_id` (`document_id`),
  KEY `idx_keyword` (`keyword`),
  CONSTRAINT `knowledge_document_keywords_ibfk_1` FOREIGN KEY (`document_id`) REFERENCES `knowledge_documents` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识文档关键词表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `knowledge_document_versions` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `document_id` bigint NOT NULL COMMENT '关联文档ID',
  `version_number` int NOT NULL COMMENT '版本号',
  `file_path` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文件存储路径',
  `file_size` bigint DEFAULT '0' COMMENT '文件大小',
  `change_summary` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '变更说明',
  `uploaded_by` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '上传者',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_doc_version` (`document_id`,`version_number`),
  KEY `idx_document_id` (`document_id`),
  KEY `idx_version_number` (`version_number`),
  CONSTRAINT `knowledge_document_versions_ibfk_1` FOREIGN KEY (`document_id`) REFERENCES `knowledge_documents` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识文档版本表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `knowledge_documents` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文档标题',
  `topic_id` int DEFAULT NULL COMMENT '关联主题ID',
  `sub_topic_id` int DEFAULT NULL COMMENT '关联子主题ID',
  `keywords` json DEFAULT NULL COMMENT '关键词',
  `format` enum('pdf','docx','txt','md') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文档格式',
  `file_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '原始文件名',
  `file_path` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文件存储路径',
  `file_size` bigint DEFAULT '0' COMMENT '文件大小',
  `size_display` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '显示大小',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '文档描述',
  `file_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'MIME类型',
  `file_hash` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '文件哈希',
  `rag_path` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'RAG知识库路径',
  `upload_status` enum('uploading','uploaded','failed') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'uploading' COMMENT '上传状态',
  `progress` int DEFAULT '0' COMMENT '处理进度',
  `is_indexed` tinyint(1) DEFAULT '0' COMMENT '是否已索引',
  `is_deleted` tinyint(1) DEFAULT '0' COMMENT '是否已删除',
  `deleted_at` datetime DEFAULT NULL COMMENT '删除时间',
  `usage_count` int DEFAULT '0' COMMENT '查阅次数',
  `uploaded_by` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '上传者',
  `uploaded_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_topic_id` (`topic_id`),
  KEY `idx_sub_topic_id` (`sub_topic_id`),
  KEY `idx_format` (`format`),
  KEY `idx_upload_status` (`upload_status`),
  KEY `idx_is_indexed` (`is_indexed`),
  KEY `idx_uploaded_at` (`uploaded_at`),
  CONSTRAINT `knowledge_documents_ibfk_1` FOREIGN KEY (`topic_id`) REFERENCES `knowledge_topics` (`id`) ON DELETE SET NULL,
  CONSTRAINT `knowledge_documents_ibfk_2` FOREIGN KEY (`sub_topic_id`) REFERENCES `knowledge_sub_topics` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=80 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识文档表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `knowledge_keywords` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `keyword` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '关键词',
  `category` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '分类（可为空）',
  `color_class` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'bg-orange-100 text-orange-700' COMMENT '前端样式类',
  `is_hot` tinyint(1) DEFAULT '0' COMMENT '是否热门',
  `usage_count` int DEFAULT '0' COMMENT '使用次数',
  `sort_order` int DEFAULT '0' COMMENT '排序',
  `is_active` tinyint(1) DEFAULT '1' COMMENT '是否启用',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `keyword` (`keyword`),
  KEY `idx_is_active` (`is_active`),
  KEY `idx_is_hot` (`is_hot`),
  KEY `idx_usage_count` (`usage_count`),
  KEY `idx_sort_order` (`sort_order`)
) ENGINE=InnoDB AUTO_INCREMENT=250 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库关键词表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `knowledge_sub_topics` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `topic_id` int NOT NULL COMMENT '关联主题ID',
  `sub_topic_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '子主题名称',
  `sub_topic_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '子主题代码',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '子主题描述',
  `sort_order` int DEFAULT '0' COMMENT '排序顺序',
  `is_active` tinyint(1) DEFAULT '1' COMMENT '是否启用',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_topic_sub` (`topic_id`,`sub_topic_name`),
  KEY `idx_topic_id` (`topic_id`),
  KEY `idx_sub_topic_code` (`sub_topic_code`),
  KEY `idx_sort_order` (`sort_order`),
  KEY `idx_is_active` (`is_active`),
  CONSTRAINT `knowledge_sub_topics_ibfk_1` FOREIGN KEY (`topic_id`) REFERENCES `knowledge_topics` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=113 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识子主题表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `knowledge_topics` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `topic_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主题名称',
  `topic_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主题代码',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '主题描述',
  `icon` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '图标',
  `color` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '#C19A83' COMMENT '主题色',
  `sort_order` int DEFAULT '0' COMMENT '排序顺序',
  `is_active` tinyint(1) DEFAULT '1' COMMENT '是否启用',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `topic_code` (`topic_code`),
  KEY `idx_topic_code` (`topic_code`),
  KEY `idx_sort_order` (`sort_order`),
  KEY `idx_is_active` (`is_active`)
) ENGINE=InnoDB AUTO_INCREMENT=45 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识主题表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `models` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `model_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模型名称',
  `model_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模型代码',
  `model_category` enum('api','local_llm','detection') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模型大类',
  `model_type` enum('api','ollama','transformers','fealearner','emoji') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模型类型',
  `provider` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '提供商',
  `api_key` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'API密钥',
  `api_base_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'API基础URL',
  `config_template` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '配置模板',
  `ollama_model_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Ollama模型名',
  `ollama_base_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'http://localhost:11434' COMMENT 'Ollama服务地址',
  `model_path` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Transformers模型路径',
  `lora_path` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'LoRA权重路径',
  `detection_type` enum('fealearner','emoji') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '检测类型',
  `model_file_path` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '模型文件路径',
  `embedding_file_path` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '嵌入文件路径',
  `supported_datasets` json DEFAULT NULL COMMENT '支持的数据集',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '模型描述',
  `version` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '模型版本',
  `is_available` tinyint(1) DEFAULT '1' COMMENT '是否可用',
  `is_default` tinyint(1) DEFAULT '0' COMMENT '是否默认模型',
  `is_builtin` tinyint(1) DEFAULT '0' COMMENT '是否内置模型',
  `performance_metrics` json DEFAULT NULL COMMENT '性能指标',
  `status` enum('active','inactive','error') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'active' COMMENT '状态',
  `error_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '错误信息',
  `last_used_at` datetime DEFAULT NULL COMMENT '最后使用时间',
  `usage_count` int DEFAULT '0' COMMENT '使用次数',
  `avg_processing_time_ms` int DEFAULT NULL COMMENT '平均处理时间',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `temperature` float DEFAULT '0.7' COMMENT '模型温度参数',
  `max_tokens` int DEFAULT '2048' COMMENT 'Max tokens',
  `top_p` float DEFAULT '0.9' COMMENT 'Nucleus sampling parameter',
  `timeout` int DEFAULT '120' COMMENT 'Request timeout (seconds)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `model_code` (`model_code`),
  KEY `idx_model_category` (`model_category`),
  KEY `idx_model_type` (`model_type`),
  KEY `idx_provider` (`provider`),
  KEY `idx_detection_type` (`detection_type`),
  KEY `idx_is_available` (`is_available`),
  KEY `idx_is_default` (`is_default`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=1392 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型配置表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `personal_posts` (
  `id` varchar(36) NOT NULL,
  `owner_id` bigint NOT NULL,
  `content` text NOT NULL,
  `source` varchar(40) NOT NULL,
  `published_at` timestamp NULL DEFAULT NULL,
  `imported_at` timestamp NOT NULL,
  `content_hash` varchar(64) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_personal_posts_owner_hash` (`owner_id`,`content_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `prompt_templates` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模板名称',
  `task_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务类型',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '模板描述',
  `prompt_content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '提示词模板内容',
  `variables` json DEFAULT NULL COMMENT '变量定义',
  `model_id` int DEFAULT NULL COMMENT '适用模型ID',
  `is_active` tinyint(1) DEFAULT '1' COMMENT '是否启用',
  `usage_count` int DEFAULT '0' COMMENT '使用次数',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_name` (`name`),
  KEY `idx_task_type` (`task_type`),
  KEY `idx_model_id` (`model_id`),
  KEY `idx_is_active` (`is_active`),
  CONSTRAINT `prompt_templates_ibfk_1` FOREIGN KEY (`model_id`) REFERENCES `models` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=277 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='提示词模板表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `psychological_archives` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户ID',
  `dataset_source` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '数据来源',
  `post_count` int DEFAULT '0' COMMENT '贴文数量',
  `risk_level` enum('low','medium','high') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'low' COMMENT '粗粒度风险等级',
  `risk_value` int DEFAULT '0' COMMENT '细粒度风险值',
  `label` int DEFAULT NULL COMMENT '人工标签数字',
  `has_timestamp` tinyint(1) DEFAULT '0' COMMENT '是否有时间戳',
  `post_timestamp_start` datetime DEFAULT NULL COMMENT '用户最早发布时间',
  `post_timestamp_end` datetime DEFAULT NULL COMMENT '用户最晚发布时间',
  `has_emojis` tinyint(1) DEFAULT '0' COMMENT '是否有表情序列',
  `import_batch_id` bigint DEFAULT NULL COMMENT '关联导入批次ID',
  `import_timestamp` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '导入时间',
  `frequent_words` json DEFAULT NULL COMMENT '频繁词汇统计',
  `high_importance_count` int DEFAULT '0' COMMENT '高重要性帖子数量',
  `medium_importance_count` int DEFAULT '0' COMMENT '中等重要性帖子数量',
  `low_importance_count` int DEFAULT '0' COMMENT '低重要性帖子数量',
  `avg_importance_score` decimal(5,4) DEFAULT NULL COMMENT '平均重要性分数',
  `top_posts_summary` json DEFAULT NULL COMMENT 'Top重要性帖子摘要',
  `status` enum('importing','ready','analyzing') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'importing' COMMENT '状态',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `user_id` (`user_id`),
  KEY `import_batch_id` (`import_batch_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_dataset_source` (`dataset_source`),
  KEY `idx_risk_level` (`risk_level`),
  KEY `idx_risk_value` (`risk_value`),
  KEY `idx_label` (`label`),
  KEY `idx_importance_level` (`high_importance_count`),
  KEY `idx_avg_importance_score` (`avg_importance_score`),
  KEY `idx_status` (`status`),
  CONSTRAINT `psychological_archives_ibfk_1` FOREIGN KEY (`import_batch_id`) REFERENCES `archive_import_batch` (`id`) ON DELETE SET NULL,
  CONSTRAINT `psychological_archives_ibfk_2` FOREIGN KEY (`dataset_source`) REFERENCES `dataset_profile` (`dataset_key`)
) ENGINE=InnoDB AUTO_INCREMENT=21209 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='心理档案主表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `psychological_records` (
  `id` int NOT NULL AUTO_INCREMENT,
  `user_id` int NOT NULL,
  `posts_text` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `posts_features` json DEFAULT NULL,
  `risk_level` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `risk_score` float DEFAULT NULL,
  `model_fea_learner_result` json DEFAULT NULL,
  `model_emoji_result` json DEFAULT NULL,
  `final_assessment` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `report_content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `user_id` (`user_id`),
  CONSTRAINT `psychological_records_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `user_profiles` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `research_archive_sources` (
  `owner_id` bigint NOT NULL,
  `kind` varchar(12) NOT NULL,
  `source_id` varchar(120) NOT NULL,
  `name` varchar(180) NOT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`owner_id`,`kind`,`source_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `research_archive_subjects` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `owner_id` bigint NOT NULL,
  `version_id` varchar(36) NOT NULL,
  `subject` varchar(100) NOT NULL,
  `posts_json` longtext NOT NULL,
  PRIMARY KEY (`id`),
  KEY `owner_id` (`owner_id`,`version_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `research_experiment_results` (
  `experiment_id` varchar(36) NOT NULL,
  `sample_index` int NOT NULL,
  `expected_label` int NOT NULL,
  `predicted_label` int DEFAULT NULL,
  `response_text` text,
  `error_message` varchar(300) DEFAULT NULL,
  `elapsed_ms` bigint NOT NULL,
  PRIMARY KEY (`experiment_id`,`sample_index`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `research_experiments` (
  `id` varchar(36) NOT NULL,
  `owner_id` bigint NOT NULL,
  `version_id` varchar(36) NOT NULL,
  `version_name` varchar(160) NOT NULL,
  `dataset_checksum` varchar(64) NOT NULL,
  `model_id` bigint NOT NULL,
  `model_name` varchar(200) NOT NULL,
  `model_snapshot` text NOT NULL,
  `config_hash` varchar(64) NOT NULL,
  `prompt_version` varchar(40) NOT NULL,
  `status` varchar(30) NOT NULL,
  `total` int NOT NULL,
  `completed` int NOT NULL DEFAULT '0',
  `message` varchar(300) DEFAULT NULL,
  `created_at` timestamp NOT NULL,
  `finished_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `research_version_events` (
  `id` varchar(36) NOT NULL,
  `version_id` varchar(36) NOT NULL,
  `actor_id` bigint NOT NULL,
  `target_id` bigint DEFAULT NULL,
  `action` varchar(20) NOT NULL,
  `created_at` timestamp NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `research_version_grants` (
  `version_id` varchar(36) NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`version_id`,`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `research_versions` (
  `id` varchar(36) NOT NULL,
  `owner_id` bigint NOT NULL,
  `name` varchar(100) NOT NULL,
  `version_name` varchar(40) NOT NULL,
  `source` varchar(500) NOT NULL,
  `usage_scope` varchar(1000) NOT NULL,
  `checksum` varchar(64) NOT NULL,
  `sample_count` int NOT NULL,
  `samples_json` longtext NOT NULL,
  `created_at` timestamp NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `owner_id` (`owner_id`,`name`,`version_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `risk_detection_fusion_records` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `task_id` bigint NOT NULL COMMENT '关联任务ID',
  `fusion_model_id` int NOT NULL COMMENT '融合模型ID',
  `sub_task_ids` json DEFAULT NULL COMMENT '参与融合的子任务ID列表',
  `prompt_template_id` bigint DEFAULT NULL COMMENT '使用的提示词模板ID',
  `prompt_content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '实际使用的提示词内容',
  `fusion_params` json DEFAULT NULL COMMENT '融合模型参数',
  `status` enum('pending','running','completed','failed') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'pending' COMMENT '融合状态',
  `started_at` datetime DEFAULT NULL COMMENT '开始时间',
  `completed_at` datetime DEFAULT NULL COMMENT '完成时间',
  `processing_time_ms` int DEFAULT NULL COMMENT '处理耗时',
  `input_summary` json DEFAULT NULL COMMENT '融合输入摘要',
  `output_result` json DEFAULT NULL COMMENT '融合输出结果',
  `output_text` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '融合模型的原始文本输出',
  `fused_risk_level` enum('low','medium','high') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '综合风险等级',
  `fused_risk_score` decimal(5,4) DEFAULT NULL COMMENT '综合风险分数',
  `confidence` int DEFAULT NULL COMMENT '置信度',
  `summary` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '综合评估摘要',
  `model_highlights` json DEFAULT NULL COMMENT '模型亮点',
  `error_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '错误信息',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `prompt_template_id` (`prompt_template_id`),
  KEY `idx_task_id` (`task_id`),
  KEY `idx_fusion_model_id` (`fusion_model_id`),
  KEY `idx_status` (`status`),
  KEY `idx_fused_risk_level` (`fused_risk_level`),
  CONSTRAINT `risk_detection_fusion_records_ibfk_1` FOREIGN KEY (`task_id`) REFERENCES `risk_detection_tasks` (`id`) ON DELETE CASCADE,
  CONSTRAINT `risk_detection_fusion_records_ibfk_2` FOREIGN KEY (`fusion_model_id`) REFERENCES `models` (`id`) ON DELETE CASCADE,
  CONSTRAINT `risk_detection_fusion_records_ibfk_3` FOREIGN KEY (`prompt_template_id`) REFERENCES `prompt_templates` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='融合评估记录表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `risk_detection_history` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `task_id` bigint NOT NULL COMMENT '关联任务ID',
  `archive_id` bigint DEFAULT NULL COMMENT '关联档案ID',
  `user_hash` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '脱敏用户标识',
  `task_mode` enum('single','multi') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '检测模式',
  `model_ids` json DEFAULT NULL COMMENT '使用的模型ID列表',
  `model_names` json DEFAULT NULL COMMENT '使用的模型名称列表',
  `risk_level` enum('low','medium','high') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '风险等级',
  `risk_score` decimal(5,4) DEFAULT NULL COMMENT '风险分数',
  `confidence` int DEFAULT NULL COMMENT '置信度',
  `features` json DEFAULT NULL COMMENT '检测到的特征列表',
  `detection_time` datetime NOT NULL COMMENT '检测时间',
  `data_source` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '数据来源',
  `previous_risk_level` enum('low','medium','high') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '上次检测风险等级',
  `risk_change` enum('increased','decreased','stable') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '风险变化趋势',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `task_id` (`task_id`),
  KEY `idx_user_hash` (`user_hash`),
  KEY `idx_archive_id` (`archive_id`),
  KEY `idx_detection_time` (`detection_time`),
  KEY `idx_risk_level` (`risk_level`),
  KEY `idx_risk_change` (`risk_change`),
  CONSTRAINT `risk_detection_history_ibfk_1` FOREIGN KEY (`task_id`) REFERENCES `risk_detection_tasks` (`id`) ON DELETE CASCADE,
  CONSTRAINT `risk_detection_history_ibfk_2` FOREIGN KEY (`archive_id`) REFERENCES `psychological_archives` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='风险检测历史表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `risk_detection_sub_tasks` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `task_id` bigint NOT NULL COMMENT '关联任务ID',
  `model_id` int NOT NULL COMMENT '模型ID',
  `status` enum('pending','running','completed','failed') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'pending' COMMENT '子任务状态',
  `progress` int DEFAULT '0' COMMENT '子任务进度',
  `error_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '错误信息',
  `started_at` datetime DEFAULT NULL COMMENT '开始时间',
  `completed_at` datetime DEFAULT NULL COMMENT '完成时间',
  `result` json DEFAULT NULL COMMENT '检测结果',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_task_id` (`task_id`),
  KEY `idx_model_id` (`model_id`),
  KEY `idx_status` (`status`),
  CONSTRAINT `risk_detection_sub_tasks_ibfk_1` FOREIGN KEY (`task_id`) REFERENCES `risk_detection_tasks` (`id`) ON DELETE CASCADE,
  CONSTRAINT `risk_detection_sub_tasks_ibfk_2` FOREIGN KEY (`model_id`) REFERENCES `models` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='检测子任务表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `risk_detection_tasks` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `task_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '任务编号',
  `task_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务名称',
  `task_description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '任务描述',
  `task_mode` enum('single','multi','emocc') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务模式',
  `task_type_id` int NOT NULL COMMENT '检测任务类型ID',
  `archive_id` bigint DEFAULT NULL COMMENT '关联档案ID',
  `user_hash` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '脱敏用户标识',
  `data_source` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '数据来源',
  `post_count` int DEFAULT '0' COMMENT '检测的贴文数量',
  `single_model_id` int DEFAULT NULL COMMENT '单模型ID',
  `single_prompt_template_id` bigint DEFAULT NULL COMMENT '单模型提示词模板ID',
  `single_model_params` json DEFAULT NULL COMMENT '单模型执行参数',
  `detection_model_configs` json DEFAULT NULL COMMENT '检测模型配置列表',
  `fusion_model_id` int DEFAULT NULL COMMENT '融合模型ID',
  `fusion_prompt_template_id` bigint DEFAULT NULL COMMENT '融合模型提示词模板ID',
  `fusion_model_params` json DEFAULT NULL COMMENT '融合模型执行参数',
  `progress` int DEFAULT '0' COMMENT '整体进度',
  `status` enum('pending','running','completed','failed','cancelled') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'pending' COMMENT '状态',
  `started_at` datetime DEFAULT NULL COMMENT '实际开始时间',
  `completed_at` datetime DEFAULT NULL COMMENT '完成时间',
  `processing_time_ms` int DEFAULT NULL COMMENT '处理耗时',
  `detection_progress` int DEFAULT '0' COMMENT '检测模型阶段进度',
  `fusion_progress` int DEFAULT '0' COMMENT '融合模型阶段进度',
  `detection_status` enum('pending','running','completed','failed') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'pending' COMMENT '检测阶段状态',
  `fusion_status` enum('pending','running','completed','failed') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'pending' COMMENT '融合阶段状态',
  `error_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '错误信息',
  `result_summary` json DEFAULT NULL COMMENT '结果摘要',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `task_code` (`task_code`),
  KEY `single_prompt_template_id` (`single_prompt_template_id`),
  KEY `fusion_prompt_template_id` (`fusion_prompt_template_id`),
  KEY `idx_task_code` (`task_code`),
  KEY `idx_task_mode` (`task_mode`),
  KEY `idx_task_type_id` (`task_type_id`),
  KEY `idx_archive_id` (`archive_id`),
  KEY `idx_user_hash` (`user_hash`),
  KEY `idx_data_source` (`data_source`),
  KEY `idx_single_model_id` (`single_model_id`),
  KEY `idx_fusion_model_id` (`fusion_model_id`),
  KEY `idx_status` (`status`),
  KEY `idx_fusion_status` (`fusion_status`),
  KEY `idx_created_at` (`created_at`),
  CONSTRAINT `risk_detection_tasks_ibfk_1` FOREIGN KEY (`task_type_id`) REFERENCES `detection_task_types` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `risk_detection_tasks_ibfk_2` FOREIGN KEY (`archive_id`) REFERENCES `psychological_archives` (`id`) ON DELETE SET NULL,
  CONSTRAINT `risk_detection_tasks_ibfk_3` FOREIGN KEY (`single_model_id`) REFERENCES `models` (`id`) ON DELETE SET NULL,
  CONSTRAINT `risk_detection_tasks_ibfk_4` FOREIGN KEY (`single_prompt_template_id`) REFERENCES `prompt_templates` (`id`) ON DELETE SET NULL,
  CONSTRAINT `risk_detection_tasks_ibfk_5` FOREIGN KEY (`fusion_model_id`) REFERENCES `models` (`id`) ON DELETE SET NULL,
  CONSTRAINT `risk_detection_tasks_ibfk_6` FOREIGN KEY (`fusion_prompt_template_id`) REFERENCES `prompt_templates` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=137 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='风险检测任务主表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `scale_definitions` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `scale_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '量表代码',
  `scale_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '量表名称简称',
  `full_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '量表完整名称',
  `category` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'depression' COMMENT '量表类别',
  `question_count` int NOT NULL COMMENT '题目数量',
  `max_score` int NOT NULL COMMENT '满分',
  `threshold` int DEFAULT '10' COMMENT '阈值',
  `estimated_time` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '约5分钟' COMMENT '预计完成时间',
  `color` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '#9b59b6' COMMENT '主题色',
  `bg_color` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '#9b59b6' COMMENT '背景色',
  `is_active` tinyint(1) DEFAULT '1' COMMENT '是否启用',
  `sort_order` int DEFAULT '0' COMMENT '排序顺序',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `scale_code` (`scale_code`),
  KEY `idx_scale_code` (`scale_code`),
  KEY `idx_is_active` (`is_active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='量表定义表（最小元数据）';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `scale_tasks` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `task_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务名称',
  `user_id` bigint DEFAULT NULL COMMENT '用户ID',
  `user_hash` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '脱敏用户标识',
  `user_alias` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '匿名用户' COMMENT '用户昵称',
  `archive_id` bigint DEFAULT NULL COMMENT '关联档案ID',
  `data_source` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '数据来源',
  `data_source_label` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '数据来源显示名称',
  `scale_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '量表代码（冗余存储）',
  `scale_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '量表代码',
  `scale_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '量表名称简称',
  `scale_full_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '量表完整名称',
  `scale_category` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '量表类别',
  `scale_color` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '#C19A83' COMMENT '主题色',
  `scale_bg_color` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '#C19A83' COMMENT '背景色',
  `total_questions` int DEFAULT '0' COMMENT '总题数（冗余存储）',
  `status` enum('pending','in_progress','completed','expired') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'pending' COMMENT '任务状态',
  `progress` int DEFAULT '0' COMMENT '进度',
  `answered_questions` int DEFAULT '0' COMMENT '已答题数',
  `answers` json DEFAULT NULL COMMENT '答案列表',
  `total_score` int DEFAULT NULL COMMENT '总分',
  `risk_level` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '风险等级',
  `assessment_result` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '评估结果详情',
  `started_at` datetime DEFAULT NULL COMMENT '开始时间',
  `completed_at` datetime DEFAULT NULL COMMENT '完成时间',
  `expired_at` datetime DEFAULT NULL COMMENT '过期时间',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_task_name` (`task_name`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_user_hash` (`user_hash`),
  KEY `idx_archive_id` (`archive_id`),
  KEY `idx_data_source` (`data_source`),
  KEY `idx_scale_id` (`scale_id`),
  KEY `idx_scale_code` (`scale_code`),
  KEY `idx_scale_category` (`scale_category`),
  KEY `idx_status` (`status`),
  KEY `idx_risk_level` (`risk_level`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='量表评估任务表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `social_post_details` (
  `post_id` varchar(36) NOT NULL,
  `external_id` varchar(255) NOT NULL,
  `metadata` json NOT NULL,
  PRIMARY KEY (`post_id`),
  CONSTRAINT `social_post_details_ibfk_1` FOREIGN KEY (`post_id`) REFERENCES `personal_posts` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `social_post_metrics` (
  `post_id` varchar(36) NOT NULL,
  `sampled_at` bigint NOT NULL,
  `metrics` json NOT NULL,
  PRIMARY KEY (`post_id`,`sampled_at`),
  CONSTRAINT `social_post_metrics_ibfk_1` FOREIGN KEY (`post_id`) REFERENCES `personal_posts` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `token_blacklist` (
  `id` int NOT NULL AUTO_INCREMENT,
  `token_jti` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Token 的 jti 声明',
  `revoked_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '撤销时间',
  `expires_at` datetime NOT NULL COMMENT 'Token 原定过期时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `token_jti` (`token_jti`),
  KEY `idx_token_jti` (`token_jti`),
  KEY `idx_expires_at` (`expires_at`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='JWT Token 黑名单表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_posts` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `archive_id` bigint NOT NULL COMMENT '关联档案ID',
  `user_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户ID',
  `post_index` int DEFAULT '0' COMMENT '贴文序号',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '贴文内容',
  `sentiment_score` decimal(5,4) DEFAULT NULL COMMENT '情感分数',
  `importance_score` decimal(5,4) DEFAULT NULL COMMENT '重要性分数',
  `importance_level` enum('low','medium','high') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '重要性等级',
  `micro_expressions` json DEFAULT NULL COMMENT '微表情序列',
  `post_timestamp` datetime DEFAULT NULL COMMENT '发布时间',
  `emoji_count` int DEFAULT '0' COMMENT '表情个数',
  `emoji_sequence` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '表情序列',
  `fine_risk_value` int DEFAULT NULL COMMENT '细粒度风险值',
  `review_status` enum('pending','accepted','rejected') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'pending' COMMENT '审核状态',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_archive_id` (`archive_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_post_index` (`post_index`),
  KEY `idx_importance_score` (`importance_score`),
  KEY `idx_importance_level` (`importance_level`),
  KEY `idx_fine_risk_value` (`fine_risk_value`),
  KEY `idx_review_status` (`review_status`),
  CONSTRAINT `user_posts_ibfk_1` FOREIGN KEY (`archive_id`) REFERENCES `psychological_archives` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=161963 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户贴文明细表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_profiles` (
  `id` int NOT NULL AUTO_INCREMENT,
  `user_hash` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `dataset_source` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `gender` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `age` int DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `user_hash` (`user_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `weibo_connections` (
  `owner_id` bigint NOT NULL,
  `credentials` text NOT NULL,
  `uid` varchar(128) NOT NULL,
  `status` varchar(24) NOT NULL,
  `next_run` bigint NOT NULL,
  `last_success` bigint NOT NULL,
  `page_no` int NOT NULL,
  `page_cursor` varchar(128) NOT NULL,
  `initial_done` tinyint(1) NOT NULL,
  `auth_expires` bigint NOT NULL,
  `poll_seconds` int NOT NULL,
  PRIMARY KEY (`owner_id`),
  KEY `next_run` (`next_run`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;


-- Public seed data

-- Match the appointment schema loaded by the Java backend.
CREATE TABLE care_appointment_reviews (
  appointment_id VARCHAR(36) PRIMARY KEY, reviewer_id BIGINT NOT NULL,
  decision VARCHAR(8) NOT NULL, opinion VARCHAR(1000) NOT NULL,
  reviewed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE admin_appointment_opinions (
  appointment_id VARCHAR(36) PRIMARY KEY, reviewer_id BIGINT NOT NULL,
  opinion VARCHAR(1000) NOT NULL, reviewed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

INSERT INTO `app_homepage_cards` (`id`, `card_key`, `card_label`, `card_icon`, `card_color`, `card_bg`, `card_route`, `card_description`, `card_order`, `is_active`, `is_new`, `created_at`, `updated_at`) VALUES (1,'archive','心理档案','UserSquare2','text-teal-600','bg-teal-100','/archive','管理心理档案',1,1,0,'2026-04-21 00:17:50','2026-04-21 00:20:21'),(2,'scale','心理量表','ListChecks','text-purple-600','bg-purple-100','/scale','量表评估',2,1,0,'2026-04-21 00:17:50','2026-04-21 00:20:21'),(3,'risk','风险检测','ShieldCheck','text-red-600','bg-red-100','/risk','自杀风险检测',3,1,0,'2026-04-21 00:17:50','2026-04-21 00:20:21'),(4,'knowledge','知识库','BookOpen','text-orange-600','bg-orange-100','/knowledge','心理健康知识库',4,1,0,'2026-04-21 00:17:50','2026-04-21 00:20:21'),(5,'chat','智能问答','MessageCircle','text-blue-600','bg-blue-100','/chat','AI 智能问答',5,1,0,'2026-04-21 00:17:50','2026-04-21 00:20:21'),(6,'model','模型中心','Settings','text-gray-600','bg-gray-100','/model','模型配置管理',6,1,0,'2026-04-21 00:17:50','2026-04-21 00:20:21'),(7,'map','心理援助地图','MapPin','text-pink-600','bg-pink-100','/map','心理援助机构地图',7,1,0,'2026-04-21 00:17:50','2026-04-21 00:20:21');

INSERT INTO `detection_task_types` (`id`, `type_code`, `type_name`, `description`, `icon`, `color`, `sort_order`, `is_active`, `created_at`, `updated_at`) VALUES (1,'suicide','自杀风险检测','自杀风险检测任务',NULL,'#1890ff',0,1,'2026-04-21 01:01:45','2026-04-21 01:01:45');
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

-- Public catalog only. Configure credentials separately after deployment.
INSERT INTO homepage_summary_stats
    (stat_key, stat_label, stat_category, stat_type, stat_order, updated_at)
VALUES
    ('knowledge_base_docs', '知识文档数', 'core_stats', 'count', 1, '2000-01-01'),
    ('total_archives', '档案用户数', 'core_stats', 'count', 2, '2000-01-01'),
    ('total_posts', '研究贴文数', 'core_stats', 'count', 3, '2000-01-01'),
    ('total_scales', '已完成测评数', 'core_stats', 'count', 4, '2000-01-01'),
    ('reports_generated', '已生成报告数', 'core_stats', 'count', 5, '2000-01-01'),
    ('risk_low_count', '低风险档案数', 'risk_distribution', 'count', 6, '2000-01-01'),
    ('risk_low_percentage', '低风险占比', 'risk_distribution', 'percentage', 7, '2000-01-01'),
    ('risk_medium_count', '中风险档案数', 'risk_distribution', 'count', 8, '2000-01-01'),
    ('risk_medium_percentage', '中风险占比', 'risk_distribution', 'percentage', 9, '2000-01-01'),
    ('risk_high_count', '高风险档案数', 'risk_distribution', 'count', 10, '2000-01-01'),
    ('risk_high_percentage', '高风险占比', 'risk_distribution', 'percentage', 11, '2000-01-01');

INSERT INTO `models` (`model_name`,`model_code`,`model_category`,`model_type`,`detection_type`,`supported_datasets`,`is_builtin`) VALUES
('Emocc-Weibo','emocc-weibo','detection','emoji','emoji',JSON_ARRAY('weibo'),1),
('FeaLearner-Weibo','fealearner-weibo','detection','fealearner','fealearner',JSON_ARRAY('weibo'),1);
INSERT INTO `models` (`model_name`,`model_code`,`model_category`,`model_type`,`provider`,`api_base_url`,`is_builtin`,`is_available`,`status`) VALUES
('Qwen Flash','qwen-flash','api','api','DashScope','https://dashscope.aliyuncs.com/compatible-mode/v1',1,0,'inactive');

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

