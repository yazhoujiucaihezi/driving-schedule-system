package com.box.reservation.common.constant;

/**
 * RabbitMQ 队列 / 交换机 / 路由键常量
 * <p>
 * 统一管理预约链路上的 MQ 名称，避免字符串散落在 MQConfig 和业务代码里各写一份，
 * 改名字时漏改一处就变成「消息发到没人监听的队列」——不报错，消息静默堆积。
 * </p>
 * <p>
 * 拓扑结构：
 * <pre>
 * 预约成功通知
 *   生产者 → reservation.success.exchange
 *              └─(routingkey)─→ reservation.success.queue
 *
 * 延迟核验（TTL 到期后转死信）
 *   生产者 → (默认交换机) ─→ reservation.delay.queue   [设置 TTL，绑定死信交换机]
 *                                  │ 到期
 *                                  ▼
 *                          reservation.dlx.exchange
 *                                  └─(absent.routingkey)─→ reservation.absent.queue
 * </pre>
 * </p>
 */
public final class RabbitMQConstants {

    private RabbitMQConstants() {}

    // ==================== 预约成功通知 ====================

    /** 预约成功通知队列 */
    public static final String QUEUE_RESERVATION_SUCCESS = "reservation.success.queue";

    /** 预约成功通知交换机 */
    public static final String EXCHANGE_RESERVATION_SUCCESS = "reservation.success.exchange";

    /** 预约成功通知路由键 */
    public static final String ROUTING_KEY_RESERVATION_SUCCESS = "reservation.success.routingkey";

    // ==================== 延迟核验队列 ====================

    /** 延迟队列：投递时设置消息 TTL，到期后由死信交换机转走 */
    public static final String QUEUE_RESERVATION_DELAY = "reservation.delay.queue";

    // ==================== 死信（超时缺席） ====================

    /** 死信交换机 */
    public static final String EXCHANGE_RESERVATION_DLX = "reservation.dlx.exchange";

    /** 缺席处理队列：死信最终落到这里，由监听器标记缺席 */
    public static final String QUEUE_RESERVATION_ABSENT = "reservation.absent.queue";

    /** 缺席处理路由键 */
    public static final String ROUTING_KEY_RESERVATION_ABSENT = "reservation.absent.routingkey";
}
