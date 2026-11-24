package com.studyspace.security;

import com.studyspace.constants.CommonConstants;
import com.studyspace.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 登录状态存储（Redis）
 *
 * <p>JWT 本身是无状态的：签发之后服务端说了不算，只能等它自己过期。
 * 想让"退出登录立刻生效""顶号下线""封号立即失效"做到，就得有一份服务端记录，
 * 这里用 Redis 存三类键：</p>
 *
 * <pre>
 * login:token:{token}   -> userId            令牌白名单，TTL = 令牌有效期
 * login:user:{userId}   -> Hash(会话快照)     登录状态，TTL 同上
 * login:fail:{学号}      -> 失败次数           计数器，TTL = 统计窗口
 * login:lock:{学号}      -> 解锁时间           锁定标记，TTL = 锁定时长
 * </pre>
 *
 * <p>为什么用 Hash 而不是把对象序列化成 JSON：字段少、查询直观，
 * 而且不用关心序列化器和 Jackson 版本，redis-cli 里也能直接看懂。</p>
 */
@Slf4j
@Component
public class LoginSessionStore {

    private static final String TOKEN_KEY_PREFIX = "login:token:";
    private static final String USER_KEY_PREFIX = "login:user:";
    private static final String FAIL_KEY_PREFIX = "login:fail:";
    private static final String LOCK_KEY_PREFIX = "login:lock:";

    private static final String FIELD_TOKEN = "token";
    private static final String FIELD_STUDENT_NO = "studentNo";
    private static final String FIELD_NICKNAME = "nickname";
    private static final String FIELD_ROLE = "role";
    private static final String FIELD_LOGIN_TIME = "loginTime";

    private final StringRedisTemplate redis;
    /** 是否同一账号只允许一个有效会话（新登录顶掉旧登录） */
    private final boolean singleSession;
    /** 连续失败多少次锁定 */
    private final int maxFailTimes;
    /** 锁定时长（分钟） */
    private final int lockMinutes;
    /** 失败次数统计窗口（分钟），超过窗口没再失败就自动清零 */
    private final int failWindowMinutes;

    public LoginSessionStore(StringRedisTemplate redis,
                             @Value("${login.single-session:true}") boolean singleSession,
                             @Value("${login.max-fail-times:5}") int maxFailTimes,
                             @Value("${login.lock-minutes:15}") int lockMinutes,
                             @Value("${login.fail-window-minutes:15}") int failWindowMinutes) {
        this.redis = redis;
        this.singleSession = singleSession;
        this.maxFailTimes = maxFailTimes;
        this.lockMinutes = lockMinutes;
        this.failWindowMinutes = failWindowMinutes;
    }

    // ==================== 登录状态 / 令牌白名单 ====================

    /**
     * 登录成功后写入会话：令牌进白名单，用户 id 存一份会话快照
     */
    public void save(String token, User user, Duration ttl) {
        String tokenKey = TOKEN_KEY_PREFIX + token;
        String userKey = USER_KEY_PREFIX + user.getId();

        // 单端登录：先把这个账号的旧令牌踢下线，旧令牌下次请求就查不到白名单了
        if (singleSession) {
            Object oldToken = redis.opsForHash().get(userKey, FIELD_TOKEN);
            if (oldToken != null && !token.equals(oldToken.toString())) {
                redis.delete(TOKEN_KEY_PREFIX + oldToken);
                log.info("同一账号再次登录，旧会话已被顶下线：userId={}", user.getId());
            }
        }

        redis.opsForValue().set(tokenKey, String.valueOf(user.getId()), ttl);

        Map<String, String> session = new HashMap<>(8);
        session.put(FIELD_TOKEN, token);
        session.put(FIELD_STUDENT_NO, user.getStudentNo());
        session.put(FIELD_NICKNAME, user.getNickname());
        session.put(FIELD_ROLE, user.getRole());
        session.put(FIELD_LOGIN_TIME, CommonConstants.DATE_TIME_FORMATTER.format(LocalDateTime.now()));
        redis.opsForHash().putAll(userKey, session);
        redis.expire(userKey, ttl);
    }

    /** 令牌是否还在白名单里（退出、被顶下线、过期都会返回 false） */
    public boolean isTokenActive(String token) {
        return Boolean.TRUE.equals(redis.hasKey(TOKEN_KEY_PREFIX + token));
    }

    /** 令牌对应的用户 id，令牌不在白名单时为 null */
    public Long userIdOf(String token) {
        String value = redis.opsForValue().get(TOKEN_KEY_PREFIX + token);
        return value == null ? null : Long.valueOf(value);
    }

    /** 用户是否在线（会话快照还在） */
    public boolean isOnline(Long userId) {
        return Boolean.TRUE.equals(redis.hasKey(USER_KEY_PREFIX + userId));
    }

    /** 读取会话快照，没有则返回 null */
    public Map<Object, Object> findSession(Long userId) {
        Map<Object, Object> session = redis.opsForHash().entries(USER_KEY_PREFIX + userId);
        return session.isEmpty() ? null : session;
    }

    /**
     * 退出登录：删掉白名单和会话快照
     * <p>只有当前会话正是这个令牌时才清用户快照，否则会把用户刚建立的新会话删掉</p>
     */
    public void destroy(String token) {
        Long userId = userIdOf(token);
        redis.delete(TOKEN_KEY_PREFIX + token);
        if (userId == null) {
            return;
        }
        String userKey = USER_KEY_PREFIX + userId;
        Object current = redis.opsForHash().get(userKey, FIELD_TOKEN);
        if (token.equals(current)) {
            redis.delete(userKey);
        }
    }

    // ==================== 登录失败次数 / 锁定 ====================

    /** 失败次数 +1，返回自增后的次数；第一次失败时给计数器设置过期时间 */
    public int increaseFail(String studentNo) {
        String key = FAIL_KEY_PREFIX + studentNo;
        Long times = redis.opsForValue().increment(key);
        if (times != null && times == 1L) {
            redis.expire(key, Duration.ofMinutes(failWindowMinutes));
        }
        return times == null ? 1 : times.intValue();
    }

    /** 登录成功后清零失败计数 */
    public void clearFail(String studentNo) {
        redis.delete(FAIL_KEY_PREFIX + studentNo);
    }

    public int getFailTimes(String studentNo) {
        String value = redis.opsForValue().get(FAIL_KEY_PREFIX + studentNo);
        return value == null ? 0 : Integer.parseInt(value);
    }

    /** 账号是否处于锁定期 */
    public boolean isLocked(String studentNo) {
        return Boolean.TRUE.equals(redis.hasKey(LOCK_KEY_PREFIX + studentNo));
    }

    /** 锁定账号，并在 key 里记下解锁时间，方便排查 */
    public void lock(String studentNo) {
        Duration ttl = Duration.ofMinutes(lockMinutes);
        redis.opsForValue().set(LOCK_KEY_PREFIX + studentNo,
                CommonConstants.DATE_TIME_FORMATTER.format(LocalDateTime.now().plus(ttl)), ttl);
        log.warn("账号因连续登录失败被锁定：studentNo={}, 锁定时长={}分钟", studentNo, lockMinutes);
    }

    /** 锁定还剩多少秒，没锁返回 0 */
    public long lockRemainSeconds(String studentNo) {
        Long seconds = redis.getExpire(LOCK_KEY_PREFIX + studentNo);
        return seconds == null || seconds < 0 ? 0 : seconds;
    }

    public int getMaxFailTimes() {
        return maxFailTimes;
    }

    public int getLockMinutes() {
        return lockMinutes;
    }
}
