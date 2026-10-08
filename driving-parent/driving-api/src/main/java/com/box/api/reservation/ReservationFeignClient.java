package com.box.api.reservation;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;


@FeignClient("driving-reservation-service")
public interface ReservationFeignClient {

    @GetMapping("/internal/reservation/count/date")
    Long countByDate(@RequestParam("date") LocalDate date);

    @GetMapping("/internal/reservation/count/status")
    Long countByDateAndStatus(@RequestParam("date") LocalDate date, @RequestParam("status") String status);

    @GetMapping("/internal/reservation/count/student")
    Long countByStudentIdAndStatus(@RequestParam("studentId") Long studentId, @RequestParam("status") String status);

    @GetMapping("/internal/reservation/count/isAdjusted")
    long countByStudentIdAndIsAdjusted(@RequestParam("studentId") Long studentId, @RequestParam("isAdjusted") int isAdjusted);
}
