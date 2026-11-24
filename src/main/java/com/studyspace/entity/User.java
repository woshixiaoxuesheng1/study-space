package com.studyspace.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户表实体
 * <p>user 是 MySQL 关键字，@TableName 里用反引号包起来，生成的 SQL 才不会报语法错误</p>
 */
@Data
@TableName("`user`")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 学号，登录账号 */
    private String studentNo;

    /** BCrypt 加密后的密码，禁止存明文 */
    private String password;

    /** 昵称 */
    private String nickname;

    /** 邮箱 */
    private String email;

    /** 手机号 */
    private String phone;

    /** 角色：STUDENT / ADMIN */
    private String role;

    /** 信用分，初始 100 */
    private Integer creditScore;

    /** 禁止预约截止时间，信用分低于 60 时写入 */
    private LocalDateTime banUntil;

    /** 1 正常 / 0 禁用 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
