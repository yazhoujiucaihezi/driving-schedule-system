package com.box.reservation.dto.ai;
import java.time.LocalDate;

public record SecureQueryScheduleParam(String coachName, LocalDate date, Long studentId, String timeSlot) {
    public static SecureQueryScheduleParam build(AiToolParams aiParams, Long studentId) {
        LocalDate date = LocalDate.parse(aiParams.dateString());
        if(date.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("日期早于今天");
        }
        return new SecureQueryScheduleParam(aiParams.coachName(), date, studentId, aiParams.timeSlot());
    }
}