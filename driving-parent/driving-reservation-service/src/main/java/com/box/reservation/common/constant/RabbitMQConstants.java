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

    // ==================== 延迟投递（超时缺席） ====================

    /**
     * 延迟交换机，类型为 x-delayed-message，依赖 rabbitmq_delayed_message_exchange 插件。
     * <p>
     * 与「TTL 队列 + 死信交换机」的区别：TTL 方案只检查队头消息是否过期，
     * 当同一队列里消息的过期时间跨度很大时（今天下午的课和明天早上的课混在一起），
     * 后排消息会被前排堵住，实际触发时间不可控。延迟交换机由插件内部时间轮调度，
     * 每条消息按自己的 x-delay 独立投递。
     * </p>
     */
    public static final String EXCHANGE_RESERVATION_DELAY = "reservation.delay.exchange";

    /** 延迟交换机类型：由 rabbitmq_delayed_message_exchange 插件注册的自定义类型 */
    public static final String EXCHANGE_TYPE_DELAYED = "x-delayed-message";

    /** 延迟交换机的参数名：指定它内部按哪种路由规则分发消息 */
    public static final String DELAYED_TYPE_PARAM = "x-delayed-type";

    /** 延迟交换机内部分发规则：按路由键精确匹配（我们的缺席路由键是固定值） */
    public static final String DELAYED_TYPE_DIRECT = "direct";

    /**
     * 缺席消息体里的订单 ID 键名。
     * 生产端写入、消费端读取，两边改一处漏一处不会报错，只会静默读不到。
     */
    public static final String MSG_KEY_RESERVATION_ID = "reservationId";

    // ==================== 死信（超时缺席） ====================

    /** 死信交换机 */
    public static final String EXCHANGE_RESERVATION_DLX = "reservation.dlx.exchange";

    /** 缺席处理队列：死信最终落到这里，由监听器标记缺席 */
    public static final String QUEUE_RESERVATION_ABSENT = "reservation.absent.queue";

    /** 缺席处理路由键 */
    public static final String ROUTING_KEY_RESERVATION_ABSENT = "reservation.absent.routingkey";
}
