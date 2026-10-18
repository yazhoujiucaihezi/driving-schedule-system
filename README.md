# 🚗 Driving Schedule System - 驾校学员练车预约与调度管理系统

![Java](https://img.shields.io/badge/Java-21-orange.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-brightgreen.svg)
![Spring Cloud Alibaba](https://img.shields.io/badge/Spring%20Cloud%20Alibaba-2023.0.1.0-blue.svg)
![Nacos](https://img.shields.io/badge/Nacos-2.x-1E90FF.svg)
![Sentinel](https://img.shields.io/badge/Sentinel-1.8-4FC08D.svg)
![MySQL](https://img.shields.io/badge/MySQL-8.0-00758F.svg)
![Redisson](https://img.shields.io/badge/Redisson-3.x-DC382D.svg)
![RabbitMQ](https://img.shields.io/badge/RabbitMQ-3.8-FF6600.svg)
![License](https://img.shields.io/badge/License-MIT-yellow.svg)

基于 **Java 21 + Spring Cloud Alibaba** 微服务架构构建的驾校练车预约与排班调度平台。系统围绕学员、教练、训练车辆及排班计划等核心领域进行服务拆分，重点解决放号高峰期的**高并发抢约冲突（防超卖）**、**突发流量限流与跨服务熔断降级**、**主教练满员智能调剂**以及**预约超时未到场自动标记缺席**等分布式场景问题。

---

## 📸 系统界面与运行预览

<div align="center">
  <img width="85%" src="https://github.com/user-attachments/assets/3701b8f7-e53b-40a5-9de4-6ddb91c23dcb" alt="统计驾驶舱看板" />
  <img width="85%" src="https://github.com/user-attachments/assets/8c31e2f1-1d3f-4074-b615-c4c56c02bdd9" alt="学员端排班预约与调剂" />
</div>

<div align="center">
  <br/>
  <img width="85%" src="https://github.com/user-attachments/assets/463ec2fd-9556-4dbf-a281-1e538ade45f5" alt="Nacos 服务注册列表" />
  <img width="85%" src="https://github.com/user-attachments/assets/966fa505-bb9c-41cb-8b2b-b86f26dc629c" alt="Sentinel 流控监控面板" />
</div>

---

## ✨ 核心业务与微服务划分

| 服务模块 | 服务名称 | 核心职责描述 |
| :--- | :--- | :--- |
| **网关服务** | `driving-gateway` | 统一路由转发、跨域处理、全局 JWT Token 鉴权校验与用户信息请求头透传 |
| **认证与用户服务** | `driving-auth-service`<br>`driving-user-service` | 学员/教练/管理员账号体系、登录认证、JWT 签发与权限角色下发 |
| **教练与排班服务** | `driving-coach-service`<br>`driving-schedule-service` | 教练档案维护、每日练车时段排班批量生成、可用名额核验与数据库条件 `UPDATE` 扣减 |
| **预约订单服务** | `driving-reservation-service` | 高并发练车抢约、**Redisson** 分布式锁控制、**Sentinel** 限流降级、满员调剂、**RabbitMQ** 延迟任务投递 |
| **学员与车辆服务** | `driving-student-service`<br>`driving-vehicle-service` | 学员档案管理、训练车辆信息维护、科目车型匹配与调度分配 |
| **训练与统计服务** | `driving-training-service`<br>`driving-statistics-service` | 练车完结确认、训练历史档案沉淀、管理员数据驾驶舱与教练负载分析 |
| **通知服务** | `driving-notification-service` | 异步消费 MQ 消息，处理预约成功站内通知及超时未到场自动缺席标记 |

---

## 🛠️ 技术栈

* **微服务治理**：Spring Cloud Alibaba 2023.0.1.0、Spring Cloud Gateway、Nacos（注册中心 & 配置中心）、OpenFeign、LoadBalancer
* **容灾与流控**：Sentinel（接口 QPS 峰值限流、OpenFeign 慢调用/异常熔断降级、基于 Nacos 的流控规则动态持久化）
* **核心框架与安全**：Java 21、Spring Boot 3.2、Spring Security、JWT、MyBatis-Plus
* **数据存储与分布式协调**：MySQL 8.0、Redis 7、Redisson（分布式可重入锁）
* **消息中间件**：RabbitMQ 3.8（异步消息解耦 + TTL & DLX 死信交换机实现延迟队列）
* **前端与工具**：Vue 3、TypeScript、Tailwind CSS、Apache JMeter 5.6.3、go-stress-testing、Maven、Docker

---

## 🏗️ 系统架构设计

```text
┌──────────────────────────┐
│  客户端 (Vue 3 前端页面)   │
└────────────┬─────────────┘
             │ HTTP / RESTful API (携带 JWT)
             ▼
┌────────────────────────────────────────────────────────────────────────────┐
│ driving-gateway 网关层                                                     │
│ (统一路由转发 / JwtGlobalFilter 全局验签 / 用户上下文请求头透传)              │
└─────────┬─────────────────────────────┬──────────────────────────┬─────────┘
          │                             │                          │
          ▼                             ▼                          ▼
┌───────────────────┐         ┌───────────────────┐      ┌───────────────────┐
│ auth / user-svc   │         │reservation-service│      │ coach / student   │
│ (Spring Security) │         │ (预约核心服务)    │      │ (基础档案与查询)  │
└───────────────────┘         └─────────┬─────────┘      └───────────────────┘
                                        │
            ┌───────────────────────────┼───────────────────────────┐
            │ 1. Sentinel 限流 &        │ 2. OpenFeign 远程调用     │ 3. 投递延迟消息
            │ Redisson 分布式锁         │ (含 Fallback 熔断兜底)    │ (TTL + DLX)
            ▼                           ▼                           ▼
┌───────────────────┐         ┌───────────────────┐      ┌───────────────────┐
│ Redis 7           │         │ schedule-service  │      │ RabbitMQ 3.8      │
│(分布式锁/缓存/鉴权)│         │ (排班核验与名额扣减)│      │ (死信延迟核验队列)│
└───────────────────┘         └─────────┬─────────┘      └─────────┬─────────┘
                                        │                          │
                                        ▼                          ▼
                              ┌───────────────────┐      ┌───────────────────┐
                              │ MySQL 8.0         │◄─────┤ notification-svc  │
                              │ (条件UPDATE防超卖) │      │(超时标缺席&异步通知)│
                              └───────────────────┘      └───────────────────┘
```

---

## 🔥 核心技术实现与难点攻关

### 1. 高并发排班抢约与“零超卖”双重防线

针对每天固定放号时段大量学员同时争抢同一热门教练排班名额的并发冲突场景，构建“分布式锁 + 数据库条件更新”双重保障：

* **细粒度分布式锁控制**：在 `driving-reservation-service` 中设计以 `lock:schedule:{scheduleId}` 为粒度的 Redisson 分布式锁，将锁范围严格限制在单一排班时段内，避免全局锁导致其他教练排班请求阻塞。
* **跨服务扣减与乐观锁兜底**：抢到锁的线程通过 OpenFeign 调用 `driving-schedule-service` 核验余量，并在 SQL 层面执行带条件更新（`UPDATE coach_schedule SET current_students = current_students + 1 WHERE id = #{id} AND current_students < max_students`），锁防并发冲突，SQL 兜底防锁超时失效，从底层彻底杜绝超卖。

### 2. Sentinel 峰值限流、Feign 熔断降级与规则持久化

为防止放号瞬间超额并发请求大量阻塞在 Redisson 锁等待队列中拖垮 Tomcat 线程池：

* **接口快速失败与统一响应**：在抢约入口结合 `@SentinelResource` 与自定义 `BlockExceptionHandler` 实施 QPS 峰值限流，超出阈值的请求在最外层直接拦截并返回标准 JSON 降级提示，避免无效请求穿透至底层锁与数据库。
* **OpenFeign 跨服务熔断**：开启 `feign.sentinel.enabled=true` 并编写 `FallbackFactory` 降级工厂；当下游 `driving-schedule-service` 或 `driving-coach-service` 出现慢查询或异常时自动触发熔断，阻断级联雪崩。
* **Nacos 规则持久化**：引入 `sentinel-datasource-nacos` 将流控与降级规则同步至 Nacos 配置中心，解决微服务重启后内存流控规则丢失问题。

### 3. 基于 RabbitMQ 死信队列（DLX）的超时自动流转闭环

针对学员预约成功后爽约、未在规定时间内完成练车签到核销的问题：

```text
创建预约单 (状态: RESERVED) ──► 投递消息至普通队列 (设置消息 TTL)
                                           │
                                           ▼ (消息过期未消费，自动路由)
                               死信交换机 (DLX) ──► 死信队列 (Dead Letter Queue)
                                                           │
                                                           ▼
                                           消费者监听: 幂等校验当前预约单状态
                                                           │
                                                           ▼
                                       若仍未完成练车 -> 自动标记状态为 ABSENT (缺席)
```

* **消费端幂等性保障**：死信消费者在执行状态流转时，严格校验预约单前置状态（仅针对仍处于未完结状态的记录更新为 ABSENT），防止消息重复投递覆盖正常完结的训练记录。

---

## 📊 性能与并发压测报告

本项目在本地台式机环境下，分别使用 go-stress-testing（轻量级协程压测工具）与 Apache JMeter 5.6.3 (CLI 模式) 对高频读接口与高并发写接口进行了基准测试：

### 1. 教练服务 (`driving-coach-service`) 吞吐量压测

* **压测配置**：100 并发协程（Concurrency = 100），连续发送 13,000 次请求。
* **实测指标**：
  * 峰值吞吐量（QPS）：稳定达到 **1810.25 QPS**
  * 响应耗时（RT）：平均耗时 55.24 ms | TP90：70 ms | TP95：76 ms | TP99：94 ms
  * 可用性：13,000 次请求全部正常响应（HTTP 200），失败率 0%
 
  <div align="center">
  <img width="85%" src="https://github.com/user-attachments/assets/5028c328-9ff1-422f-82bb-ae0ff05079dc" alt="Go 压测 1810 QPS" />
  </div>

### 2. 高并发抢约防超卖与 Sentinel 限流对比压测 (`driving-reservation-service`)

* **压测场景**：使用 JMeter 命令行非 GUI 模式（`-n -t`）模拟 50 个并发线程在 1 秒内同时登录并争抢同一排班时段的 10 个可用练车名额（`max_students = 10`）。

| 测试阶段 | 并发线程数 | 吞吐量 (Throughput) | 平均耗时 (Avg RT) | 最短耗时 (Min) | 最长阻塞耗时 (Max) | 数据库最终扣减结果 |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| 优化前（仅开启 Redisson 锁） | 50 | 43.1 / sec | 614 ms | 28 ms | 1794 ms | `current_students = 10` (0 超卖) |
| 优化后（接入 Sentinel 限流） | 50 | 57.2 / sec（峰值 93.3/s） | 404 ms | 11 ms | 1029 ms | `current_students = 10` (0 超卖) |

<div align="center">
  <img width="85%" src="https://github.com/user-attachments/assets/bf50d88a-6ce4-4f55-834f-874469f7a9f5" alt="JMeter 压测结果" />
  <br/><br/>
  <img width="85%" src="https://github.com/user-attachments/assets/4321f73f-3022-4f0c-b4a5-c2ec0bb6e010" alt="MySQL 0超卖结果验证" />
</div>

**压测结论分析：**

* **数据强一致性**：在多轮 50 线程并发冲击下，排班表最终已约人数与有效预约订单数均严格等于 10，实现 0 超卖。
* **限流削峰效果**：未加 Sentinel 前，后排线程全部阻塞在 Redisson 锁等待及锁内 OpenFeign 调用上，导致最大响应时间拉长至 1794 ms；接入 Sentinel 快速失败机制后，超额并发请求在 11 ms 内即被快速拦截返回，接口平均耗时下降 34.2%，最长阻塞时间缩短至 1029 ms。

---

## 📂 项目工程结构

```text
driving-parent/
├── driving-gateway/             # Spring Cloud Gateway 统一网关服务 (8000)
├── driving-auth-service/        # 用户登录认证与 JWT 签发服务 (18081)
├── driving-user-service/        # 用户账号管理服务 (18080)
├── driving-coach-service/       # 教练资料管理微服务 (18082)
├── driving-student-service/     # 学员资料管理微服务 (18083)
├── driving-vehicle-service/     # 训练车辆管理微服务 (18084)
├── driving-schedule-service/    # 排班生成与名额扣减微服务 (18085)
├── driving-reservation-service/ # 预约调度核心微服务 - Redisson/Sentinel/MQ (18087)
├── driving-training-service/    # 练车记录与档案服务 (18086)
├── driving-statistics-service/  # 数据驾驶舱统计服务 (18088)
├── driving-notification-service/# MQ 消费者与通知服务 (18090)
├── driving-common/              # 公共基础模块 (Result封装 / JWT工具，无MVC依赖)
├── driving-common-web/          # Web公共模块 (SecurityConfig / UserContext / 拦截器)
├── driving-api/                 # Feign 契约与 DTO 共享模块
├── sql/                         # 数据库初始化脚本
└── pom.xml                      # Maven 父工程依赖版本管理
```

---

## 🚀 快速启动指南

### 1. 环境准备

* **JDK**：21+
* **MySQL**：8.0+
* **Redis**：7.0+
* **Nacos Server**：2.x（默认端口 8848）
* **Sentinel Dashboard**：1.8+（默认端口 8080）
* **RabbitMQ**：3.8+（默认端口 5672 / 管理后台 15672）

### 2. 配置与启动步骤

* 导入 `sql/` 目录下的初始化脚本至本地 MySQL。
* 启动 Nacos、Redis、RabbitMQ 及 Sentinel 控制台服务（支持 `docker-compose up -d` 一键启动）。
* 在 Nacos 配置中心导入各微服务的 YAML 配置及 Sentinel 流控规则 JSON 文件。
* 编译公共模块：`mvn clean install -pl driving-common,driving-common-web,driving-api -am`。
* 按顺序启动各业务微服务（建议使用 IDEA 复合启动 Compound），最后启动 `driving-gateway`。
* 进入 `frontend` 目录执行 `npm install && npm run dev` 启动前端（默认访问地址：`http://localhost:25173`）。

---

## 📄 开源协议

本项目基于 MIT License 开源协议发布。

**作者**：yazhoujiucaihezi | **仓库**：https://github.com/yazhoujiucaihezi/driving-schedule-system
```
