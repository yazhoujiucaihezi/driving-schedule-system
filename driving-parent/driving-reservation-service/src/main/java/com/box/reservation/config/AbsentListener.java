package com.box.reservation.config;


import com.box.reservation.entity.Reservation;
import com.box.reservation.mapper.ReservationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
@Slf4j
@RequiredArgsConstructor
public class AbsentListener {

    private final ReservationMapper mapper;

    @RabbitListener(queues = "reservation.absent.queue")
    public void handle(Map<String,Object> msg){
        Long reservationId = Long.valueOf(msg.get("reservationId").toString());
        Reservation reservation = mapper.selectById(reservationId);
        if(reservation == null){
            log.info("Reservation not found: {}", reservationId);
            return;
        }
        if (!"SUCCESS".equals(reservation.getStatus())) {
            log.info("预约状态已是 {}，无需标记", reservation.getStatus());
            return;
        }
        reservation.setStatus("ABSENT");
        reservation.setCancelReason("超时未到场");
        mapper.updateById(reservation);
        log.info("标记预约缺席成功：{}", reservationId);
    }

}
