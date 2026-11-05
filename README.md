# 驾校学员练车预约与调度系统

![Java](https://img.shields.io/badge/Java-21-orange.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.0-brightgreen.svg)
![Spring Cloud Alibaba](https://img.shields.io/badge/Spring%20Cloud%20Alibaba-2023.0.1.0-blue.svg)
![Spring AI](https://img.shields.io/badge/Spring%20AI-1.1.2-6DB33F.svg)
![Nacos](https://img.shields.io/badge/Nacos-2.x-1E90FF.svg)
![Sentinel](https://img.shields.io/badge/Sentinel-1.8-4FC08D.svg)
![MySQL](https://img.shields.io/badge/MySQL-8.0-00758F.svg)
![Redis](https://img.shields.io/badge/Redis-7.0-DC382D.svg)
![RabbitMQ](https://img.shields.io/badge/RabbitMQ-3.8-FF6600.svg)
![Vue](https://img.shields.io/badge/Vue-3.4-42B883.svg)
![License](https://img.shields.io/badge/License-MIT-yellow.svg)

基于 **Java 21 + Spring Cloud Alibaba + Spring AI** 构建的驾校排班与练车预约平台，覆盖学员建档、教练与车辆管理、批量排班、并发抢约、练车核销、爽约处置与经营统计的完整业务闭环。

系统重点解决两类问题：

- **自然语言代约**：学员通过对话完成「查询余量 → 满员时推荐同期同科目教练 → 生成预约草稿 → 确认下单」。核心设计不在于接入大模型，而在于**约束大模型在交易链路中的权限边界**——身份不经模型、草稿不写库、下单必须人工确认。
- **放号峰值抢约**：数十名学员在同一时刻争抢同一教练同一时段的名额，需同时解决超卖、请求堆积、跨服务熔断与到点未签到的自动处置。

---

## 目录

- [一、项目概览](#一项目概览)
- [二、AI 智能代约](#二ai-智能代约)
- [三、并发抢约与零超卖](#三并发抢约与零超卖)
- [四、流量治理与熔断降级](#四流量治理与熔断降级)
- [五、履约闭环：到点未签到自动判缺席](#五履约闭环到点未签到自动判缺席)
- [六、批量排班与幂等生成](#六批量排班与幂等生成)
- [七、系统架构与服务划分](#七系统架构与服务划分)
- [八、性能测试](#八性能测试)
- [九、工程实践与设计取舍](#九工程实践与设计取舍)
- [十、快速开始](#十快速开始)

---

## 界面预览

<div align="center">
  <img width="85%" src="https://github.com/user-attachments/assets/3701b8f7-e53b-40a5-9de4-6ddb91c23dcb" alt="统计驾驶舱" />
  <br/><br/>
  <img width="85%" src="https://github.com/user-attachments/assets/8c31e2f1-1d3f-4074-b615-c4c56c02bdd9" alt="学员端排班预约与调剂" />
  <br/><br/>
  <img width="85%" src="https://github.com/user-attachments/assets/463ec2fd-9556-4dbf-a281-1e538ade45f5" alt="Nacos 服务注册列表" />
  <br/><br/>
  <img width="85%" src="https://github.com/user-attachments/assets/48f1a5c8-b33f-4887-bbd6-79bafe9f76e1" alt="AI自动预约功能" />

</div>


---

## 一、项目概览

### 业务背景

驾校业务的核心矛盾集中在「放号」：每天固定时段开放教练的练车名额，学员在短时间内集中争抢。该场景同时涉及高并发写入、名额一致性、跨服务调用与后续履约管理。

### 业务约束

系统设计遵循驾校的真实规则，以下三条约束直接决定了多处技术选型：

| 约束 | 说明 |
| :--- | :--- |
| 容量约束 | 一位教练在一个时段内最多带 5 名学员（`max_students`） |
| 车辆独占 | 一辆教练车在一个时段内仅服务一位教练 |
| 主教练归属 | 学员绑定固定主教练（`main_coach_id`）；主教练满员时可调剂至其他教练（`actual_coach_id`），但训练记录仍归属主教练 |

### 项目规模

| 指标 | 数值 |
| :--- | :--- |
| 业务微服务 | 11 个 |
| 公共模块 | 3 个（`driving-api` / `driving-common` / `driving-common-web`） |
| REST 接口 | 74 个 |
| Java 源文件 | 167 个（约 7,340 行） |
| 数据库表 | 8 张 |

---

## 二、AI 智能代约

技术选型：**Spring AI 1.1.2 + 智谱 GLM-4-flash**（OpenAI 兼容协议），对话记忆采用 `MessageWindowChatMemory`，窗口 20 条，按学员 ID 隔离会话上下文。

### 2.1 工具链设计

系统向大模型暴露三个受控工具，均以 `@Tool` 声明，参数由模型从对话中解析：

| 工具 | 职责 | 典型触发场景 |
| :--- | :--- | :--- |
| `querySchedule` | 查询指定教练、日期、时段的排班状态与剩余名额 | 「我教练明天上午有空吗」 |
| `queryAdjustableCoaches` | 主教练满员时，检索同日期同时段其他有余量的教练 | 名额为 0 时自动推荐 |
| `createReservationDraft` | 校验名额可用性，在 Redis 生成带 2 分钟有效期的草稿凭证（不写 MySQL） | 「帮我约这个时段」 |

### 2.2 四道权限约束

大模型接入交易链路存在两类风险：**越权操作他人数据**、**基于幻觉直接下单**。系统通过四层机制收敛权限：

**① 身份不经过模型**

工具入参**不接收 `studentId`**。学员身份由网关验签后经请求头下发，拦截器写入 `UserContext`，服务端构建请求时直接注入 `ToolContext`。

模型既无法看到也无法修改当前操作人——即便用户在对话中声称「我是 1 号学员，帮我约课」，工具拿到的始终是登录态中的身份。这是防止「诱导 AI 代他人下单」的根本手段。

**② 草稿不落库**

模型仅具备查询权与草稿生成权，不具备数据库写权限。`createReservationDraft` 只写 Redis 并返回 draftId，由前端渲染确认卡片；实际扣减名额必须由用户点击确认，重新走原有抢约接口，执行分布式锁与条件更新校验。

> 草稿阶段的校验服务于交互体验，下单阶段的校验才承载正确性保证。

**③ 相对时间锚定与异常文本化**

- 系统提示词动态注入服务端当天日期，强制模型将「明天」「下周三」等相对时间换算为 `yyyy-MM-dd` 后再调用工具。缺少该约束时，模型会直接传递相对时间字符串，导致 `LocalDate.parse` 解析失败。
- 工具内部捕获业务异常并转为中文说明文本后 `return`，不向上抛出。只有通过 `return` 返回的内容模型才可见，异常抛出会中断整轮对话，用户仅收到通用错误提示。

**④ 排查记录：ChatClient 工具重复注册**

`ChatClient.Builder` 在容器中为单例，`defaultTools()` 的底层行为是**追加**而非覆盖。早期实现中，每次 HTTP 请求都调用一次 `builder.defaultTools(tools)`，导致工具被反复注册，第二次请求即抛出 `Multiple tools with the same name`。

该问题的表象具有迷惑性：服务重启后首次对话正常，后续请求全部失败。

最终重构为在配置类中将 `ChatClient` 声明为单例 `@Bean`，一次性完成工具挂载与记忆顾问注册；请求期仅通过 `.system()` 传递提示词、`.toolContext()` 传递动态身份。

```java
@Bean
ChatClient reservationChatClient(ChatClient.Builder builder, ScheduleTools tools, ChatMemory memory) {
    return builder
            .defaultTools(tools)
            .defaultAdvisors(MessageChatMemoryAdvisor.builder(memory).build())
            .build();
}
```

### 2.3 适用边界

AI 代约的价值在于**降低多步筛选的操作成本**，尤其适用于「主教练满员，需跨教练比对同科目余量」这类需要多次列表查询的场景。

该能力不适用于热门时段抢约。名额通常在数秒内售罄，而对话链路包含语义理解、数据库查询、草稿生成、等待用户确认等多个环节，端到端耗时高于人工点击一个数量级。高竞争时段仍由前端直接调用抢约接口。

明确界定能力边界，避免为追求技术亮点而将大模型置于不适用的场景，是本次接入的重要原则。

---

## 三、并发抢约与零超卖

### 问题

50 个线程并发争抢同一排班的 10 个名额。

### 方案演进

最初实现为「分布式锁内先 `SELECT` 判断余量，再 `UPDATE` 扣减」。压测中出现过 **11 笔成功订单（超卖 1 笔）**：锁超时释放后，原线程基于已失效的快照完成了更新。

最终收敛为两层防线，各自承担明确职责：

```sql
UPDATE coach_schedule
SET current_students = current_students + 1
WHERE id = ? AND current_students < max_students
```

| 层次 | 机制 | 作用 |
| :--- | :--- | :--- |
| 应用层 | Redisson 分布式锁，粒度 `lock:schedule:{scheduleId}` | 拦截并发竞争；锁粒度限定在单个排班时段，不同教练的排班互不阻塞 |
| 数据库层 | 条件更新，影响行数为 0 即判定名额已满 | 在锁失效或超时情况下仍保证不超卖 |

应用层负责降低竞争强度，数据库层负责保证最终一致性，二者不可互相替代。

### 结果

多轮 50 线程压测后，`current_students` 恒等于 `max_students`，**零超卖**。

---

## 四、流量治理与熔断降级

### 问题

未引入限流时，超出处理能力的请求全部堆积在 Redisson 锁等待队列中，Tomcat 工作线程被占满，最长阻塞达 **1794 ms**。

### 方案

| 组件 | 配置 | 作用 |
| :--- | :--- | :--- |
| 接口限流 | 抢约入口 `@SentinelResource` + 自定义 `BlockExceptionHandler` | 超额请求在最外层快速失败，不穿透至锁与数据库 |
| 服务熔断 | `feign.sentinel.enabled=true` + `FallbackFactory` | 下游慢调用或异常时熔断，阻断级联故障，降级返回统一 503 提示 |
| 规则持久化 | `sentinel-datasource-nacos` | 流控与降级规则从 Nacos 读取，避免服务重启后内存规则丢失 |

### 压测对比（50 并发抢 10 名额）

| 阶段 | 吞吐量 | 平均 RT | 最长阻塞 | 超卖 |
| :--- | :--- | :--- | :--- | :--- |
| 仅分布式锁 | 43.1 req/s | 614 ms | 1794 ms | 0 |
| 接入 Sentinel | **57.2 req/s**（峰值 93.3） | **404 ms**（-34.2%） | **1029 ms** | 0 |

---

## 五、履约闭环：到点未签到自动判缺席

### 业务规则

练车开始时刻后 10 分钟仍未完成签到核销（上午 `08:10`、下午 `14:10`），预约单自动标记为缺席（`ABSENT`）。

判定的时间基准是**绝对时间点**，而非「预约成功后固定时长」，这决定了技术选型。

### 方案对比

| 方案 | 缺陷 |
| :--- | :--- |
| TTL 队列 + 死信队列（DLX） | RabbitMQ 的 TTL 队列仅检查队头消息是否过期。学员可预约未来 1–7 天不同时段，消息延迟跨度从数小时到数天不等，远期预约会阻塞近期预约，实际触发时间不可控。该限制源于队列机制本身，无法通过参数调整解决 |

### 最终实现

启用 `rabbitmq_delayed_message_exchange` 插件，使用 `x-delayed-message` 类型交换机。

```text
预约成功 (SUCCESS)
      │  delay = (上课时刻 + 10min) − now；已过截止时间则取 0，立即投递
      ▼
reservation.delay.exchange   (x-delayed-message)
      │  消息头携带 x-delay，由插件按各自到期时间独立调度
      ▼
reservation.absent.queue
      │
      ▼
消费者：订单状态仍为 SUCCESS → 标记 ABSENT
        已取消 / 已核销        → 跳过
```

### 设计要点

- **幂等性**：延迟消息会重复投递，消费端仅处理 `SUCCESS` 状态的订单，已取消或已核销的记录直接跳过，不会覆盖正常完结的训练记录。
- **时间口径集中管理**：上课时刻与宽限时长统一定义在 `ScheduleTimeConstants`，调整上课时间仅需修改一处。
- **已知上限**：插件使用 32 位整数存储延迟毫秒数，理论上限约 **24.8 天**。当前排班周期为 7 天，处于安全范围；若后续支持更远期的预约，需在投递前增加判断与降级处理。

### 排查记录：队列重复声明导致启动失败

`driving-notification-service` 中曾重复声明同名队列，且 TTL 参数为 30 秒（预约服务为 30 分钟）。两个服务对同一队列声明不一致，后启动者会因 `PRECONDITION_FAILED` 直接启动失败。

修正方式为删除通知服务的重复声明——该服务仅消费 `reservation.success.queue` 用于发送站内通知，不应参与延迟队列与缺席队列的拓扑声明。

---

## 六、批量排班与幂等生成

### 场景

管理员按「教练列表 × 日期区间 × 上下午时段」三个维度批量生成排班：已存在的时段跳过，可选自动分配车辆，最终返回成功与跳过条数。

### 实现要点

| 优化点 | 做法 | 收益 |
| :--- | :--- | :--- |
| 判重 | 一次性查出目标区间内已有排班，在内存构建 `Set<String>`（key 为 `教练ID_日期_时段`） | 三重循环内 O(1) 判重，避免 224 次数据库往返 |
| 车辆分配 | 一次 `getAll` 拉取车辆，本地过滤状态并按教练分组 | 避免在循环内逐个发起 Feign 调用 |
| 计数口径 | `skipped = 理论总数 − created`，理论总数以**全量教练数**为分母 | 差值自然涵盖「非 NORMAL 教练整人跳过」与「已存在」两类情况 |
| 并发兜底 | 唯一索引 `uk_coach_date_slot (coach_id, schedule_date, time_slot)` | 应用层判重负责性能，数据库约束负责正确性 |

### 验证结果

16 名教练 × 7 天 × 2 时段，单次生成 **224 条**排班；同一批次重复执行返回 `created = 0`、`skipped = 224`，幂等验证通过。

---

## 七、系统架构与服务划分

### 服务清单

| 服务模块 | 端口 | 核心职责 |
| :--- | :--- | :--- |
| `driving-gateway` | 8000 | 统一路由、跨域、JWT 全局验签、用户信息透传 |
| `driving-auth-service` | 18081 | 登录认证、JWT 签发 |
| `driving-user-service` | 18080 | 账号体系、角色管理与关联业务 ID |
| `driving-coach-service` | 18082 | 教练档案、状态维护 |
| `driving-student-service` | 18083 | 学员档案、主教练绑定、训练科目 |
| `driving-vehicle-service` | 18084 | 教练车台账、车辆状态 |
| `driving-schedule-service` | 18085 | 批量排班、余量查询、条件更新扣减 |
| `driving-training-service` | 18086 | 练车核销、训练记录沉淀 |
| `driving-reservation-service` | 18087 | 抢约、取消、调剂、延迟缺席投递、AI 代约工具链 |
| `driving-statistics-service` | 18088 | 教练负载、排班利用率趋势 |
| `driving-notification-service` | 18090 | 消费预约成功消息，发送站内通知 |

### 公共模块分层

| 模块 | 内容 | 设计意图 |
| :--- | :--- | :--- |
| `driving-api` | Feign 客户端、DTO、降级工厂 | 供所有业务服务依赖，避免接口契约重复定义 |
| `driving-common` | 统一返回结构、异常体系、JWT 工具 | 与 Web 无关的基础能力 |
| `driving-common-web` | 拦截器、`UserContext`、安全配置 | 隔离 WebFlux（gateway）与 Spring MVC（业务服务）的依赖冲突 |

### 技术栈

| 分类 | 组件 |
| :--- | :--- |
| 语言与框架 | Java 21、Spring Boot 3.2.0 |
| 微服务治理 | Spring Cloud 2023.0.1、Spring Cloud Alibaba 2023.0.1.0、Nacos、Gateway、OpenFeign、LoadBalancer |
| 容灾与流控 | Sentinel 1.8（接口限流、Feign 熔断）、`sentinel-datasource-nacos` |
| 安全 | Spring Security、JWT |
| 数据访问 | MyBatis-Plus、MySQL 8.0 |
| 缓存与协调 | Redis 7.0、Redisson |
| 消息中间件 | RabbitMQ 3.8、`rabbitmq_delayed_message_exchange` |
| AI | Spring AI 1.1.2、智谱 GLM-4-flash |
| 前端 | Vue 3.4、TypeScript 5.4、Vite 5、Pinia、Tailwind CSS 3.4 |
| 测试工具 | Apache JMeter 5.6.3、go-stress-testing |

---

## 八、性能测试

测试环境为本地单机部署，分别使用 go-stress-testing 与 Apache JMeter 5.6.3（CLI 模式）对读接口与写接口进行基准测试。

### 教练 / 排班查询接口

| 指标 | 数值 |
| :--- | :--- |
| 压测工具 | go-stress-testing |
| 并发与总量 | 100 并发 / 13,000 请求 |
| 峰值吞吐 | **1810.25 req/s** |
| 平均响应 | 55.24 ms |
| TP90 / TP95 / TP99 | 70 ms / 76 ms / 94 ms |
| 错误率 | 0% |

### 并发抢约接口

| 指标 | 仅分布式锁 | 接入 Sentinel |
| :--- | :--- | :--- |
| 并发场景 | 50 并发抢 10 名额 | 50 并发抢 10 名额 |
| 吞吐量 | 43.1 req/s | **57.2 req/s（峰值 93.3）** |
| 平均响应 | 614 ms | **404 ms**（-34.2%） |
| 最长阻塞 | 1794 ms | **1029 ms** |
| 最终扣减 | 10（零超卖） | 10（零超卖） |

### 结论

- 多轮并发冲击下，排班表已约人数与有效订单数严格相等，未出现超卖。
- 引入限流后，超额请求在 11 ms 内被拦截返回，不再进入锁等待队列；平均响应时间下降 34.2%，最长阻塞时间缩短 42.6%。

<div align="center">
  <img width="85%" src="https://github.com/user-attachments/assets/5028c328-9ff1-422f-82bb-ae0ff05079dc" alt="Go 压测 1810 QPS" />
  <br/><br/>
  <img width="85%" src="https://github.com/user-attachments/assets/bf50d88a-6ce4-4f55-834f-874469f7a9f5" alt="JMeter 抢约压测结果" />
</div>

---

## 九、工程实践与设计取舍

### 1. 微服务拆分粒度

从业务体量判断，单个驾校系统以模块化单体承载即可满足需求。本项目拆分为 11 个服务，目的是完整演练服务注册发现、网关鉴权透传、跨服务 Feign 熔断与分布式锁协同等分布式能力。

对应代价是明确的：一次抢约需跨越 4 个服务（`reservation → schedule → coach → student`），调用链变长、超时点增多、本地联调复杂度上升。这是技术演练的成本，而非业务必要性。

### 2. 大模型的能力边界

系统只将大模型用于「低频、需要多步筛选」的查询与草稿生成，不将其接入「高频、强一致」的名额扣减环节。不让模型参与写操作，是本设计中最重要的一条边界。

### 3. 已知不足与演进方向

| 问题 | 现状 | 演进方向 |
| :--- | :--- | :--- |
| 批量排班插入 | 单事务内逐条插入，200+ 条可控 | 上千条时改为分批提交，每批 500 条 |
| 统计接口 | 实时 SQL 聚合，无缓存 | 数据量增长后引入缓存或定时预计算 |
| 下单幂等 | 依赖业务状态判断与唯一索引 | 补充全局请求防重机制，阻止重复提交 |

---

## 十、快速开始

### 环境要求

| 组件 | 版本 |
| :--- | :--- |
| JDK | 21 |
| Maven | 3.9+ |
| MySQL | 8.0 |
| Redis | 7.0 |
| Nacos | 2.x |
| RabbitMQ | 3.8（需启用 `rabbitmq_delayed_message_exchange` 插件） |
| Node.js | 18+ |

### 启动步骤

1. **启动基础设施**

   MySQL 与 Redis 可通过项目根目录的 `docker-compose.yml` 快速启动；Nacos 与 RabbitMQ 需自行部署。

   RabbitMQ 需启用延迟插件：

   ```bash
   rabbitmq-plugins enable rabbitmq_delayed_message_exchange
   ```

2. **初始化数据库**

   ```bash
   mysql -u root -p < sql/init.sql
   ```

3. **配置环境变量**

   ```bash
   # AI 代约功能所需；未设置时该接口不可用，其余功能正常
   export ZHIPU_API_KEY=你的密钥
   ```

4. **启动后端服务**

   按依赖顺序启动：`gateway` → `auth` / `user` → `coach` / `student` / `vehicle` → `schedule` → `reservation` → `training` / `statistics` / `notification`

5. **启动前端**

   ```bash
   cd frontend
   npm install
   npm run dev
   ```

   开发服务器默认代理至网关 `8000` 端口。

---

## License

本项目基于 [MIT License](LICENSE) 开源。
