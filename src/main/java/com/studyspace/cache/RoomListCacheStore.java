package com.studyspace.cache;

import com.studyspace.dto.PageResult;
import com.studyspace.vo.RoomVO;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 自习室列表缓存（Redis，Cache-Aside 模式）
 *
 * <p>列表页是整站访问最频繁的读接口，但自习室数据一天改不了几次，
 * 每次都 count + limit 打到 MySQL 是白费力气，所以整页结果按查询条件缓存成 JSON：</p>
 *
 * <pre>
 * room:list:page:{page}:size:{size}:status:{status}:kw:{keyword}  ->  PageResult&lt;RoomVO&gt; 的 JSON，TTL 10 分钟
 * </pre>
 *
 * <p>key 里必须带上分页和筛选条件，否则第 2 页会读到第 1 页的数据；
 * 用 String 存整段 JSON 而不是 Hash，是因为这个值整体读取、整体失效，不需要按字段操作，
 * redis-cli 里 get 一下也能直接看到接口返回的结构。</p>
 *
 * <p>写操作删缓存采用「延迟双删」：事务提交后立刻删一次，500 毫秒后再删一次。
 * 第一次删是常规操作；第二次删是为了兜住并发读——恰好有请求在删之前读了数据库旧值、
 * 在删之后才把旧值写回缓存，不补一刀这份脏数据要等到 TTL 过期才会消失。</p>
 *
 * <p>所有 Redis 异常都在这里被吃掉并降级为"未命中"：
 * Redis 挂掉时列表接口退化成直查数据库，只是慢一点，不会不可用。</p>
 */
@Slf4j
@Component
public class RoomListCacheStore {

    /** 键前缀，与 SRS 第 9 章 room:list:* 保持一致，便于 redis-cli 按前缀排查 */
    private static final String KEY_PREFIX = "room:list:";

    /** 反序列化要用完整泛型，否则 PageResult<RoomVO> 会被擦成 PageResult<LinkedHashMap> */
    private static final TypeReference<PageResult<RoomVO>> VALUE_TYPE = new TypeReference<>() {
    };

    /** 延迟双删的第二次删除延迟（毫秒） */
    private static final long SECOND_EVICT_DELAY_MILLIS = 500L;

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    /** 列表缓存有效期 */
    private final Duration ttl;

    /** 延迟删除专用线程：单线程 + 守护线程，不参与业务线程池，不阻止 JVM 退出 */
    private final ScheduledExecutorService delayedEvictExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "room-list-cache-evict");
        thread.setDaemon(true);
        return thread;
    });

    public RoomListCacheStore(StringRedisTemplate redis,
                              ObjectMapper objectMapper,
                              @Value("${cache.room-list-ttl-minutes:10}") long ttlMinutes) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    /**
     * 拼缓存 key
     * <p>空条件统一写成 "-"，避免 null 拼进 key 变成 "null" 这种看不出含义的字符串；
     * 关键字做 trim，因为 service 层也是按 trim 后的值查库的，两边必须一致才能命中</p>
     */
    public String key(int page, int size, String status, String keyword) {
        return KEY_PREFIX + "page:" + page
                + ":size:" + size
                + ":status:" + (status == null || status.isBlank() ? "-" : status.trim())
                + ":kw:" + (keyword == null || keyword.isBlank() ? "-" : keyword.trim());
    }

    /** 读缓存，未命中或读失败都返回 null，由调用方回源数据库 */
    public PageResult<RoomVO> get(String key) {
        try {
            String json = redis.opsForValue().get(key);
            return json == null ? null : objectMapper.readValue(json, VALUE_TYPE);
        } catch (RuntimeException e) {
            // 包括 Redis 连不上、存量 JSON 格式对不上（改过字段）两种情况：丢掉即可，下次会重新回填
            log.warn("读取自习室列表缓存失败，降级查数据库：key={}, 原因={}", key, e.getMessage());
            return null;
        }
    }

    /** 回填缓存；写失败只记日志，不影响本次请求返回结果 */
    public void put(String key, PageResult<RoomVO> value) {
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (RuntimeException e) {
            log.warn("写入自习室列表缓存失败：key={}, 原因={}", key, e.getMessage());
        }
    }

    /**
     * 清空所有自习室列表缓存（延迟双删）
     * <p>一个自习室变动会同时影响"全部页""按状态筛""按关键字筛"等一批 key，
     * 与其维护一张 key 与条件之间的映射表，不如整体清掉——列表接口 TTL 只有 10 分钟，重建成本很低</p>
     */
    public void evictAll() {
        evictNow();
        try {
            delayedEvictExecutor.schedule(this::evictNow, SECOND_EVICT_DELAY_MILLIS, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            log.warn("延迟删除自习室列表缓存的任务被拒绝，应用可能正在关闭");
        }
    }

    /** 用 SCAN 而不是 KEYS 遍历：KEYS 会阻塞整个 Redis，SCAN 是游标分批取，线上更安全 */
    private void evictNow() {
        try {
            List<String> keys = new ArrayList<>();
            ScanOptions options = ScanOptions.scanOptions().match(KEY_PREFIX + "*").count(200).build();
            try (Cursor<String> cursor = redis.scan(options)) {
                cursor.forEachRemaining(keys::add);
            }
            if (!keys.isEmpty()) {
                redis.delete(keys);
                log.info("清除自习室列表缓存：{} 个 key", keys.size());
            }
        } catch (RuntimeException e) {
            log.warn("清除自习室列表缓存失败（不影响业务，等 TTL 自然过期）：{}", e.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        delayedEvictExecutor.shutdownNow();
    }
}
