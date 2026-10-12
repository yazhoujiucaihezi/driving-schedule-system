package com.box.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class ReservationListener {

    @RabbitListener(queues = "reservation.success.queue")
    public void listen(Map<String, Object> msg) {
        log.info("========== 收到预约成功消息 ==========");
        log.info("预约ID: {}", msg.get("reservationId"));
        log.info("学员ID: {}", msg.get("studentId"));
        log.info("教练ID: {}", msg.get("coachId"));
        log.info("日期: {}", msg.get("date"));
        log.info("时段: {}", msg.get("timeSlot"));
        log.info("========== 模拟发送短信通知学员 ==========");
    }
}
