package com.box.reservation.dto;

import lombok.Data;

/** 取消预约请求 DTO */
@Data
public class ReservationCancelDTO {
    /** 取消原因 */
    private String reason;
}
