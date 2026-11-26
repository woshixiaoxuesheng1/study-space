package com.studyspace.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.studyspace.common.BusinessException;
import com.studyspace.common.ResultCode;
import com.studyspace.constants.UserConstants;
import com.studyspace.dto.LoginRequest;
import com.studyspace.dto.RegisterRequest;
import com.studyspace.dto.UpdateProfileRequest;
import com.studyspace.entity.User;
import com.studyspace.mapper.UserMapper;
import com.studyspace.security.JwtUtil;
import com.studyspace.security.LoginSessionStore;
import com.studyspace.service.UserService;
import com.studyspace.vo.LoginVO;
import com.studyspace.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * 用户业务实现
 */
@Slf4j
@Service
// 自动注入依赖，无需 @Autowired或者构造器注入 以及所有未初始化且标注了@NonNull的字段
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final LoginSessionStore loginSessionStore;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVO register(RegisterRequest request) {
        // 1. 学号是否已注册
        Long sameNo = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getStudentNo, request.getStudentNo()));
        if (sameNo != null && sameNo > 0) {
            throw new BusinessException(ResultCode.STUDENT_NO_EXISTS);
        }

        // 2. 邮箱是否被占用（邮箱可以不填）
        if (StringUtils.hasText(request.getEmail())) {
            Long sameEmail = userMapper.selectCount(new LambdaQueryWrapper<User>()
                    .eq(User::getEmail, request.getEmail()));
            if (sameEmail != null && sameEmail > 0) {
                throw new BusinessException(ResultCode.EMAIL_EXISTS);
            }
        }

        // 3. 密码加密后再入库，数据库里永远看不到明文
        User user = new User();
        user.setStudentNo(request.getStudentNo().trim());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname().trim());
        user.setEmail(StringUtils.hasText(request.getEmail()) ? request.getEmail().trim() : null);
        user.setPhone(StringUtils.hasText(request.getPhone()) ? request.getPhone().trim() : null);
        user.setRole(UserConstants.ROLE_STUDENT);
        user.setCreditScore(UserConstants.DEFAULT_CREDIT_SCORE);
        user.setStatus(UserConstants.STATUS_NORMAL);
        userMapper.insert(user);

        log.info("学生注册成功：studentNo={}, id={}", user.getStudentNo(), user.getId());
        return UserVO.from(user);
    }

    @Override
    public LoginVO login(LoginRequest request) {
        String studentNo = request.getStudentNo().trim();

        // 0. 先看账号是否在锁定期：锁定期内连密码都不校验，直接拒绝
        //    否则攻击者可以一边爆破解锁一边继续试密码
        if (loginSessionStore.isLocked(studentNo)) {
            long remainMinutes = Math.max(1, loginSessionStore.lockRemainSeconds(studentNo) / 60);
            throw new BusinessException(ResultCode.LOGIN_LOCKED,
                    "密码错误次数过多，账号已锁定，请 " + remainMinutes + " 分钟后再试");
        }

        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getStudentNo, studentNo));

        // 账号不存在和密码错误返回同一句提示，避免别人拿它来试探哪个学号已注册
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            int failTimes = loginSessionStore.increaseFail(studentNo);
            int maxTimes = loginSessionStore.getMaxFailTimes();
            if (failTimes >= maxTimes) {
                // 达到阈值：上锁，并把计数器清零，避免解锁后又立即被累计的旧次数触发
                loginSessionStore.lock(studentNo);
                loginSessionStore.clearFail(studentNo);
                throw new BusinessException(ResultCode.LOGIN_LOCKED,
                        "密码错误 " + failTimes + " 次，账号已锁定 " + loginSessionStore.getLockMinutes() + " 分钟");
            }
            throw new BusinessException(ResultCode.ACCOUNT_NOT_FOUND,
                    "学号或密码错误，还可尝试 " + (maxTimes - failTimes) + " 次");
        }
        if (user.getStatus() != null && user.getStatus() != UserConstants.STATUS_NORMAL) {
            throw new BusinessException(ResultCode.ACCOUNT_DISABLED);
        }

        // 登录成功：清零失败计数，签发令牌，并写入 Redis（白名单 + 会话快照）
        loginSessionStore.clearFail(studentNo);
        String token = jwtUtil.generateToken(user.getId(), user.getStudentNo(), user.getRole());
        loginSessionStore.save(token, user, Duration.ofSeconds(jwtUtil.getExpiresInSeconds()));
        log.info("登录成功：studentNo={}, userId={}", user.getStudentNo(), user.getId());
        return new LoginVO(token, "Bearer", jwtUtil.getExpiresInSeconds(), UserVO.from(user));
    }

    @Override
    public void logout(String token) {
        Long userId = loginSessionStore.userIdOf(token);
        loginSessionStore.destroy(token);
        log.info("退出登录，令牌已失效：userId={}", userId);
    }

    @Override
    public UserVO getProfile(Long userId) {
        return UserVO.from(requireUser(userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVO updateProfile(Long userId, UpdateProfileRequest request) {
        User user = requireUser(userId);

        // 只更新传了值的字段，没传的保持原样（前端可以只提交改动的部分）
        if (request.getNickname() != null) {
            user.setNickname(request.getNickname().trim());
        }
        if (request.getEmail() != null) {
            String email = request.getEmail().trim();
            if (StringUtils.hasText(email)) {
                Long sameEmail = userMapper.selectCount(new LambdaQueryWrapper<User>()
                        .eq(User::getEmail, email)
                        .ne(User::getId, userId));
                if (sameEmail != null && sameEmail > 0) {
                    throw new BusinessException(ResultCode.EMAIL_EXISTS);
                }
            }
            user.setEmail(StringUtils.hasText(email) ? email : null);
        }
        if (request.getPhone() != null) {
            String phone = request.getPhone().trim();
            user.setPhone(StringUtils.hasText(phone) ? phone : null);
        }

        userMapper.updateById(user);
        return UserVO.from(requireUser(userId));
    }

    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        return user;
    }
}
