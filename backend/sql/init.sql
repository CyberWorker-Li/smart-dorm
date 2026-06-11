-- 原先的init.sql

-- CREATE DATABASE IF NOT EXISTS smart_dorm
-- DEFAULT CHARACTER SET utf8mb4
-- COLLATE utf8mb4_0900_ai_ci;

-- USE smart_dorm;

-- DROP TABLE IF EXISTS sys_user;

-- CREATE TABLE sys_user (
--     id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
--     username VARCHAR(50) NOT NULL UNIQUE COMMENT '登录用户名',
--     password_hash VARCHAR(255) NOT NULL COMMENT '密码哈希',
--     real_name VARCHAR(50) NOT NULL COMMENT '真实姓名',
--     user_no VARCHAR(50) NULL UNIQUE COMMENT '学号/工号/编号',
--     user_type VARCHAR(20) NOT NULL COMMENT 'STUDENT/DORM_MANAGER/SYS_ADMIN',
--     is_leader TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否宿舍长，仅学生有效',
--     enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
--     create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
--     update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
-- ) COMMENT='系统用户表';


-- 新的init.sql

-- 创建数据库
CREATE DATABASE IF NOT EXISTS smart_dorm
DEFAULT CHARACTER SET utf8mb4
COLLATE utf8mb4_0900_ai_ci;

USE smart_dorm;

-- =============================================
-- 用户相关表
-- =============================================

CREATE TABLE sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '登录用户名',
    password_hash VARCHAR(255) NOT NULL COMMENT '密码哈希',
    real_name VARCHAR(50) NOT NULL COMMENT '真实姓名',
    user_no VARCHAR(50) NULL UNIQUE COMMENT '学号/工号/编号',
    user_type VARCHAR(20) NOT NULL COMMENT 'STUDENT/DORM_MANAGER/SYS_ADMIN',
    gender VARCHAR(10) NOT NULL DEFAULT 'MALE' COMMENT 'MALE男/FEMALE女',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) COMMENT='系统用户父表（角色扩展见子表）';

CREATE TABLE sys_user_admin (
    user_id BIGINT PRIMARY KEY COMMENT '关联 sys_user.id',
    CONSTRAINT fk_sys_user_admin_user FOREIGN KEY (user_id) REFERENCES sys_user (id) ON DELETE CASCADE
) COMMENT='管理员扩展表';

CREATE TABLE sys_user_student (
    user_id BIGINT PRIMARY KEY COMMENT '关联 sys_user.id',
    is_leader TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否宿舍长',
    academic_year VARCHAR(20) NOT NULL COMMENT '学年，如 2025-2026',
    CONSTRAINT fk_sys_user_student_user FOREIGN KEY (user_id) REFERENCES sys_user (id) ON DELETE CASCADE
) COMMENT='学生扩展表';

CREATE TABLE sys_user_dorm_manager (
    user_id BIGINT PRIMARY KEY COMMENT '关联 sys_user.id',
    CONSTRAINT fk_sys_user_dorm_manager_user FOREIGN KEY (user_id) REFERENCES sys_user (id) ON DELETE CASCADE
) COMMENT='宿管扩展表';

-- =============================================
-- 宿舍相关表
-- =============================================

CREATE TABLE dorm_questionnaire (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    title VARCHAR(100) NOT NULL COMMENT '问卷标题',
    academic_year VARCHAR(20) NOT NULL COMMENT '学年，如 2025-2026',
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/CLOSED',
    deadline DATETIME NOT NULL COMMENT '截止时间（创建时必填）',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除，1 表示已删除（答卷不删）',
    create_by BIGINT NOT NULL COMMENT '创建人(sys_user.id)',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='问卷模板表';

CREATE TABLE dorm_questionnaire_answer (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    questionnaire_id BIGINT NOT NULL COMMENT '问卷ID',
    student_id BIGINT NOT NULL COMMENT '学生ID(sys_user.id)',
    wake_up_time VARCHAR(10) NULL COMMENT '起床时间，如 07:00',
    sleep_time VARCHAR(10) NULL COMMENT '睡觉时间，如 23:00',
    stay_up_late TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否熬夜',
    smoke TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否吸烟',
    keep_clean TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否爱干净',
    hometown VARCHAR(50) NULL COMMENT '家乡（省/市）',
    major VARCHAR(50) NULL COMMENT '专业',
    personality VARCHAR(20) NULL COMMENT '性格：INTROVERT/EXTROVERT/AMBIVERT',
    hobbies VARCHAR(255) NULL COMMENT '兴趣爱好，逗号分隔',
    self_description TEXT NULL COMMENT '自我优缺点分析',
    dorm_expectation TEXT NULL COMMENT '对宿舍的希望',
    status VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED' COMMENT 'SUBMITTED/RECALLED',
    submit_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_questionnaire_student (questionnaire_id, student_id)
) COMMENT='学生问卷答卷表';

CREATE TABLE dorm_building (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    name VARCHAR(80) NOT NULL COMMENT '楼房名称',
    floor_count INT NOT NULL DEFAULT 0 COMMENT '总层数',
    gender VARCHAR(10) NOT NULL COMMENT 'MALE/FEMALE',
    valid TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否有效',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='宿舍楼表';

CREATE TABLE dorm_floor (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    building_id BIGINT NOT NULL COMMENT '楼房ID',
    seq_no INT NOT NULL DEFAULT 0 COMMENT '序号',
    floor_no INT NOT NULL COMMENT '第几层(从1起)',
    max_rooms INT NOT NULL DEFAULT 0 COMMENT '本层最多房间数',
    valid TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否有效',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_building_floor (building_id, floor_no),
    CONSTRAINT fk_floor_building FOREIGN KEY (building_id) REFERENCES dorm_building (id) ON DELETE CASCADE
) COMMENT='楼层表';

CREATE TABLE dorm_room (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    floor_id BIGINT NOT NULL COMMENT '楼层ID',
    building VARCHAR(80) NOT NULL COMMENT '楼栋显示名，可修改',
    room_no INT NOT NULL COMMENT '本层房间序号，从1起，不得超过楼层max_rooms',
    capacity TINYINT NOT NULL DEFAULT 4 COMMENT '床位数',
    valid TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否有效',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_floor_room (floor_id, room_no),
    CONSTRAINT fk_room_floor FOREIGN KEY (floor_id) REFERENCES dorm_floor (id) ON DELETE CASCADE
) COMMENT='宿舍房间表';

CREATE TABLE dorm_manager_scope (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    manager_id BIGINT NOT NULL COMMENT '宿管 sys_user.id',
    building_id BIGINT NULL COMMENT '整栋楼负责',
    floor_id BIGINT NULL COMMENT '负责某层',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_scope_building FOREIGN KEY (building_id) REFERENCES dorm_building (id) ON DELETE CASCADE,
    CONSTRAINT fk_scope_floor FOREIGN KEY (floor_id) REFERENCES dorm_floor (id) ON DELETE CASCADE
) COMMENT='宿管管辖范围';

CREATE TABLE dorm_assignment_batch (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    questionnaire_id BIGINT NOT NULL COMMENT '关联问卷ID',
    academic_year VARCHAR(20) NOT NULL COMMENT '学年',
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/ARCHIVED',
    remark VARCHAR(255) NULL COMMENT '备注',
    create_by BIGINT NOT NULL COMMENT '操作管理员ID',
    publish_time DATETIME NULL COMMENT '公示时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='宿舍分配批次表';

CREATE TABLE dorm_assignment_result (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    batch_id BIGINT NOT NULL COMMENT '批次ID',
    student_id BIGINT NOT NULL COMMENT '学生ID(sys_user.id)',
    room_id BIGINT NOT NULL COMMENT '房间ID(dorm_room.id)',
    bed_no TINYINT NULL COMMENT '床位号',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_batch_student (batch_id, student_id),
    CONSTRAINT fk_result_room FOREIGN KEY (room_id) REFERENCES dorm_room (id) ON DELETE CASCADE
) COMMENT='宿舍分配结果明细表';

CREATE TABLE dorm_adjust_request (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    batch_id BIGINT NOT NULL COMMENT '批次ID',
    student_id BIGINT NOT NULL COMMENT '申请学生ID',
    current_room_id BIGINT NOT NULL COMMENT '当前分配房间ID',
    target_room_id BIGINT NULL COMMENT '希望调入房间ID',
    reason TEXT NOT NULL COMMENT '申请理由',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED',
    handler_id BIGINT NULL COMMENT '处理宿管ID',
    handle_remark VARCHAR(255) NULL COMMENT '处理备注',
    handle_time DATETIME NULL COMMENT '处理时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_adj_room_curr FOREIGN KEY (current_room_id) REFERENCES dorm_room (id) ON DELETE CASCADE,
    CONSTRAINT fk_adj_room_tgt FOREIGN KEY (target_room_id) REFERENCES dorm_room (id) ON DELETE SET NULL
) COMMENT='宿舍微调申请表';

CREATE TABLE IF NOT EXISTS `dorm_conflict_warning` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `batch_id` bigint NOT NULL COMMENT '分配批次ID',
    `room_id` bigint NOT NULL COMMENT '房间ID',
    `student_id1` bigint NOT NULL COMMENT '学生A',
    `student_id2` bigint NOT NULL COMMENT '学生B',
    `conflict_score` decimal(5,4) COMMENT '冲突分数（0-1，越高越冲突）',
    `description` varchar(255) COMMENT '冲突原因简述',
    `status` varchar(20) DEFAULT 'PENDING' COMMENT 'PENDING/RESOLVED',
    `create_time` datetime,
    `update_time` datetime,
    PRIMARY KEY (`id`),
    KEY `idx_batch` (`batch_id`)
);

-- =============================================
-- 宿舍卫生管理（3.2）
-- =============================================

CREATE TABLE IF NOT EXISTS `dorm_hygiene_rule` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `rule_key` varchar(80) NOT NULL,
    `rule_value` varchar(255) NOT NULL,
    `remark` varchar(255) NULL,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rule_key` (`rule_key`)
) COMMENT='卫生规则配置表';

CREATE TABLE IF NOT EXISTS `dorm_hygiene_duty_task` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `room_id` bigint NOT NULL COMMENT '房间ID(dorm_room.id)',
    `duty_date` date NOT NULL COMMENT '值日日期',
    `duty_user_id` bigint NOT NULL COMMENT '值日学生(sys_user.id)',
    `duty_item` varchar(100) NOT NULL COMMENT '任务内容',
    `status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/COMPLETED/OVERDUE',
    `deadline_time` datetime NOT NULL COMMENT '截止时间',
    `created_by` bigint NOT NULL COMMENT '创建人（宿舍长/系统）',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_room_date` (`room_id`, `duty_date`),
    KEY `idx_room` (`room_id`),
    KEY `idx_duty_user` (`duty_user_id`)
) COMMENT='卫生值日任务表';

CREATE TABLE IF NOT EXISTS `dorm_hygiene_checkin` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `task_id` bigint NOT NULL COMMENT '任务ID(dorm_hygiene_duty_task.id)',
    `user_id` bigint NOT NULL COMMENT '打卡人(sys_user.id)',
    `photo_url` varchar(255) NOT NULL COMMENT '照片URL/路径（应带时间水印）',
    `location_text` varchar(255) NULL COMMENT '定位/地点描述',
    `remark` varchar(255) NULL COMMENT '备注',
    `checkin_time` datetime NOT NULL COMMENT '打卡时间',
    `verify_status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED',
    `verified_by` bigint NULL COMMENT '审核宿管(sys_user.id)',
    `verified_time` datetime NULL COMMENT '审核时间',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_id` (`task_id`),
    KEY `idx_user` (`user_id`)
) COMMENT='卫生打卡记录表';

CREATE TABLE IF NOT EXISTS `dorm_hygiene_score` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `room_id` bigint NOT NULL COMMENT '房间ID(dorm_room.id)',
    `score_date` date NOT NULL COMMENT '评分日期',
    `period_type` varchar(20) NOT NULL COMMENT 'DAILY/INSPECTION',
    `score` int NOT NULL COMMENT '0-100分',
    `source_type` varchar(20) NOT NULL COMMENT 'SYSTEM/DORM_MANAGER',
    `reason` varchar(255) NULL COMMENT '原因/扣分点/建议',
    `inspector_id` bigint NULL COMMENT '评分人(sys_user.id)，系统为0',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_room_date` (`room_id`, `score_date`),
    KEY `idx_score_date` (`score_date`)
) COMMENT='卫生评分表';

CREATE TABLE IF NOT EXISTS `dorm_hygiene_reward_punishment` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `room_id` bigint NOT NULL COMMENT '房间ID(dorm_room.id)',
    `user_id` bigint NULL COMMENT '学生ID，可为空（宿舍维度）',
    `rp_type` varchar(20) NOT NULL COMMENT 'REWARD/PUNISH',
    `points` int NOT NULL COMMENT '奖惩点（正奖负惩）',
    `reason` varchar(255) NOT NULL COMMENT '原因',
    `related_score_id` bigint NULL COMMENT '关联评分ID',
    `created_by` bigint NOT NULL COMMENT '创建人(sys_user.id)，系统为0',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_room` (`room_id`),
    KEY `idx_user` (`user_id`)
) COMMENT='卫生奖惩记录表';

INSERT INTO dorm_hygiene_rule(rule_key, rule_value, remark) VALUES
('hygiene.deadlineHour', '22', '值日截止小时（0-23）'),
('hygiene.doneScore', '100', '全部完成的系统日评分'),
('hygiene.missPenaltyScore', '60', '存在未完成任务的系统日评分'),
('hygiene.redThreshold', '90', '红榜阈值（含）'),
('hygiene.blackThreshold', '70', '黑榜阈值（不含）'),
('hygiene.systemWeight', '0.4', '月度汇总：系统打卡评分权重'),
('hygiene.manualWeight', '0.6', '月度汇总：宿管检查评分权重')
ON DUPLICATE KEY UPDATE rule_value = VALUES(rule_value), remark = VALUES(remark);
-- =============================================
-- 测试数据
-- =============================================

-- 测试账号：admin 密码 admin123456；其余用户密码均为 123456（BCrypt）
INSERT INTO sys_user (id, username, password_hash, real_name, user_no, user_type, gender, enabled) VALUES
(1, 'admin', '$2b$10$XSPhf9RWxqstRchyAnf2UuavB7zkcfBy5HsoCykkEkKOOBjpVOrMu', '系统管理员', 'SYS0001', 'SYS_ADMIN', 'MALE', 1),
(2, 'dorm_mgr_m', '$2b$10$llYkXPzLrnGhd3JKPW2E.eBtEPPBWu/46xD.V/llkz5mGHr3la1ha', '男宿管测试', 'DM-M-01', 'DORM_MANAGER', 'MALE', 1),
(3, 'dorm_mgr_f', '$2b$10$llYkXPzLrnGhd3JKPW2E.eBtEPPBWu/46xD.V/llkz5mGHr3la1ha', '女宿管测试', 'DM-F-01', 'DORM_MANAGER', 'FEMALE', 1),
(4, 'stu_m_leader', '$2b$10$llYkXPzLrnGhd3JKPW2E.eBtEPPBWu/46xD.V/llkz5mGHr3la1ha', '男学生宿舍长', 'STU-M-L-01', 'STUDENT', 'MALE', 1),
(5, 'stu_m', '$2b$10$llYkXPzLrnGhd3JKPW2E.eBtEPPBWu/46xD.V/llkz5mGHr3la1ha', '男学生普通', 'STU-M-01', 'STUDENT', 'MALE', 1),
(6, 'stu_f_leader', '$2b$10$llYkXPzLrnGhd3JKPW2E.eBtEPPBWu/46xD.V/llkz5mGHr3la1ha', '女学生宿舍长', 'STU-F-L-01', 'STUDENT', 'FEMALE', 1),
(7, 'stu_f', '$2b$10$llYkXPzLrnGhd3JKPW2E.eBtEPPBWu/46xD.V/llkz5mGHr3la1ha', '女学生普通', 'STU-F-01', 'STUDENT', 'FEMALE', 1);

INSERT INTO sys_user_admin (user_id) VALUES (1);
INSERT INTO sys_user_dorm_manager (user_id) VALUES (2), (3);
INSERT INTO sys_user_student (user_id, is_leader, academic_year) VALUES
(4, 1, '2025-2026'),
(5, 0, '2025-2026'),
(6, 1, '2025-2026'),
(7, 0, '2025-2026');

ALTER TABLE sys_user AUTO_INCREMENT = 8;

-- 宿舍楼测试数据
INSERT INTO dorm_building (id, name, floor_count, gender, valid) VALUES
(1, '一号男生公寓', 3, 'MALE', 1),
(2, '二号女生公寓', 2, 'FEMALE', 1);

-- 男生楼：3层，每层最多6间；每层插入2间有效测试房（序号1、2）
INSERT INTO dorm_floor (id, building_id, seq_no, floor_no, max_rooms, valid) VALUES
(1, 1, 1, 1, 6, 1),
(2, 1, 2, 2, 6, 1),
(3, 1, 3, 3, 6, 1);

INSERT INTO dorm_room (floor_id, building, room_no, capacity, valid) VALUES
(1, '一号男生公寓', 1, 4, 1),
(1, '一号男生公寓', 2, 4, 1),
(2, '一号男生公寓', 1, 4, 1),
(2, '一号男生公寓', 2, 4, 1),
(3, '一号男生公寓', 1, 4, 1),
(3, '一号男生公寓', 2, 4, 1);

-- 女生楼：2层，每层最多5间；每层2间
INSERT INTO dorm_floor (id, building_id, seq_no, floor_no, max_rooms, valid) VALUES
(4, 2, 1, 1, 5, 1),
(5, 2, 2, 2, 5, 1);

INSERT INTO dorm_room (floor_id, building, room_no, capacity, valid) VALUES
(4, '二号女生公寓', 1, 4, 1),
(4, '二号女生公寓', 2, 4, 1),
(5, '二号女生公寓', 1, 4, 1),
(5, '二号女生公寓', 2, 4, 1);

INSERT INTO dorm_manager_scope (manager_id, building_id, floor_id) VALUES
(2, 1, NULL),
(3, 2, NULL);

-- =============================================
-- 宿舍互换模块（3.4）
-- =============================================

CREATE TABLE IF NOT EXISTS dorm_swap_request (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    batch_id BIGINT NOT NULL,
    student_a_id BIGINT NOT NULL,
    student_a_room_id BIGINT NOT NULL,
    student_b_id BIGINT NOT NULL,
    student_b_room_id BIGINT NOT NULL,
    initiator_remark TEXT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_B_CONFIRM',
    b_confirm_status VARCHAR(20) NULL,
    handler_id BIGINT NULL,
    handle_remark VARCHAR(255) NULL,
    handle_time DATETIME NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_student_a (student_a_id),
    INDEX idx_student_b (student_b_id),
    INDEX idx_batch (batch_id),
    INDEX idx_status (status)
);

CREATE TABLE IF NOT EXISTS dorm_swap_intent (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    batch_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    remark VARCHAR(255) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/CLOSED',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_batch_student (batch_id, student_id),
    INDEX idx_batch (batch_id),
    INDEX idx_student (student_id),
    INDEX idx_status (status)
) COMMENT='换宿意愿（匿名展示）';

CREATE TABLE IF NOT EXISTS dorm_swap_chat_thread (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    batch_id BIGINT NOT NULL,
    starter_id BIGINT NOT NULL COMMENT '发起人学生ID',
    target_student_id BIGINT NOT NULL COMMENT '对方学生ID',
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/CLOSED',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_batch_pair (batch_id, starter_id, target_student_id),
    INDEX idx_batch (batch_id),
    INDEX idx_starter (starter_id),
    INDEX idx_target (target_student_id)
) COMMENT='换宿匿名沟通会话';

CREATE TABLE IF NOT EXISTS dorm_swap_chat_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    thread_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL COMMENT '发送者学生ID',
    content VARCHAR(500) NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_thread (thread_id),
    INDEX idx_sender (sender_id)
) COMMENT='换宿匿名沟通消息';

CREATE TABLE IF NOT EXISTS notify_inbox (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    recipient_user_id BIGINT NOT NULL,
    biz_type VARCHAR(30) NOT NULL,
    biz_id BIGINT NOT NULL,
    title VARCHAR(80) NOT NULL,
    summary VARCHAR(255) NULL,
    read_time DATETIME NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_recipient_read (recipient_user_id, read_time),
    INDEX idx_biz (biz_type, biz_id)
);

CREATE TABLE IF NOT EXISTS noise_request (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    batch_id BIGINT NOT NULL,
    from_room_id BIGINT NOT NULL,
    to_room_id BIGINT NOT NULL,
    content VARCHAR(255) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    ack_time DATETIME NULL,
    escalate_time DATETIME NULL,
    handler_id BIGINT NULL,
    handle_remark VARCHAR(255) NULL,
    handle_time DATETIME NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_from (from_room_id, create_time),
    INDEX idx_to (to_room_id, create_time),
    INDEX idx_status (status, create_time),
    INDEX idx_batch (batch_id)
);

CREATE TABLE IF NOT EXISTS notice (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    publisher_id BIGINT NOT NULL,
    title VARCHAR(80) NOT NULL,
    content TEXT NOT NULL,
    scope_type VARCHAR(20) NOT NULL,
    building_id BIGINT NULL,
    floor_id BIGINT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    publish_time DATETIME NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_publisher (publisher_id, create_time),
    INDEX idx_status (status, publish_time)
);

CREATE TABLE IF NOT EXISTS dorm_rule_proposal (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    batch_id BIGINT NOT NULL,
    room_id BIGINT NOT NULL,
    proposer_student_id BIGINT NOT NULL,
    content VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'VOTING',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_room (batch_id, room_id, create_time),
    INDEX idx_status (status, create_time)
) COMMENT='宿舍公约提案';

CREATE TABLE IF NOT EXISTS dorm_rule_vote (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    proposal_id BIGINT NOT NULL,
    voter_student_id BIGINT NOT NULL,
    agree TINYINT(1) NOT NULL DEFAULT 1,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_proposal_voter (proposal_id, voter_student_id),
    INDEX idx_proposal (proposal_id, create_time)
) COMMENT='宿舍公约投票';

CREATE TABLE IF NOT EXISTS dorm_feedback (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    batch_id BIGINT NOT NULL,
    room_id BIGINT NOT NULL,
    submitter_student_id BIGINT NOT NULL,
    content VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED',
    escalate_time DATETIME NULL,
    resolve_time DATETIME NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_room (batch_id, room_id, create_time),
    INDEX idx_status (status, create_time)
) COMMENT='宿舍内部意见反馈（匿名展示）';

CREATE TABLE IF NOT EXISTS dorm_peer_eval (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    batch_id BIGINT NOT NULL,
    room_id BIGINT NOT NULL,
    submitter_student_id BIGINT NOT NULL,
    month VARCHAR(7) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_eval (batch_id, room_id, submitter_student_id, month),
    INDEX idx_room_month (batch_id, room_id, month)
) COMMENT='宿舍月度互评提交';

CREATE TABLE IF NOT EXISTS dorm_peer_eval_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    eval_id BIGINT NOT NULL,
    target_student_id BIGINT NOT NULL,
    schedule_score INT NOT NULL,
    hygiene_score INT NOT NULL,
    communication_score INT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_eval (eval_id),
    INDEX idx_target (target_student_id)
) COMMENT='宿舍月度互评明细';

CREATE TABLE IF NOT EXISTS dorm_repair_request (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    batch_id BIGINT NOT NULL,
    room_id BIGINT NOT NULL,
    submitter_student_id BIGINT NOT NULL,
    type VARCHAR(20) NOT NULL,
    detail VARCHAR(255) NULL,
    reply VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_room (batch_id, room_id, create_time),
    INDEX idx_status (status, create_time)
) COMMENT='宿舍报修单';

CREATE TABLE IF NOT EXISTS dorm_utility_monthly (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    room_id BIGINT NOT NULL,
    month VARCHAR(7) NOT NULL,
    water DOUBLE NULL,
    power DOUBLE NULL,
    cost DOUBLE NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_room_month (room_id, month),
    INDEX idx_room (room_id, month)
) COMMENT='宿舍水电月度用量';

CREATE TABLE IF NOT EXISTS dorm_utility_threshold (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    room_id BIGINT NOT NULL,
    water_limit DOUBLE NULL,
    power_limit DOUBLE NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_room (room_id)
) COMMENT='宿舍水电阈值设置';

CREATE TABLE IF NOT EXISTS dorm_activity (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(80) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status (status, create_time)
) COMMENT='宿舍活动';

CREATE TABLE IF NOT EXISTS dorm_activity_signup (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    activity_id BIGINT NOT NULL,
    room_id BIGINT NOT NULL,
    signup_student_id BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_activity_room (activity_id, room_id),
    INDEX idx_room (room_id, create_time)
) COMMENT='宿舍活动报名（按宿舍）';

-- =============================================
-- 管理员批量导入用户 CSV 模板格式（对应接口：POST /api/admin/users/import）
-- 文件编码：UTF-8（建议无 BOM）
-- 如使用 Windows Excel 编辑 CSV，推荐直接从系统“下载模板 CSV”，该模板带 UTF-8 BOM，可避免中文乱码。
-- 首行必须为表头（字段可按任意顺序，大小写不敏感）：
-- username,password,realName,userNo,userType,gender,leader,academicYear,enabled
--
-- 字段说明：
-- - username：必填，登录用户名（唯一）
-- - password：可空，空则默认 123456
-- - realName：必填，姓名
-- - userNo：可空，学号/工号/编号（不为空则必须唯一）
-- - userType：必填，枚举：STUDENT / DORM_MANAGER / SYS_ADMIN
-- - gender：必填，枚举：MALE / FEMALE
-- - leader：可空，仅对 STUDENT 有效；true/false/1/0（默认 false）
-- - academicYear：仅对 STUDENT 必填，如 2025-2026；非学生必须留空
-- - enabled：可空，true/false/1/0（默认 true）
-- - 以 # 开头的行会被忽略（可用于写提示行）
-- - username 以 __EXAMPLE__ 或 EXAMPLE_ 开头的行会被忽略（模板自带示例无需删除）
--
-- 模板文件已提供：SmartDorm\group1\backend\sql\user_import_template.csv
