package com.box.api.schedule;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ScheduleDTO {
    private Long id;
    private Long coachId;
    private Long vehicleId;
    private LocalDate scheduleDate;
    private String timeSlot;
    private Integer maxStudents;
    private Integer currentStudents;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}