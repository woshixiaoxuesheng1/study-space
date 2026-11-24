package com.studyspace.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.studyspace.common.BusinessException;
import com.studyspace.common.ResultCode;
import com.studyspace.constants.CommonConstants;
import com.studyspace.constants.UserConstants;
import com.studyspace.dto.CreditVO;
import com.studyspace.dto.LoginRequest;
import com.studyspace.dto.LoginResponse;
import com.studyspace.dto.RegisterRequest;
import com.studyspace.dto.UpdateProfileRequest;
import com.studyspace.dto.UserVO;
import com.studyspace.entity.User;
import com.studyspace.mapper.UserMapper;
import com.studyspace.security.JwtUtil;
import com.studyspace.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 用户业务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

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
    public LoginResponse login(LoginRequest request) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getStudentNo, request.getStudentNo()));

        // 账号不存在和密码错误返回同一句提示，避免别人拿它来试探哪个学号已注册
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.ACCOUNT_NOT_FOUND);
        }
        if (user.getStatus() != null && user.getStatus() != UserConstants.STATUS_NORMAL) {
            throw new BusinessException(ResultCode.ACCOUNT_DISABLED);
        }

        String token = jwtUtil.generateToken(user.getId(), user.getStudentNo(), user.getRole());
        log.info("登录成功：studentNo={}", user.getStudentNo());
        return new LoginResponse(token, "Bearer", jwtUtil.getExpiresInSeconds(), UserVO.from(user));
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

    @Override
    public CreditVO getCredit(Long userId) {
        User user = requireUser(userId);
        int score = user.getCreditScore() == null ? UserConstants.DEFAULT_CREDIT_SCORE : user.getCreditScore();
        LocalDateTime banUntil = user.getBanUntil();
        boolean bannedByTime = banUntil != null && banUntil.isAfter(LocalDateTime.now());
        boolean canBook = score >= UserConstants.CREDIT_LIMIT && !bannedByTime;

        CreditVO vo = new CreditVO();
        vo.setCreditScore(score);
        vo.setLimitScore(UserConstants.CREDIT_LIMIT);
        vo.setCanBook(canBook);
        vo.setBanUntil(banUntil == null ? null : CommonConstants.DATE_TIME_FORMATTER.format(banUntil));
        if (canBook) {
            vo.setMessage("信用分正常，可以预约座位");
        } else if (bannedByTime) {
            long days = Math.max(1, Duration.between(LocalDateTime.now(), banUntil).toDays() + 1);
            vo.setMessage("信用分 " + score + " 分低于 " + UserConstants.CREDIT_LIMIT + " 分，约 " + days + " 天后自动恢复预约权限");
        } else {
            vo.setMessage("信用分 " + score + " 分低于 " + UserConstants.CREDIT_LIMIT + " 分，暂时无法预约");
        }
        return vo;
    }

    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        return user;
    }
}
