package com.box.reservation.controller;

import com.box.common.result.Result;
import com.box.reservation.service.ReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/reservation")
public class ReservationInternalController {

    private final ReservationService reservationService;

    @GetMapping("/count/date")
    public Result<Long> countByDate(@RequestParam("date") String date) {
        return Result.success(reservationService.countByDate(date));
    }

    @GetMapping("/count/status")
    public Result<Long> countByDateAndStatus(@RequestParam("date") LocalDate date, @RequestParam("status") String status) {
        return Result.success(reservationService.countByDateAndStatus(date, status));
    }
    @GetMapping("/count/student")
    public Result<Long> countByStudentIdAndStatus(@RequestParam("studentId") Long studentId, @RequestParam("status") String status) {
        return Result.success(reservationService.countByStudentIdAndStatus(studentId, status));
    }

    @GetMapping("/count/isAdjusted")
    public Result<Long> countByStudentIdAndIsAdjusted(@RequestParam("studentId") Long studentId, @RequestParam("isAdjusted") boolean isAdjusted) {
        return Result.success(reservationService.countByStudentIdAndIsAdjusted(studentId, isAdjusted));
    }
}
