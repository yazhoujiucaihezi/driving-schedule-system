package com.box.statistics.vo;

import lombok.Data;

/** 教练负载 */
@Data
public class CoachLoadVO {

    /** 教练ID */
    private Long coachId;

    /** 教练姓名 */
    private String coachName;

    /** 排班条数：统计区间内该教练被排了多少场 */
    private long scheduleCount;

    /** 预约数：这些排班上产生的预约总条数（不含已取消） */
    private long reservationCount;

    /** 已完成数：其中已经练完的条数 */
    private long completedCount;

    /** 总容量：所有排班 max_students 之和，即理论上能带多少人 */
    private long capacity;

    /** 已用容量：所有排班 current_students 之和，即实际占用掉的名额 */
    private long usedCapacity;

    /**
     * 利用率：已用容量 ÷ 总容量，百分比（0~100，保留两位小数）
     * <p>
     * 分母为 0（该教练在区间内没有排班）时返回 0，不能除零。
     * </p>
     */
    private double utilizationRate;
}
