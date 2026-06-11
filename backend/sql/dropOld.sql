-- 删除旧的用户表（新设计已拆分为父表+子表）
DROP TABLE IF EXISTS sys_user;

-- 删除宿舍业务相关的旧表（重建时使用新结构）
DROP TABLE IF EXISTS dorm_adjust_request;
DROP TABLE IF EXISTS dorm_assignment_result;
DROP TABLE IF EXISTS dorm_assignment_batch;
DROP TABLE IF EXISTS dorm_manager_scope;
DROP TABLE IF EXISTS dorm_room;
DROP TABLE IF EXISTS dorm_floor;
DROP TABLE IF EXISTS dorm_building;
DROP TABLE IF EXISTS dorm_questionnaire_answer;
DROP TABLE IF EXISTS dorm_questionnaire;

-- 确保新表不存在（避免冲突）
DROP TABLE IF EXISTS sys_user_admin;
DROP TABLE IF EXISTS sys_user_student;
DROP TABLE IF EXISTS sys_user_dorm_manager;