-- =============================================================
--  校园自习室智能预约与座位调度系统  数据库初始化脚本
--  数据库：MySQL 8.0    字符集：utf8mb4
--  对应文档：需求说明 第6章「数据库只要 6 张表」
--  说明：脚本可重复执行（会先删表再重建），执行后自带演示数据
--  注意：user 是 MySQL 关键字，代码里访问时请写成 `user`
-- =============================================================

CREATE DATABASE IF NOT EXISTS `study_space`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_general_ci;

USE `study_space`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `notification`;
DROP TABLE IF EXISTS `violation`;
DROP TABLE IF EXISTS `booking`;
DROP TABLE IF EXISTS `seat`;
DROP TABLE IF EXISTS `room`;
DROP TABLE IF EXISTS `user`;

-- -------------------------------------------------------------
-- 1. user 用户表：学生 + 管理员
-- -------------------------------------------------------------
CREATE TABLE `user` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `student_no`   VARCHAR(20)  NOT NULL                COMMENT '学号，登录账号（管理员用 A0001）',
    `password`     VARCHAR(100) NOT NULL                COMMENT 'BCrypt 加密后的密码，禁止存明文',
    `nickname`     VARCHAR(50)  NOT NULL                COMMENT '昵称',
    `email`        VARCHAR(100)          DEFAULT NULL   COMMENT '邮箱，用于预约通知',
    `phone`        VARCHAR(20)           DEFAULT NULL   COMMENT '手机号，选填',
    `role`         VARCHAR(20)  NOT NULL DEFAULT 'STUDENT' COMMENT '角色：STUDENT 学生 / ADMIN 管理员',
    `credit_score` INT          NOT NULL DEFAULT 100    COMMENT '信用分，满分 100，违约一次扣 10',
    `ban_until`    DATETIME              DEFAULT NULL   COMMENT '禁止预约截止时间，信用分低于 60 时写入',
    `status`       TINYINT      NOT NULL DEFAULT 1      COMMENT '账号状态：1 正常 / 0 禁用',
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_student_no` (`student_no`),
    KEY `idx_role` (`role`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='用户表（学生、管理员）';

-- -------------------------------------------------------------
-- 2. room 自习室表
-- -------------------------------------------------------------
CREATE TABLE `room` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`        VARCHAR(50)  NOT NULL                COMMENT '自习室名称',
    `building`    VARCHAR(50)  NOT NULL                COMMENT '楼栋',
    `floor`       INT          NOT NULL                COMMENT '楼层',
    `open_time`   TIME         NOT NULL DEFAULT '08:00:00' COMMENT '每日开放时间',
    `close_time`  TIME         NOT NULL DEFAULT '22:00:00' COMMENT '每日关闭时间',
    `seat_count`  INT          NOT NULL DEFAULT 0      COMMENT '座位总数（生成座位后回写）',
    `status`      VARCHAR(20)  NOT NULL DEFAULT 'OPEN' COMMENT '状态：OPEN 开放 / CLOSED 关闭 / MAINTENANCE 维护中',
    `description` VARCHAR(255)          DEFAULT NULL   COMMENT '备注说明',
    `deleted`     TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_status_deleted` (`status`, `deleted`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='自习室表';

-- -------------------------------------------------------------
-- 3. seat 座位表：位置（第几行第几列）给前端画座位图用
-- -------------------------------------------------------------
CREATE TABLE `seat` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `room_id`     BIGINT      NOT NULL                COMMENT '所属自习室 id',
    `seat_no`     VARCHAR(20) NOT NULL                COMMENT '座位号，如 A01',
    `row_no`      INT         NOT NULL                COMMENT '第几行，从 1 开始',
    `col_no`      INT         NOT NULL                COMMENT '第几列，从 1 开始',
    `seat_type`   VARCHAR(20) NOT NULL DEFAULT 'NORMAL' COMMENT '座位类型：NORMAL 普通 / WINDOW 靠窗 / POWER 带插座',
    `status`      VARCHAR(20) NOT NULL DEFAULT 'FREE' COMMENT '显示状态：FREE 空闲 / BOOKED 已预约 / IN_USE 使用中 / DISABLED 停用',
    `deleted`     TINYINT     NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_room_seat_no` (`room_id`, `seat_no`),
    KEY `idx_room_status` (`room_id`, `status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='座位表（status 只用于页面展示，能否预约以 booking 表为准）';

-- -------------------------------------------------------------
-- 4. booking 预约表：系统核心表
--    冲突判断：new.start_time < old.end_time AND new.end_time > old.start_time
-- -------------------------------------------------------------
CREATE TABLE `booking` (
    `id`             BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `booking_no`     VARCHAR(32) NOT NULL                COMMENT '预约编号，给学生看的，如 BK20261125001',
    `user_id`        BIGINT      NOT NULL                COMMENT '预约人 id',
    `seat_id`        BIGINT      NOT NULL                COMMENT '座位 id',
    `start_time`     DATETIME    NOT NULL                COMMENT '开始时间',
    `end_time`       DATETIME    NOT NULL                COMMENT '结束时间',
    `status`         VARCHAR(20) NOT NULL DEFAULT 'PENDING_CHECKIN' COMMENT '状态：PENDING_CHECKIN / CHECKED_IN / COMPLETED / CANCELLED / NO_SHOW',
    `check_in_code`  VARCHAR(8)           DEFAULT NULL   COMMENT '签到码，4 位数字',
    `check_in_time`  DATETIME             DEFAULT NULL   COMMENT '实际签到时间',
    `check_out_time` DATETIME             DEFAULT NULL   COMMENT '实际签退时间',
    `cancel_reason`  VARCHAR(255)         DEFAULT NULL   COMMENT '取消原因',
    `create_time`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_booking_no` (`booking_no`),
    KEY `idx_seat_time` (`seat_id`, `start_time`, `end_time`),
    KEY `idx_user_time` (`user_id`, `start_time`),
    KEY `idx_status_end` (`status`, `end_time`),
    CONSTRAINT `ck_booking_time` CHECK (`start_time` < `end_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='预约表';

-- -------------------------------------------------------------
-- 5. violation 违约记录表
-- -------------------------------------------------------------
CREATE TABLE `violation` (
    `id`           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`      BIGINT      NOT NULL                COMMENT '违约人 id',
    `booking_id`   BIGINT               DEFAULT NULL   COMMENT '关联预约 id，管理员手动扣分时为空',
    `type`         VARCHAR(30) NOT NULL                COMMENT '类型：NO_SHOW 超时未签到 / LATE_CANCEL 临近取消 / MANUAL 管理员手动扣分',
    `deduct_score` INT         NOT NULL DEFAULT 10     COMMENT '本次扣减信用分',
    `happen_time`  DATETIME    NOT NULL                COMMENT '违约发生时间',
    `reason`       VARCHAR(255)         DEFAULT NULL   COMMENT '说明',
    `revoked`      TINYINT     NOT NULL DEFAULT 0      COMMENT '是否已撤销：0 否 / 1 是',
    `revoke_time`  DATETIME             DEFAULT NULL   COMMENT '撤销时间',
    `operator_id`  BIGINT               DEFAULT NULL   COMMENT '操作人（撤销或手动扣分的管理员）id',
    `create_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user` (`user_id`),
    KEY `idx_booking` (`booking_id`),
    KEY `idx_type_time` (`type`, `happen_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='违约记录表';

-- -------------------------------------------------------------
-- 6. notification 通知表
-- -------------------------------------------------------------
CREATE TABLE `notification` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`     BIGINT       NOT NULL                COMMENT '接收人 id',
    `title`       VARCHAR(100) NOT NULL                COMMENT '标题',
    `content`     VARCHAR(500) NOT NULL                COMMENT '正文',
    `type`        VARCHAR(30)  NOT NULL DEFAULT 'SYSTEM' COMMENT '类型：BOOKING 预约 / VIOLATION 违约 / CREDIT 信用分 / SYSTEM 系统',
    `is_read`     TINYINT      NOT NULL DEFAULT 0      COMMENT '是否已读：0 未读 / 1 已读',
    `read_time`   DATETIME              DEFAULT NULL   COMMENT '阅读时间',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_read` (`user_id`, `is_read`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='通知表';

-- =============================================================
--  基础数据
-- =============================================================

-- 用户：1 个管理员 + 3 个学生，初始密码统一为 123456（BCrypt 加密）
INSERT INTO `user` (`id`, `student_no`, `password`, `nickname`, `email`, `phone`, `role`, `credit_score`, `ban_until`) VALUES
(1, 'A0001',       '$2a$10$oVw0RyPSt0BtXoag5OfDHuIvpiovAl3qwzez104y3wKMcjyKRbIBO', '系统管理员', 'admin@campus.edu.cn',    '13800000000', 'ADMIN',   100, NULL),
(2, '20230101001', '$2a$10$ZjD2jPtH3weLox698.9RzOf4uoHJRYM92pZhMiR7k.xa1T7rkpKSW', '张三',       'zhangsan@campus.edu.cn', '13800000001', 'STUDENT', 100, NULL),
(3, '20230101002', '$2a$10$BU1Q5Uy90ts/4aDBSEDbsOJ70kUIyhJkDO1rxs6ylWm5AKTrgRpwe', '李四',       'lisi@campus.edu.cn',     '13800000002', 'STUDENT',  90, NULL),
(4, '20230101003', '$2a$10$fUbD0QRC2R.Wan5UJHlIaOw2YZ8bO.mP66alWQxRUYb4wsP7lDAI6', '王五',       'wangwu@campus.edu.cn',   '13800000003', 'STUDENT',  50, DATE_ADD(NOW(), INTERVAL 7 DAY));

-- 自习室：3 间开放 + 1 间维护中（用于演示状态过滤）
INSERT INTO `room` (`id`, `name`, `building`, `floor`, `open_time`, `close_time`, `status`, `description`) VALUES
(1, '图书馆三楼自习室',   '图书馆',   3, '08:00:00', '22:00:00', 'OPEN',        '普通自习室，共 4 行 5 列'),
(2, '图书馆四楼静音自习室', '图书馆',  4, '08:00:00', '22:30:00', 'OPEN',        '禁止交谈，手机静音'),
(3, '教学楼B201考研自习室', '教学楼B',  2, '07:30:00', '23:00:00', 'OPEN',        '考研专用，共 4 行 6 列'),
(4, '旧馆一楼自习室',     '旧图书馆', 1, '08:00:00', '18:00:00', 'MAINTENANCE', '空调检修中，暂不开放');

-- 座位按"行 × 列"批量生成，座位号形如 A01，id 显式指定便于预约单引用
-- 编号规则：id = 房间基数 + (行-1) * 列数 + 列
-- 1 号自习室：4 行 5 列 = 20 个，id 1-20
INSERT INTO `seat` (`id`, `room_id`, `seat_no`, `row_no`, `col_no`, `seat_type`)
SELECT (r.n - 1) * 5 + c.n, 1, CONCAT(CHAR(64 + r.n), LPAD(c.n, 2, '0')), r.n, c.n,
       CASE WHEN c.n = 1 THEN 'WINDOW' WHEN c.n = 5 THEN 'POWER' ELSE 'NORMAL' END
FROM (SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4) r
CROSS JOIN (SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5) c;

-- 2 号自习室：3 行 4 列 = 12 个，id 101-112
INSERT INTO `seat` (`id`, `room_id`, `seat_no`, `row_no`, `col_no`, `seat_type`)
SELECT 100 + (r.n - 1) * 4 + c.n, 2, CONCAT(CHAR(64 + r.n), LPAD(c.n, 2, '0')), r.n, c.n,
       CASE WHEN c.n = 1 THEN 'WINDOW' WHEN c.n = 2 THEN 'POWER' ELSE 'NORMAL' END
FROM (SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3) r
CROSS JOIN (SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4) c;

-- 3 号自习室：4 行 6 列 = 24 个，id 201-224
INSERT INTO `seat` (`id`, `room_id`, `seat_no`, `row_no`, `col_no`, `seat_type`)
SELECT 200 + (r.n - 1) * 6 + c.n, 3, CONCAT(CHAR(64 + r.n), LPAD(c.n, 2, '0')), r.n, c.n,
       CASE WHEN c.n = 1 THEN 'WINDOW' WHEN c.n = 6 THEN 'POWER' ELSE 'NORMAL' END
FROM (SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4) r
CROSS JOIN (SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6) c;

-- 回写每个自习室的座位总数
UPDATE `room` r SET r.`seat_count` = (SELECT COUNT(*) FROM `seat` s WHERE s.`room_id` = r.`id` AND s.`deleted` = 0);

-- 预约单：覆盖全部 5 种状态，时间相对 CURDATE() 生成，随便哪天执行都能直接用
INSERT INTO `booking` (`id`, `booking_no`, `user_id`, `seat_id`, `start_time`, `end_time`, `status`, `check_in_code`, `check_in_time`, `check_out_time`, `cancel_reason`, `create_time`) VALUES
-- 已完成：张三昨天 09:00-11:00，正常签到签退
(1, 'BK20261123001', 2, 1,
    TIMESTAMP(CURDATE() - INTERVAL 1 DAY, '09:00:00'), TIMESTAMP(CURDATE() - INTERVAL 1 DAY, '11:00:00'),
    'COMPLETED', '6821', TIMESTAMP(CURDATE() - INTERVAL 1 DAY, '08:52:00'), TIMESTAMP(CURDATE() - INTERVAL 1 DAY, '11:03:00'), NULL,
    TIMESTAMP(CURDATE() - INTERVAL 2 DAY, '20:15:00')),
-- 违约：李四昨天 14:00-16:00 没来签到
(2, 'BK20261123002', 3, 12,
    TIMESTAMP(CURDATE() - INTERVAL 1 DAY, '14:00:00'), TIMESTAMP(CURDATE() - INTERVAL 1 DAY, '16:00:00'),
    'NO_SHOW', '3095', NULL, NULL, NULL,
    TIMESTAMP(CURDATE() - INTERVAL 2 DAY, '21:40:00')),
-- 待签到：张三今天 15:00-17:00，签到窗口 14:45-15:15，演示签到用
(3, 'BK20261124001', 2, 2,
    TIMESTAMP(CURDATE(), '15:00:00'), TIMESTAMP(CURDATE(), '17:00:00'),
    'PENDING_CHECKIN', '4471', NULL, NULL, NULL,
    TIMESTAMP(CURDATE(), '09:12:00')),
-- 使用中：李四今天 14:00-16:00 已签到
(4, 'BK20261124002', 3, 12,
    TIMESTAMP(CURDATE(), '14:00:00'), TIMESTAMP(CURDATE(), '16:00:00'),
    'CHECKED_IN', '8823', TIMESTAMP(CURDATE(), '13:58:00'), NULL, NULL,
    TIMESTAMP(CURDATE(), '09:30:00')),
-- 已取消：王五明天 10:00-12:00 主动取消
(5, 'BK20261124003', 4, 20,
    TIMESTAMP(CURDATE() + INTERVAL 1 DAY, '10:00:00'), TIMESTAMP(CURDATE() + INTERVAL 1 DAY, '12:00:00'),
    'CANCELLED', '1207', NULL, NULL, '临时有事，提前取消',
    TIMESTAMP(CURDATE(), '10:05:00')),
-- 以下 4 条是王五的历史违约记录（对应 violation 表），座位在 3 号考研自习室
(6, 'BK20261118001', 4, 201,
    TIMESTAMP(CURDATE() - INTERVAL 6 DAY, '09:00:00'), TIMESTAMP(CURDATE() - INTERVAL 6 DAY, '11:00:00'),
    'NO_SHOW', '5512', NULL, NULL, NULL, TIMESTAMP(CURDATE() - INTERVAL 7 DAY, '18:20:00')),
(7, 'BK20261119001', 4, 205,
    TIMESTAMP(CURDATE() - INTERVAL 5 DAY, '10:00:00'), TIMESTAMP(CURDATE() - INTERVAL 5 DAY, '12:00:00'),
    'NO_SHOW', '7340', NULL, NULL, NULL, TIMESTAMP(CURDATE() - INTERVAL 6 DAY, '19:05:00')),
(8, 'BK20261120001', 4, 211,
    TIMESTAMP(CURDATE() - INTERVAL 4 DAY, '13:00:00'), TIMESTAMP(CURDATE() - INTERVAL 4 DAY, '15:00:00'),
    'NO_SHOW', '2268', NULL, NULL, NULL, TIMESTAMP(CURDATE() - INTERVAL 5 DAY, '20:30:00')),
(9, 'BK20261121001', 4, 221,
    TIMESTAMP(CURDATE() - INTERVAL 3 DAY, '15:00:00'), TIMESTAMP(CURDATE() - INTERVAL 3 DAY, '17:00:00'),
    'NO_SHOW', '9034', NULL, NULL, NULL, TIMESTAMP(CURDATE() - INTERVAL 4 DAY, '21:00:00'));

-- 座位显示状态与预约保持一致：2 号座位待签到 → 已预约；12 号座位已签到 → 使用中
UPDATE `seat` SET `status` = 'BOOKED' WHERE `id` = 2;
UPDATE `seat` SET `status` = 'IN_USE' WHERE `id` = 12;

-- 违约记录：李四 1 次 + 王五 4 次 + 管理员手动扣分 1 次 = 王五信用分 50
INSERT INTO `violation` (`user_id`, `booking_id`, `type`, `deduct_score`, `happen_time`, `reason`, `operator_id`) VALUES
(3, 2, 'NO_SHOW', 10, TIMESTAMP(CURDATE() - INTERVAL 1 DAY, '16:15:00'), '超过签到截止时间仍未签到，座位自动释放', NULL),
(4, 6, 'NO_SHOW', 10, TIMESTAMP(CURDATE() - INTERVAL 6 DAY, '11:15:00'), '超过签到截止时间仍未签到，座位自动释放', NULL),
(4, 7, 'NO_SHOW', 10, TIMESTAMP(CURDATE() - INTERVAL 5 DAY, '12:15:00'), '超过签到截止时间仍未签到，座位自动释放', NULL),
(4, 8, 'NO_SHOW', 10, TIMESTAMP(CURDATE() - INTERVAL 4 DAY, '15:15:00'), '超过签到截止时间仍未签到，座位自动释放', NULL),
(4, 9, 'NO_SHOW', 10, TIMESTAMP(CURDATE() - INTERVAL 3 DAY, '17:15:00'), '超过签到截止时间仍未签到，座位自动释放', NULL),
(4, NULL, 'MANUAL', 10, TIMESTAMP(CURDATE() - INTERVAL 2 DAY, '10:00:00'), '多次占座不来，管理员手动扣分', 1);

-- 通知
INSERT INTO `notification` (`user_id`, `title`, `content`, `type`, `is_read`, `read_time`) VALUES
(2, '预约成功', '您已成功预约【图书馆三楼自习室 A02】座位，时间 15:00-17:00，请在开始前 15 分钟至开始后 15 分钟内到场签到，签到码 4471。', 'BOOKING', 0, NULL),
(2, '预约已完成', '您昨日 09:00-11:00 的预约已正常签退，本次使用已计入按时完成记录。', 'BOOKING', 1, TIMESTAMP(CURDATE() - INTERVAL 1 DAY, '11:05:00')),
(3, '签到成功', '您已成功签到【图书馆三楼自习室 C02】，离开时请记得签退。', 'BOOKING', 1, TIMESTAMP(CURDATE(), '13:59:00')),
(3, '违约提醒', '您昨日 14:00-16:00 的预约未签到，已记违约一次并扣 10 分，当前信用分 90。', 'VIOLATION', 0, NULL),
(4, '信用分低于限制线', '您的信用分已降至 50 分，7 天内无法预约座位，到期自动恢复。请按时签到以恢复信用。', 'CREDIT', 0, NULL);

SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================
--  自检：执行完可跑这几句确认
-- =============================================================
-- SELECT COUNT(*) AS 表数量 FROM information_schema.tables WHERE table_schema = 'study_space';
-- SELECT COUNT(*) AS 用户数 FROM `user`;
-- SELECT COUNT(*) AS 座位数 FROM `seat`;
-- SELECT status, COUNT(*) FROM `booking` GROUP BY status;
