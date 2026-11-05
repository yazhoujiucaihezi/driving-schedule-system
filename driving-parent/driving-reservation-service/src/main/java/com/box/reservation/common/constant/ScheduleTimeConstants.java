package com.box.reservation.common.constant;

import java.time.LocalTime;

/**
 * 排班时段的上课时间点与缺席判定规则
 * <p>
 * {@code time_slot} 字段存的是 MORNING / AFTERNOON 这种枚举值，不带具体时刻。
 * 缺席判定需要「这节课几点开始」，所以在这里把映射关系固定下来：
 * 改上课时间只改这一个文件，不用去业务代码里翻。
 * </p>
 */
public final class ScheduleTimeConstants {

    private ScheduleTimeConstants() {}

    /** 上午时段：08:00 开始 */
    public static final LocalTime MORNING_START = LocalTime.of(8, 0);

    /** 下午时段：14:00 开始 */
    public static final LocalTime AFTERNOON_START = LocalTime.of(14, 0);

    /** 时段枚举值：上午 */
    public static final String SLOT_MORNING = "MORNING";

    /** 时段枚举值：下午 */
    public static final String SLOT_AFTERNOON = "AFTERNOON";

    /**
     * 缺席判定的宽限分钟数：上课后超过这个时间仍未签到，判定为缺席。
     * 目前是 10 分钟，即 08:10 / 14:10 之后仍未核销的预约会被标记 ABSENT。
     */
    public static final long ABSENT_GRACE_MINUTES = 10;

    /**
     * 根据时段取上课时间点。
     * 只有明确是 AFTERNOON 才取下午，其余（含 null）一律按上午处理——与排班默认值一致。
     */
    public static LocalTime startTimeOf(String timeSlot) {
        return SLOT_AFTERNOON.equalsIgnoreCase(timeSlot) ? AFTERNOON_START : MORNING_START;
    }
}
