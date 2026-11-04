package com.box.reservation.config;

import com.box.reservation.common.constant.RabbitMQConstants;
import com.box.reservation.common.constant.ReservationStatusConstants;
import com.box.reservation.entity.Reservation;
import com.box.reservation.mapper.ReservationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class AbsentListener {

    private final ReservationMapper reservationMapper;

    @RabbitListener(queues = RabbitMQConstants.QUEUE_RESERVATION_ABSENT)
    public void handle(Map<String, Object> msg) {
        Long reservationId = ((Number) msg.get("reservationId")).longValue();
        log.info("[缺席核验] 收到消息 reservationId={}", reservationId);

        Reservation reservation = reservationMapper.selectById(reservationId);
        if (reservation == null) {
            // 订单被删或消息是脏数据，直接丢弃，不要抛异常否则会无限重试
            log.warn("[缺席核验] 订单不存在，跳过 reservationId={}", reservationId);
            return;
        }

        // 幂等：消息会重复投递，只有仍处于 SUCCESS 的单才允许标记缺席
        if (!ReservationStatusConstants.SUCCESS.equals(reservation.getStatus())) {
            log.info("[缺席核验] 订单当前状态为 {}，非 SUCCESS，跳过 reservationId={}",
                    reservation.getStatus(), reservationId);
            return;
        }

        LocalDateTime time = LocalDateTime.now();
        reservation.setStatus(ReservationStatusConstants.ABSENT);
        reservation.setCancelTime(time);
        reservation.setCancelReason("缺席");
        reservation.setUpdateTime(time);
        reservationMapper.updateById(reservation);

        log.info("[缺席核验] 已标记为缺席 reservationId={} scheduleId={}",
                reservationId, reservation.getScheduleId());
    }
}
