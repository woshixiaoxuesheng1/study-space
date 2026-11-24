# Study Space · 校园自习室智能预约与座位调度系统

![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.2.0-6DB33F)
![Spring Security](https://img.shields.io/badge/Spring%20Security-7-6DB33F)
![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1)
![Redis](https://img.shields.io/badge/Redis-8.x-DC382D)
![MyBatis-Plus](https://img.shields.io/badge/MyBatis--Plus-3.5.18-blue)
![License](https://img.shields.io/badge/license-MIT-lightgrey)

> 后端服务：学生在手机上查空位、提前订座、到点扫码签到；管理员维护自习室与座位、处理违约。
> 系统只回答两个问题——**这个座位现在有没有人订**，以及**订了的人到底来没来**。

---

## 目录

- [项目简介](#项目简介)
- [功能特性](#功能特性)
- [技术栈](#技术栈)
- [系统架构](#系统架构)
- [核心设计](#核心设计)
- [接口清单](#接口清单)
- [快速开始](#快速开始)
- [配置说明](#配置说明)
- [演示账号](#演示账号)
- [开发进度](#开发进度)

---

## 项目简介

图书馆和自习室最典型的三个痛点：

| 痛点 | 系统的解法 |
| --- | --- |
| 必须走到门口才知道有没有空位，经常白跑一趟 | 按日期 + 时间段实时查询空闲座位，座位图直观展示布局 |
| 有人占了座半天不来，别人干等 | 预约后必须在签到窗口内签到，超时由定时任务自动判违约、释放座位、扣信用分 |
| 多个人同时抢同一个座位，谁先谁后说不清 | 先锁座位行再判冲突，一个事务内完成"查询 → 校验 → 插入"，后到者收到"该座位已被预约" |

设计上刻意回避了"时间片表"这类重型方案：只用 `start_time` + `end_time` 两个字段配合一条重叠判断 SQL，数据量小、逻辑直观、出错好排查。校园场景一天几百次预约，这套方案完全够用。

---

## 功能特性

### 账号与安全

- 学号注册（BCrypt 加密存储）、学号 + 密码登录、退出登录
- JWT 无状态令牌 + Redis 白名单：注销、被顶下线、改密码后令牌立即失效
- 单会话控制：同一账号新登录会把旧登录顶下线
- 登录失败计数：连续失败 5 次锁定账号 15 分钟，防暴力破解
- 用户身份**只从令牌解析**，任何"我的数据"都不接受前端传 `userId`
- 角色权限：`STUDENT` 可预约 / 签到 / 签退，`ADMIN` 额外可维护自习室与座位、代学生取消

### 自习室与座位

- 自习室增删改查（同楼栋重名校验、开放时间校验、有进行中预约时禁止删除）
- 按"几行几列"批量生成座位，自动编号并生成行列坐标
- 座位布局查询：一次返回整间自习室的行列结构与每个座位的实时状态
- 空闲座位查询：按日期 + 时间段筛选，已预约 / 使用中 / 停用的座位不返回

### 预约

- 创建预约前依次校验：时间合法性 → 不能订过去的时间 → 在开放时间内 → 时长下限 → 座位是否停用 → 时段冲突 → 本人同时段重复 → 每日次数上限 → 信用分是否达标
- 冲突判定全程以 `booking` 表为准，座位表的状态只用于页面展示
- 分页查询我的预约，支持按状态筛选
- 取消预约：开始前 30 分钟以外免费取消；以内取消扣 10 分；管理员代取消不扣分
- 预约单编号形如 `BK20261125093015`，同时生成 4 位随机签到码

### 签到与签退

- 扫码签到：校验本人 → 状态 → 座位/自习室是否可用 → 时间窗口 → 签到码，五关逐条给出人话提示
- 签到窗口：开始前 15 分钟开放，开始后 15 分钟截止，以服务端时间为准
- 签到凭证接口返回签到码、窗口起止和倒计时，前端可直接渲染
- 签退后座位立刻释放，剩余时段可被别人预订
- 定时任务每分钟两件事：把超时未签到的预约判为违约并释放座位；把已到结束时间的"使用中"预约自动置为已完成

### 信用分与违约

- 初始 100 分，违约一次扣 10 分
- 低于 60 分禁止预约，`ban_until` 记录解禁时间，到点自然失效（无需再跑解禁任务）
- 违约记录落库，含类型、扣分、原因、发生时间，支持管理员撤销

### 工程规范

- 统一返回 `{code, message, data}`：成功 `code=0`，业务失败 HTTP 200 + 非 0 码，认证失败 401，越权 403
- 全局异常处理，业务异常与参数校验异常分别给出可读提示
- 逻辑删除 + 创建/更新时间自动填充
- 业务阈值（每日次数、最短时长、签到窗口、扣分规则）全部外置到 `application.yaml`，调参不改代码

---

## 技术栈

| 层次 | 选型 | 版本 | 说明 |
| --- | --- | --- | --- |
| 语言 | Java | 源码级别 17（运行 JDK 25） | — |
| 框架 | Spring Boot | 4.2.0 | Boot 4 的 Web 模块为 `spring-boot-starter-webmvc` |
| 安全 | Spring Security + JWT | 7 / 0.12.7 | 基于 JJWT 实现无状态鉴权，认证失败由 `SecurityConfig` 直接输出 JSON |
| 持久层 | MyBatis-Plus | 3.5.18.1 | `mybatis-plus-spring-boot4-starter` |
| 数据库 | MySQL | 8.0 | 6 张表，`booking` 表核心 |
| 缓存 | Redis | 8.x | 登录白名单、会话快照、登录失败计数 |
| 校验 | Spring Validation | — | `@Valid` + 全局异常处理 |
| 构建 | Maven | 3.9.x | `mvn spring-boot:run` |

---

## 系统架构

```
客户端（Web / 小程序）
        │  HTTP + Bearer Token
        ▼
┌─────────────────────────────────────────────────┐
│  Security 层                                     │
│  JwtAuthenticationFilter → Redis 白名单校验      │
│  → SecurityContext（LoginUser: id / 学号 / 角色）│
└─────────────────────────────────────────────────┘
        ▼
┌─────────────────────────────────────────────────┐
│  Controller 层    参数校验、取当前用户、统一包装  │
└─────────────────────────────────────────────────┘
        ▼
┌─────────────────────────────────────────────────┐
│  Service 层       业务规则、事务边界、状态流转    │
│  Auth / Room / Booking / CheckIn / Credit        │
└─────────────────────────────────────────────────┘
        ▼
┌─────────────────────────────────────────────────┐
│  Mapper 层（MyBatis-Plus + 手写 SQL）            │
│  行锁 / 重叠查询 / 条件更新                       │
└─────────────────────────────────────────────────┘
        ▼
   MySQL（6 张表）        Redis（登录态）     定时任务（每分钟扫描）
```

### 目录结构

```
src/main/java/com/studyspace
├── common/        统一返回 Result、错误码 ResultCode、业务异常、全局异常处理
├── config/        SecurityConfig、MyBatis-Plus 自动填充
├── constants/     业务常量（预约编号前缀、违约类型、角色、状态字面量）
├── controller/    Auth / User / Room / Booking / CheckIn
├── dto/           入参对象与分页包装 PageResult
├── entity/        User / Room / Seat / Booking / Violation
├── enums/         BookingStatus（含合法流转规则）
├── mapper/        MyBatis-Plus Mapper + 手写 SQL
├── security/      JWT 工具、认证过滤器、Redis 会话仓库、当前用户工具
├── service/       业务接口与实现
├── task/          超时释放 / 到点完成的定时任务
└── vo/            出参对象（不含密码等敏感字段）
```

---

## 核心设计

### 1. 时段重叠判定

两段时间有交集，当且仅当：

```
新的开始时间 < 已有的结束时间  且  新的结束时间 > 已有的开始时间
```

两端都是**严格小于 / 大于**。所以 `10:00–12:00` 与 `12:00–14:00` 是**不冲突**的——前后两位同学正好接上，写成 `<=` / `>=` 就会误判，这是该功能最容易出错的地方。

配合 `idx_seat_time (seat_id, start_time, end_time)` 索引，查询只扫目标座位的时间区间。

### 2. 并发抢座：先锁行，再判冲突

查与插之间存在时间窗口，两个人可能都查到"没人订"然后都插入。解法是把座位行锁住：

```sql
BEGIN;
SELECT * FROM `seat` WHERE id = ? AND deleted = 0 FOR UPDATE;   -- 锁住座位行
SELECT COUNT(*) FROM `booking`                                   -- 再判有没有重叠
 WHERE seat_id = ? AND status IN ('PENDING_CHECKIN','CHECKED_IN')
   AND start_time < ?  -- 新结束时间
   AND end_time   > ?; -- 新开始时间
INSERT INTO `booking` (...) VALUES (...);                        -- 为 0 才插入
COMMIT;                                                          -- 提交后锁释放
```

第二个请求会阻塞在 `FOR UPDATE`，等第一个人提交后再查，查到的是"已被预约"，直接返回冲突。不需要分布式锁，也不需要额外引入缓存做互斥。

### 3. 状态机：所有跳转只走一个出口

| 当前状态 | 含义 | 可流转到 |
| --- | --- | --- |
| `PENDING_CHECKIN` | 待签到 | `CHECKED_IN` / `CANCELLED` / `NO_SHOW` |
| `CHECKED_IN` | 使用中 | `COMPLETED` |
| `COMPLETED` | 已完成 | 终态 |
| `CANCELLED` | 已取消 | 终态 |
| `NO_SHOW` | 违约 | 终态 |

状态变更统一走 `BookingStatus.canTransitTo` 校验，且 UPDATE 语句一律带上 `AND status = 原状态`。并发场景下（比如学生点签到的同一秒定时任务判了违约），条件更新影响行数为 0，后到的操作直接放弃，不会把"使用中"改成"违约"这种脏数据。

### 4. 座位表状态 vs 预约表

座位表里的 `status` 只服务于座位图渲染；**能否预约永远以 `booking` 表的重叠查询为准**。两者不一致时以预约表为准——这条边界定清楚，能避免大量"座位显示空闲但订不上"的困惑。

### 5. 为什么不建时间片表

| 方案 | 数据量 | 逻辑 | 高并发性能 |
| --- | --- | --- | --- |
| 时间片（座位 × 半小时格子） | 5000 座 × 28 格 × 7 天 ≈ 98 万行 | 需定时预生成，漏生成就出错 | 好 |
| 起止时间 + 重叠 SQL（本项目） | 与真实预约数同量级 | 一条 SQL，直观可排查 | 够用 |

校园场景每天几百次预约，后者在正确性和可维护性上的收益远大于性能损失。

---

## 接口清单

统一前缀 `/api`，除注册和登录外均需 `Authorization: Bearer <token>`。

### 账号

| 方法 | 路径 | 说明 | 权限 |
| --- | --- | --- | --- |
| POST | `/api/register` | 学号注册 | 公开 |
| POST | `/api/login` | 登录，返回 token | 公开 |
| POST | `/api/logout` | 退出，清除登录态使令牌立即失效 | 登录 |
| GET | `/api/users/me` | 查看本人资料 | 登录 |
| PUT | `/api/users/me` | 修改昵称 / 邮箱 / 手机号 | 登录 |
| GET | `/api/users/me/credit` | 查看信用分、禁约状态与可预约提示 | 登录 |

### 自习室与座位

| 方法 | 路径 | 说明 | 权限 |
| --- | --- | --- | --- |
| GET | `/api/rooms?page=&size=&status=&keyword=` | 自习室列表（分页） | 登录 |
| GET | `/api/rooms/{id}` | 自习室详情 | 登录 |
| POST | `/api/rooms` | 新增自习室 | 管理员 |
| PUT | `/api/rooms/{id}` | 修改自习室 | 管理员 |
| DELETE | `/api/rooms/{id}` | 删除自习室 | 管理员 |
| GET | `/api/rooms/{id}/seats` | 座位布局与实时状态 | 登录 |
| POST | `/api/rooms/{id}/seats/batch` | 按行列批量生成座位 | 管理员 |
| GET | `/api/rooms/{id}/free-seats?date=&startTime=&endTime=` | 查询指定时段空闲座位 | 登录 |

### 预约

| 方法 | 路径 | 说明 | 权限 |
| --- | --- | --- | --- |
| POST | `/api/bookings` | 创建预约 | 登录 |
| GET | `/api/bookings?page=&size=&status=` | 我的预约（分页） | 登录 |
| GET | `/api/bookings/{id}` | 预约详情 | 本人 / 管理员 |
| DELETE | `/api/bookings/{id}?reason=` | 取消预约 | 本人 / 管理员 |

### 签到签退

| 方法 | 路径 | 说明 | 权限 |
| --- | --- | --- | --- |
| GET | `/api/bookings/{id}/check-in-code` | 获取签到码与窗口倒计时 | 本人 / 管理员 |
| POST | `/api/bookings/{id}/check-in` | 签到（需提交签到码） | 本人 |
| POST | `/api/bookings/{id}/check-out` | 签退 | 本人 |

### 响应示例

```json
{ "code": 0, "message": "预约成功", "data": { "id": 12, "bookingNo": "BK20261125093015" } }
{ "code": 3004, "message": "该座位在所选时段已被预约", "data": null }
```

---

## 快速开始

### 环境要求

- JDK 17 及以上（本项目在 JDK 25 上运行）
- Maven 3.9+
- MySQL 8.0+
- Redis 8.x（用于登录态，未启动无法登录）

### 1. 初始化数据库

```bash
mysql -u root -p < sql/init.sql
```

脚本会创建 `study_space` 库、6 张表和演示数据，**可重复执行**（先 DROP 再 CREATE）。

### 2. 修改本地配置

编辑 `src/main/resources/application.yaml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/study_space?serverTimezone=Asia/Shanghai
    username: root
    password: 你的密码
  data:
    redis:
      host: 127.0.0.1
      port: 6379
```

### 3. 启动

```bash
mvn spring-boot:run
# 或
mvn -DskipTests package && java -jar target/study-space-0.0.1-SNAPSHOT.jar
```

服务默认监听 `8080`。

### 4. 验证

```bash
# 登录拿 token
curl -X POST http://localhost:8080/api/login \
  -H "Content-Type: application/json" \
  -d '{"studentNo":"20230101001","password":"123456"}'

# 查空闲座位
curl "http://localhost:8080/api/rooms/1/free-seats?date=2026-11-25&startTime=10:00&endTime=12:00" \
  -H "Authorization: Bearer <token>"

# 创建预约
curl -X POST http://localhost:8080/api/bookings \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{"seatId":1,"startTime":"2026-11-25 10:00:00","endTime":"2026-11-25 12:00:00"}'
```

---

## 配置说明

业务阈值集中在 `application.yaml`，答辩演示改参数无需重新编译：

| 配置项 | 默认值 | 含义 |
| --- | --- | --- |
| `booking.daily-limit` | 3 | 每人每天最多预约次数 |
| `booking.min-duration-minutes` | 60 | 单次预约最短时长（分钟） |
| `booking.cancel-free-minutes` | 30 | 开始前多少分钟以外取消免费 |
| `booking.check-in-early-minutes` | 15 | 签到窗口：开始前多少分钟开放 |
| `booking.check-in-late-minutes` | 15 | 签到窗口：开始后多少分钟截止 |
| `booking.credit-limit` | 60 | 低于该信用分禁止预约 |
| `booking.ban-days` | 7 | 低信用分解禁所需天数 |
| `booking.no-show-deduct` | 10 | 超时未签到扣分 |
| `booking.late-cancel-deduct` | 10 | 临近开始取消扣分 |
| `login.max-fail-times` | 5 | 连续登录失败几次锁定 |
| `login.lock-minutes` | 15 | 锁定时长 |
| `login.single-session` | true | 是否只允许一个有效会话 |
| `jwt.expire-minutes` | 120 | 令牌有效期 |

> 生产环境请把 `jwt.secret` 与数据库密码移到环境变量。

---

## 演示账号

| 学号 | 密码 | 角色 | 说明 |
| --- | --- | --- | --- |
| `A0001` | `123456` | ADMIN | 系统管理员 |
| `20230101001` | `123456` | STUDENT | 张三，信用分 100 |
| `20230101002` | `123456` | STUDENT | 李四，信用分 90 |
| `20230101003` | `123456` | STUDENT | 王五，信用分 50（禁约中，可演示信用分拦截） |

---

## 开发进度

| 模块 | 状态 |
| --- | --- |
| 注册 / 登录 / 退出、JWT + Redis 会话 | ✅ |
| 个人资料、信用分查询 | ✅ |
| 自习室 CRUD、批量生成座位、座位布局 | ✅ |
| 空闲座位查询 | ✅ |
| 创建预约（冲突 / 重复 / 次数 / 信用分校验） | ✅ |
| 取消预约与扣分规则 | ✅ |
| 签到（窗口 + 签到码）、签退 | ✅ |
| 超时未签到自动违约、到点自动完成 | ✅ |
| 管理端违约记录查询与撤销 | 🚧 |
| 自习室列表 Redis 缓存 | 🚧 |
| RabbitMQ 延迟队列替代定时扫描 | 🚧 |
| 座位推荐（加权评分） | 🚧 |
| 使用数据统计图表 | 🚧 |
| 站内通知（表结构已就绪） | 🚧 |
| Vue 3 前端页面 | 🚧 |

---

## 说明

本项目为个人毕业设计，用于完整实践「需求分析 → 表设计 → 接口实现 → 并发与状态治理」的后端开发全流程。代码按模块分包，每个 Service 实现类头部都写明了该模块的业务铁律与边界条件，便于后续维护与答辩讲解。

License: MIT
