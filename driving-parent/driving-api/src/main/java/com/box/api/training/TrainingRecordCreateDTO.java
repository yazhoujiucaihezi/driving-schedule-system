package com.box.api.training;

import lombok.Data;
import java.time.LocalDate;

@Data
public class TrainingRecordCreateDTO {
    private Long reservationId;
    private Long studentId;
    private Long coachId;
    private Long vehicleId;
    private LocalDate trainingDate;
    private String timeSlot;
    private String trainingContent;
    private String coachComment;
    private String result;
}