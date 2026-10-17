# 驾校学员练车预约与调度管理系统

> 基于微服务架构的智能练车预约平台

## 项目简介

本系统围绕驾校内部学员练车管理，解决传统电话预约和微信群接龙带来的预约混乱、教练负载不均等问题。

**核心问题解决：**

| 传统问题 | 系统方案 |
|---------|---------|
| 电话预约/微信群混乱 | 在线按日期和时段预约 |
| 教练工作量不均 | 排班容量控制 + 负载统计 |
| 主教练满员后无方案 | 调剂推荐 + 临时调剂预约 |
| 预约超额 | Redisson 分布式锁 + 数据库条件 UPDATE 双保险 |
| 练车无记录 | 训练记录生成 + 训练档案 |
| 预约超时未到场 | RabbitMQ 延迟队列自动标记缺席 |

## 项目定位

本项目是**驾校内部练车预约与调度管理系统**，不做考试、不做成绩、不做交管。

| 系统做 | 系统不做 |
|--------|---------|
| 学员/教练/车辆管理 | 考试预约 |
| 教练排班 + 批量生成 | 考试成绩 |
| 学员在线预约练车 | 交管系统对接 |
| Redis 锁防超额 | 缴费 |
| 主教练满员调剂 | 合同管理 |
| 教练完成/缺席 | 驾考全流程 |
| 训练记录沉淀 | |
| 统计驾驶舱 | |

## 系统架构

```
浏览器
  ↓
Gateway（8000）—— 统一入口、JWT 鉴权、路由转发
  ↓
┌────────────────────────────────────────────────────────────┐
│  auth-service        认证服务（登录、签发 JWT）              │
│  user-service        用户服务（账号管理）                    │
│  coach-service       教练服务（教练资料 CRUD）               │
│  student-service     学员服务（学员资料 CRUD）               │
│  vehicle-service     车辆服务（车辆资料 CRUD）               │
│  schedule-service    排班服务（排班、批量生成）              │
│  reservation-service 预约服务（预约、Redis 锁、满员调剂）    │
│  training-service    训练记录服务（练车档案）                │
│  statistics-service  统计服务（驾驶舱、负载分析）            │
│  notification-service通知服务（MQ 消费、异步通知）           │
└────────────────────────────────────────────────────────────┘
  ↓
Nacos（注册中心）· MySQL · Redis · RabbitMQ
```

## 服务模块

| 模块 | 端口 | 说明 |
|------|------|------|
| driving-gateway | 8000 | 网关：路由、JWT 验签、统一入口 |
| driving-auth-service | 18081 | 认证：登录、签发 Token |
| driving-user-service | 18080 | 用户：账号管理 |
| driving-coach-service | 18082 | 教练：资料管理 |
| driving-student-service | 18083 | 学员：资料管理 |
| driving-vehicle-service | 18084 | 车辆：资料管理 |
| driving-schedule-service | 18085 | 排班：排班管理、批量生成 |
| driving-reservation-service | 18087 | 预约：预约、Redisson 锁、调剂 |
| driving-training-service | 18086 | 训练记录：练车档案 |
| driving-statistics-service | 18088 | 统计：驾驶舱、负载分析 |
| driving-notification-service | 18090 | 通知：MQ 消费、异步消息 |

### 公共模块

| 模块 | 说明 |
|------|------|
| driving-common | 纯工具：Result、异常、JwtUtils |
| driving-common-web | Web 相关：SecurityConfig、UserContext、拦截器 |
| driving-api | Feign 契约：各服务的 DTO 和 FeignClient |

## 技术栈

| 层 | 技术 |
|----|------|
| 后端 | Java 21、Spring Boot 3.2、Spring Cloud 2023.0.1、Spring Cloud Alibaba 2023.0.1.0 |
| 安全 | Spring Security、JWT（jjwt） |
| 持久 | MyBatis-Plus、MySQL 8 |
| 缓存/锁 | Redis 7、Redisson |
| 消息队列 | RabbitMQ 3.8 |
| 注册/配置 | Nacos 2.1 |
| 网关/调用 | Spring Cloud Gateway、OpenFeign、LoadBalancer |
| 前端 | Vue 3、TypeScript、Vite、Pinia、Tailwind CSS |

## 核心功能

| 模块 | 说明 |
|------|------|
| 登录认证 | JWT Token + Gateway 统一鉴权 |
| 账号管理 | 为教练/学员开通登录账号 |
| 教练/学员/车辆 | 基础资料 CRUD |
| 排班管理 | 按日期+时段+教练+车辆创建排班，批量生成 |
| 学员预约 | 按日期和时段预约主教练练车 |
| Redis 并发锁 | Redisson 锁保证不会超额 |
| 满员调剂 | 主教练满员→推荐其他教练→临时调剂 |
| 完成练车 | 教练确认→填写内容→生成训练记录 |
| 练车记录 | 学员档案、教练历史、管理员查询 |
| 统计看板 | 驾驶舱 + 教练负载 + 预约趋势 |
| 异步通知 | 预约成功 → MQ → 通知服务 |
| 超时自动标缺席 | RabbitMQ 延迟队列 + 死信队列 |

## 项目亮点

### 1. 网关统一鉴权
- Gateway 层 `JwtGlobalFilter` 统一验签
- 解析出 `userId`、`role`、`relatedId` 塞进请求头
- 下游服务通过 `UserContext` 工具类直接读取，不碰 JWT

### 2. 服务间 Feign 调用
- `driving-api` 定义契约，多服务共享
- `@FeignClient(name = "driving-user-service")` 声明式调用
- Nacos + LoadBalancer 自动服务发现和负载均衡

### 3. Redis 分布式锁 + 数据库条件 UPDATE 双保险
```java
// Redisson 锁
RLock lock = redissonClient.getLock("lock:schedule:" + scheduleId);
lock.tryLock(3, 10, TimeUnit.SECONDS);

// 条件 UPDATE 兜底
UPDATE coach_schedule SET current_students = current_students + 1
WHERE id = ? AND current_students < max_students
```
锁防并发，SQL 防锁失效，双保险不超卖。

### 4. RabbitMQ 异步通知
- 预约成功后发消息，通知服务异步消费
- 主流程秒返回，通知服务挂了不影响预约

### 5. 延迟队列自动标记缺席
- 使用 TTL + 死信队列（DLX）实现延迟消息
- 预约 30 秒后检查状态，未完成自动标 ABSENT
- 消费端幂等：检查预约状态再改，防止重复消费

### 6. 模块化设计
- `common` / `common-web` / `api` 三层共享模块
- Gateway（WebFlux）只依赖 `common`，避开 MVC 冲突
- 业务服务依赖 `common-web`，自动获得权限配置

## 快速启动

### 1. 启动基础设施

```bash
# 启动 MySQL、Redis、Nacos、RabbitMQ
docker-compose up -d
```

| 服务 | 地址 | 端口 |
|------|------|------|
| MySQL | localhost | 3306 |
| Redis | localhost | 6379 |
| Nacos | http://localhost:8848/nacos | 8848 |
| RabbitMQ 控制台 | http://localhost:15672 | 15672 |

### 2. 初始化数据库

```bash
docker exec -i mysql mysql -uroot -p123 < sql/init.sql
```

### 3. 编译公共模块

```bash
cd driving-parent
mvn clean install -pl driving-common,driving-common-web,driving-api -am
```

### 4. 启动微服务

建议用 IDEA 复合启动（Run/Debug Configurations → `+` → Compound）。

**启动顺序：**

1. 基础设施（MySQL、Redis、Nacos、RabbitMQ）
2. 各业务服务（顺序随意，都注册到 Nacos）
3. Gateway（最后启动，因为要路由到其他服务）

### 5. 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端地址：`http://localhost:25173`

## 演示账号

| 账号 | 密码 | 角色 | 姓名 |
|------|------|------|------|
| admin | 123456 | 管理员 | - |
| coach001 | 123456 | 教练 | 唐国义 |
| coach002 | 123456 | 教练 | 张三 |
| student001 | 123456 | 学员 | 王小明 |
| student002 | 123456 | 学员 | 李小红 |

## 演示流程

1. 管理员登录 → 统计驾驶舱 → 查看教练/学员/车辆
2. 排班管理 → 查看或批量生成排班
3. 学员登录 → 查询主教练 → 预约练车
4. 主教练满员 → 调剂推荐 → 调剂预约
5. 教练登录 → 今日预约 → 完成练车 / 标记缺席
6. 学员查看训练档案 → 管理员查看统计
7. 预约 30 秒后自动检查是否完成 → 未完成自动标缺席

## 关键接口

### 对外接口（经 Gateway）

| 接口 | 说明 |
|------|------|
| `POST /api/auth/login` | 登录 |
| `GET /api/auth/me` | 获取当前用户 |
| `GET /api/users` | 用户分页（ADMIN） |
| `GET /api/coaches` | 教练分页 |
| `GET /api/students` | 学员分页 |
| `GET /api/schedules/available` | 可预约排班 |
| `POST /api/reservations` | 创建预约 |
| `GET /api/statistics/admin/overview` | 管理员统计 |

### 内部接口（服务间 Feign 调用，不经 Gateway）

| 接口 | 说明 |
|------|------|
| `GET /internal/user/by-username/{username}` | 按用户名查用户 |
| `GET /internal/coach/{id}` | 按 ID 查教练 |
| `GET /internal/student/by-id/{id}` | 按 ID 查学员 |
| `POST /internal/schedule/increase/{id}` | 增加预约人数（条件 UPDATE） |
| `POST /internal/training-record/create` | 创建训练记录 |

## 系统边界

本系统不涉及：考试预约、考试成绩、交管系统对接、缴费、合同管理。
---

**作者**：yazhoujiucaihezi  
**仓库**：https://github.com/yazhoujiucaihezi/driving-schedule-system
