package com.box.reservation.dto.ai;
import java.time.LocalDate;

public record SecureQueryScheduleParam(String coachName, LocalDate date, Long studentId, String timeSlot) {
    public static SecureQueryScheduleParam build(AiToolParams aiParams, Long studentId) {
        LocalDate date = LocalDate.parse(aiParams.dateString());
        if(date.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("日期早于今天");
        }
        String rawSlot = aiParams.timeSlot();
        String finalSlot = "MORNING"; // 默认上午
        if (rawSlot != null) {
            if (rawSlot.contains("下午") || rawSlot.contains("PM") || rawSlot.contains("AFTERNOON")) {
                finalSlot = "AFTERNOON";
            }
        }

        return new SecureQueryScheduleParam(aiParams.coachName(), date, studentId, finalSlot);
    }
}