# 🚗 Driving Schedule System - 驾校学员练车预约与调度管理系统

![Java](https://img.shields.io/badge/Java-21-orange.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen.svg)
![Spring Cloud Alibaba](https://img.shields.io/badge/Spring%20Cloud%20Alibaba-2022.x-blue.svg)
![Nacos](https://img.shields.io/badge/Nacos-2.x-1E90FF.svg)
![Sentinel](https://img.shields.io/badge/Sentinel-1.8-4FC08D.svg)
![MySQL](https://img.shields.io/badge/MySQL-8.0-00758F.svg)
![Redisson](https://img.shields.io/badge/Redisson-3.x-DC382D.svg)
![RabbitMQ](https://img.shields.io/badge/RabbitMQ-3.8-FF6600.svg)
![License](https://img.shields.io/badge/License-MIT-yellow.svg)

基于 **Spring Cloud Alibaba** 微服务架构构建的多校区驾培预约与排班调度平台。系统围绕学员、教练、训练车辆及排班计划等核心领域进行服务拆分，重点解决放号高峰期的**高并发抢约冲突（防超卖）**、**突发流量限流与跨服务熔断降级**，以及**预约单超时未确认自动流转回滚**等分布式场景问题。

---

## 📸 系统界面与压测预览

> 💡 提示：将截图直接粘贴至此处后，建议使用 `< img width="85%" src="图片链接" />` 替换默认标签以保持最佳视觉比例。

<div align="center">
  <!-- 在此处替换你的系统界面截图或压测截图 -->
  <!-- < img width="85%" src="你的图片地址1" alt="系统界面预览" /> -->
</div>

---

## ✨ 核心业务与微服务划分

| 服务模块 | 服务名称 | 核心职责描述 |
| :--- | :--- | :--- |
| **网关服务** | `gateway-service` | 统一路由转发、跨域处理、全局 JWT Token 鉴权校验与用户信息请求头透传 |
| **用户服务** | `user-service` | 学员/教练/管理员账号体系、基于 **Spring Security + JWT** 的认证授权与角色权限管控 |
| **教练与排班服务** | `coach-service` | 教练档案维护、每日练车时段排班生成、可用名额核验与数据库乐观锁扣减/回滚 |
| **预约订单服务** | `appointment-service` | 高并发练车抢约、**Redisson** 分布式锁控制、**Sentinel** 限流降级、**RabbitMQ** 超时延迟关单 |
| **车辆管理服务** | `vehicle-service` | 训练车辆信息维护、科目车型匹配、车辆维保状态监控与调度分配 |

---

## 🛠️ 技术栈

* **微服务治理**：Spring Cloud Alibaba、Spring Cloud Gateway、Nacos（注册中心 & 配置中心）、OpenFeign（声明式远程调用）
* **容灾与流控**：Sentinel（接口 QPS 峰值限流、OpenFeign 慢调用/异常熔断降级、基于 Nacos 的流控规则动态持久化）
* **核心框架与安全**：Spring Boot 3、Spring Security、JWT、MyBatis-Plus
* **数据存储与分布式协调**：MySQL 8.0、Redis 7、Redisson（分布式可重入锁）
* **消息中间件**：RabbitMQ 3.8（TTL + DLX 死信交换机实现延迟队列）
* **压测与工具**：JMeter、go-stress-testing、Apifox、Maven、Docker、Git

---

## 🏗️ 系统架构设计

```text
                           ┌──────────────────────────┐
                           │    客户端 (Web / 小程序)   │
                           └────────────┬─────────────┘
                                        │ HTTP / RESTful API (携带 JWT)
                                        ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                        Spring Cloud Gateway 网关层                          │
│                 (统一路由转发 / 全局 JWT 解析 / 权限前置校验)                 │
└─────────┬─────────────────────────────┬──────────────────────────┬─────────┘
          │                             │                          │
          ▼                             ▼                          ▼
┌───────────────────┐         ┌───────────────────┐      ┌───────────────────┐
│   user-service    │         │appointment-service│      │  vehicle-service  │
│ (Spring Security) │         │   (预约核心服务)    │      │   (车辆调度服务)   │
└───────────────────┘         └─────────┬─────────┘      └───────────────────┘
                                        │
           ┌────────────────────────────┼────────────────────────────┐
           │ 1. Sentinel 限流 &         │ 2. OpenFeign 远程调用       │ 3. 投递延迟消息
           │    Redisson 分布式锁       │    (含 Fallback 熔断兜底)   │    (TTL + DLX)
           ▼                            ▼                            ▼
┌───────────────────┐         ┌───────────────────┐      ┌───────────────────┐
│     Redis 7       │         │   coach-service   │      │   RabbitMQ 3.8    │
│(分布式锁/缓存/鉴权) │         │ (排班核验与名额扣减)│      │  (死信延迟关单队列) │
└───────────────────┘         └─────────┬─────────┘      └─────────┬─────────┘
                                        │                          │
                                        ▼                          ▼
                              ┌───────────────────┐      ┌───────────────────┐
                              │     MySQL 8.0     │◄─────┤  DeadLetterConsumer│
                              │ (乐观锁防超卖持久化)│      │(超时取消 & 名额回滚)│
                              └───────────────────┘      └───────────────────┘
