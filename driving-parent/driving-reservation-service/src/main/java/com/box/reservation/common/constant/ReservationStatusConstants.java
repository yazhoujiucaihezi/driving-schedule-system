package com.box.reservation.common.constant;

/**
 * 预约单状态常量（对应 reservation.status）
 * <p>
 * 注意：这是<b>预约单</b>的状态，和排班表 coach_schedule.status 的
 * OPEN / CLOSED / CANCELLED 是两套完全不同的枚举，不要混用。
 * 把排班状态写进预约单，会导致所有 where status = 'SUCCESS' 的查询全部查不到数据。
 * </p>
 * <p>
 * 状态流转：
 * <pre>
 * SUCCESS ──取消──→ CANCELLED
 *    │
 *    ├──完成练车──→ COMPLETED
 *    └──超时未到场→ ABSENT
 * </pre>
 * </p>
 */
public final class ReservationStatusConstants {

    private ReservationStatusConstants() {}

    /** 预约成功，名额已占用 */
    public static final String SUCCESS = "SUCCESS";

    /** 已取消，名额已回滚 */
    public static final String CANCELLED = "CANCELLED";

    /** 已完成练车 */
    public static final String COMPLETED = "COMPLETED";

    /** 超时未到场，由延迟队列自动标记 */
    public static final String ABSENT = "ABSENT";
}
