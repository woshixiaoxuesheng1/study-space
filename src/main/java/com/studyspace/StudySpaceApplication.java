package com.studyspace;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 启动类
 * <p>@MapperScan 指定 Mapper 接口所在包，这样每个 Mapper 上就不用再写 @Mapper</p>
 * <p>排除 UserDetailsServiceAutoConfiguration：本项目用 JWT 自己做认证，
 * 不排除的话启动日志会打印一个随机的默认密码，容易让人误以为有默认账号</p>
 * <p>@EnableScheduling：开启定时任务，预约超时未签到、到点自动结束靠它每分钟检查一次</p>
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@MapperScan("com.studyspace.mapper")
@EnableScheduling
public class StudySpaceApplication {

    public static void main(String[] args) {
        SpringApplication.run(StudySpaceApplication.class, args);
    }

}
