package com.box.api.reservation;

import org.springframework.cloud.openfeign.FeignClient;


@FeignClient("driving-reservation-service")
public interface ReservationFeignClient {
}
