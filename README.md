# 🚗 驾校学员练车预约与调度管理系统 (Driving Schedule System) ![Java](https://img.shields.io/badge/Java-21-orange.svg) ![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-brightgreen.svg) ![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2023.0.1-blue.svg) ![Spring Cloud Alibaba](https://img.shields.io/badge/Spring%20Cloud%20Alibaba-2023.0.1.0-1E90FF.svg) ![Sentinel](https://img.shields.io/badge/Sentinel-Flow%20Control-4FC08D.svg) ![Redisson](https://img.shields.io/badge/Redisson-Distributed%20Lock-DC382D.svg) ![RabbitMQ](https://img.shields.io/badge/RabbitMQ-3.8-FF6600.svg) ![Vue](https://img.shields.io/badge/Vue-3.x-42b883.svg) > 基于 **Java 21 + Spring Cloud Alibaba 2023** 微服务架构构建的智能练车预约与排班调度平台。 --- ## 📖 项目简介与定位 本系统围绕驾校内部学员练车管理，解决传统电话预约和微信群接龙带来的预约混乱、并发超额抢号、教练负载不均等问题。 **核心定位**：本项目专注于**驾校内部练车预约与调度管理**，明确系统边界（不做交管考试预约、不做成绩管理、不做缴费与合同管理）。 | 传统业务痛点 | 系统工程方案 | | :--- | :--- | | 电话预约 / 微信群接龙混乱 | 在线按日期、时段可视化查询与预约 | | 热门时段并发抢号导致名额超额 | **Redisson 分布式锁 + 数据库条件 UPDATE** 双保险防超卖 + **Sentinel** 峰值限流 | | 主教练排班满员后无法练车 | **智能调剂推荐** + 临时调剂预约机制 | | 教练工作量分配不均 | 排班容量控制 + 统计驾驶舱负载分析 | | 预约后爽约 / 超时未到场 | **RabbitMQ 延迟队列（TTL + DLX）** 自动核验并标记缺席（`ABSENT`） | | 线下练车无记录沉淀 | 教练确认完成 + 训练档案生成 + 异步消息通知 | --- ## 🏗️ 系统架构与模块划分 ### 1. 架构拓扑图 ```text 浏览器 / 前端 (Vue 3 + TS + Vite : 25173) │ ▼ driving-gateway (8000) —— 统一入口、JwtGlobalFilter 验签、解析用户信息透传请求头 │ ├─► driving-auth-service (18081) 认证服务（登录认证、签发 JWT Token） ├─► driving-user-service (18080) 用户服务（教练/学员/管理员账号管理） ├─► driving-coach-service (18082) 教练服务（教练档案维护、高并发查询） ├─► driving-student-service (18083) 学员服务（学员档案与主教练绑定） ├─► driving-vehicle-service (18084) 车辆服务（训练车辆资料与状态管理） ├─► driving-schedule-service (18085) 排班服务（排班管理、批量生成、条件 UPDATE 扣减） ├─► driving-reservation-service (18087) 预约服务（核心抢约、Redisson 锁、Sentinel 限流、满员调剂） ├─► driving-training-service (18086) 训练记录服务（练车完结归档、训练历史档案） ├─► driving-statistics-service (18088) 统计服务（数据驾驶舱、教练负载与预约趋势分析） └─► driving-notification-service (18090) 通知服务（RabbitMQ 异步消费、站内消息推送） │ ▼ Nacos 2.1（注册/配置中心） · Sentinel（限流熔断） · MySQL 8.0 · Redis 7 · RabbitMQ 3.8 

2. 公共依赖模块设计

公共模块核心职责说明driving-common纯净工具层：统一返回结果 Result、全局业务异常、JwtUtils（无 Spring MVC 依赖，供 WebFlux 网关安全引入）driving-common-webWeb 增强层：SecurityConfig、UserContext（ThreadLocal 上下文）、请求头解析拦截器（供各 MVC 业务服务引入）driving-apiFeign 契约层：集中管理跨服务调用的 DTO 与 @FeignClient 接口定义 

🛠️ 技术栈

层级采用技术与版本后端基座Java 21、Spring Boot 3.2、Spring Cloud 2023.0.1、Spring Cloud Alibaba 2023.0.1.0认证与安全Spring Security、JWT（jjwt）、Gateway 全局过滤器鉴权持久层与数据库MyBatis-Plus、MySQL 8.0缓存与分布式锁Redis 7、Redisson（分布式可重入锁）服务治理与容灾Nacos 2.1（注册/配置）、OpenFeign、Spring Cloud LoadBalancer、Sentinel（限流/熔断/Nacos 持久化）消息队列RabbitMQ 3.8（异步通知解耦、TTL + DLX 死信延迟队列）前端技术栈Vue 3、TypeScript、Vite、Pinia、Tailwind CSS 

🔥 核心技术亮点

1. 网关统一鉴权与上下文无感透传

网关前置验签：在 driving-gateway 层通过 JwtGlobalFilter 统一拦截校验 JWT 签名与有效期。

请求头透传解耦：验签通过后解析出 userId、role、relatedId 注入 HTTP 请求头转发至下游；各业务微服务通过 driving-common-web 中的拦截器自动组装至 UserContext，业务代码直接读取上下文，彻底解耦 JWT 解析逻辑。

2. Redisson 分布式锁 + 数据库条件 UPDATE 双保险防超卖

针对同一排班时段（scheduleId）的高并发争抢，在 driving-reservation-service 与 driving-schedule-service 间构建双重防线：

// 1. Redisson 细粒度分布式锁（按具体排班 ID 加锁，避免全局阻塞） RLock lock = redissonClient.getLock("lock:schedule:" + scheduleId); if (lock.tryLock(3, 10, TimeUnit.SECONDS)) { try { // 2. Feign 远程调用排班服务，执行数据库条件 UPDATE 乐观锁兜底 // UPDATE coach_schedule SET current_students = current_students + 1 // WHERE id = ? AND current_students < max_students scheduleFeignClient.increaseStudentCount(scheduleId); } finally { lock.unlock(); } } 

设计考量：分布式锁在应用层拦截绝大部分并发冲突；数据库条件 UPDATE（判断受影响行数）作为最终防线，防止极端情况下锁超时释放引发的数据超卖。

3. Sentinel 峰值限流、Feign 熔断降级与规则持久化

预约入口快速失败：针对高并发抢约接口配置 @SentinelResource 与自定义 BlockExceptionHandler 全局异常返回；当瞬时流量超过阈值时直接返回标准 JSON 限流提示，避免海量请求堆积在分布式锁等待队列中耗尽线程池。

跨服务熔断兜底：开启 OpenFeign 对 Sentinel 的支持并实现 FallbackFactory，当下游服务异常或慢调用时快速降级，防止服务雪崩。

规则动态持久化：通过 sentinel-datasource-nacos 将限流规则持久化至 Nacos，解决服务重启后规则丢失问题。

4. RabbitMQ 异步通知与死信队列（DLX）自动标记缺席

异步通知解耦：预约创建成功后投递 MQ 消息，由 driving-notification-service 异步消费生成通知，主链路毫秒级返回；即使通知服务短暂不可用也绝不阻塞核心预约交易。

超时自动标记缺席：

利用 RabbitMQ 消息 TTL + 死信交换机（DLX） 实现延迟检查任务。

预约创建后投递延迟消息（演示环境配置为 30 秒），到期进入死信队列触发核验：若当前预约状态仍未完成，系统自动将其标记为 ABSENT（缺席）。

消费端幂等保障：更新前严格校验当前状态，防止消息重复投递导致状态被错误覆盖。

5. 工程模块化与 WebFlux/MVC 依赖隔离

将公共模块精细拆分为 driving-common 与 driving-common-web。

driving-gateway 基于响应式 WebFlux 构建，仅依赖纯净的 driving-common，规避了引入 spring-boot-starter-web 导致的容器启动冲突；其余业务微服务引入 driving-common-web，开箱即用获得统一权限与拦截器配置。

📊 性能与并发压测报告

针对系统内“高频查询（读多）”与“高并发抢约扣减（写冲突）”两类不同特性的场景，分别选用轻量级协程压测工具 go-stress-testing 与多链路编排工具 Apache JMeter 5.6.3 (CLI 模式) 进行了实测验证：

场景一：教练服务 (driving-coach-service) 极限吞吐量测试

测试工具：go-stress-testing（基于 Go 协程模拟高并发持续施压，统计长尾分位延迟）

压测配置：100 并发协程（Concurrency = 100），累计发送 13,000 次请求。

实测结果：

峰值吞吐量（QPS）：稳定达到 1810.25 QPS

响应延迟分布：平均耗时 55.24 ms | TP90：70 ms | TP95：76 ms | TP99：94 ms

可用性：13,000 次请求全部正常响应（HTTP 200），失败率 0%

<div align="center"> <!-- 👇 在此处直接粘贴你的第一张 1810.25 QPS 压测截图 --> < img width="85%" src="在此处替换为你的1810_QPS截图链接" alt="coach-service 吞吐量压测报告" /> </div>

场景二：多用户并发抢约防超卖与 Sentinel 限流测试 (driving-reservation-service)

测试工具：Apache JMeter 5.6.3（使用非 GUI 命令行模式 -n -t，模拟 50 个独立学员账号“并发登录提取 JWT + 并发争抢同一排班时段”完整链路，共 100 次请求）

业务初始条件：同一排班时段可用练车名额 max_students = 10（初始 current_students = 0）。

优化前后同口径实测对比：

测试阶段并发线程数吞吐量 (Throughput)平均耗时 (Avg RT)最短耗时 (Min)最长耗时 (Max)数据库最终落库结果优化前（仅开启 Redisson 锁）5043.1 / sec614 ms28 ms1794 mscurrent_students <= 10（0 超卖）优化后（接入 Sentinel 限流）5057.2 / sec（瞬时 93.3/s）404 ms11 ms1029 mscurrent_students <= 10（0 超卖） 

<div align="center"> <!-- 👇 在此处直接粘贴你的第二张 JMeter (57.2/s) PowerShell 压测截图 --> < img width="85%" src="在此处替换为你的JMeter_Sentinel压测截图链接" alt="reservation-service JMeter抢约压测报告" /> </div>

工程复盘与结论：

防超卖验证：在 50 线程瞬时争抢 10 个名额的高并发冲突下，依托 Redisson 锁与排班表条件 UPDATE，数据库最终人数严格 ≤ 10，实现 0 超卖。

Sentinel 削峰效果：未加 Sentinel 前，超额请求全部阻塞在锁等待及锁内 Feign 远程调用上，导致最大耗时达 1794 ms；引入 Sentinel 快速失败后，超额请求在 11 ms 内即被前置拦截，平均耗时下降 34.2%（614 ms -> 404 ms），最长阻塞时间压降至 1029 ms。

🚀 快速启动

1. 启动基础设施

# 启动 MySQL、Redis、Nacos、RabbitMQ docker-compose up -d 基础设施访问地址端口MySQLlocalhost3306Redislocalhost6379Nacos 控制台http://localhost:8848/nacos8848RabbitMQ 控制台http://localhost:1567215672 

2. 初始化数据库

docker exec -i mysql mysql -uroot -p123 < sql/init.sql 

3. 编译公共模块

cd driving-parent mvn clean install -pl driving-common,driving-common-web,driving-api -am 

4. 启动微服务

建议使用 IDEA 复合启动（Run/Debug Configurations → + → Compound）：

确保基础设施（MySQL、Redis、Nacos、RabbitMQ、Sentinel）已就绪。

启动各业务服务（顺序随意，均自动注册至 Nacos）。

最后启动 driving-gateway（端口 8000）。

5. 启动前端

cd frontend npm install npm run dev 

前端访问地址：http://localhost:25173

🎮 演示账号与核心流程

演示账号

账号密码角色姓名admin123456管理员-coach001123456教练唐国义coach002123456教练张三student001123456学员王小明student002123456学员李小红 

核心闭环演示流程

管理员登录 → 查看统计驾驶舱（负载与趋势） → 管理教练/学员/车辆基础档案。

排班管理 → 按日期、时段、教练、车辆创建或批量生成排班计划。

学员登录 → 查询主教练可用排班 → 在线发起练车预约（触发 MQ 异步通知）。

满员调剂 → 当主教练排班满员时 → 系统自动推荐同科目其他空闲教练 → 完成临时调剂预约。

教练登录 → 查看今日预约列表 → 确认完成练车（生成训练档案）或手动标记缺席。

超时自动标缺席 → 预约后若超时未完成（演示环境设为 30 秒），RabbitMQ 死信队列自动触发核验并将状态标记为 ABSENT（缺席）。

🔌 关键接口清单

对外接口（经 Gateway 8000 路由转发）

接口路径功能说明POST /api/auth/login账号登录并签发 JWTGET /api/auth/me获取当前登录用户信息GET /api/users用户分页查询（ADMIN 权限）GET /api/coaches教练列表分页查询GET /api/students学员列表分页查询GET /api/schedules/available查询可预约排班列表POST /api/reservations创建练车预约（核心防超卖与限流入口）GET /api/statistics/admin/overview管理员驾驶舱全局统计数据 

内部接口（服务间 OpenFeign 契约调用，不经 Gateway）

接口路径功能说明GET /internal/user/by-username/{username}按用户名查询账号信息GET /internal/coach/{id}按 ID 查询教练详情GET /internal/student/by-id/{id}按 ID 查询学员详情POST /internal/schedule/increase/{id}增加排班已约人数（数据库条件 UPDATE 防超卖）POST /internal/training-record/create练车完成后创建训练档案记录 

作者：yazhoujiucaihezi 仓库地址：https://github.com/yazhoujiucaihezi/driving-schedule-system
