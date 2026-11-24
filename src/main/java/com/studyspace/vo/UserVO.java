package com.studyspace.vo;

import com.studyspace.entity.User;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 返回给前端的用户信息
 * <p>刻意不包含 password 字段——密码永远不要出现在任何接口的返回值里</p>
 */
@Data
public class UserVO {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Long id;
    private String studentNo;
    private String nickname;
    private String email;
    private String phone;
    private String role;
    private Integer creditScore;
    /** 禁止预约截止时间，没有限制时为 null */
    private String banUntil;
    private Integer status;
    private String createTime;

    public static UserVO from(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setStudentNo(user.getStudentNo());
        vo.setNickname(user.getNickname());
        vo.setEmail(user.getEmail());
        vo.setPhone(user.getPhone());
        vo.setRole(user.getRole());
        vo.setCreditScore(user.getCreditScore());
        vo.setBanUntil(format(user.getBanUntil()));
        vo.setStatus(user.getStatus());
        vo.setCreateTime(format(user.getCreateTime()));
        return vo;
    }

    private static String format(LocalDateTime time) {
        return time == null ? null : TIME_FORMATTER.format(time);
    }
}
