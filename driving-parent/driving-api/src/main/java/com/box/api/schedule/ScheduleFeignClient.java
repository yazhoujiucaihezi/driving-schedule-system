package com.box.api.schedule;

import com.box.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@FeignClient(name = "driving-schedule-service")
public interface ScheduleFeignClient {

    @GetMapping("/internal/schedule/{id}")
    Result<ScheduleDTO> getById(@PathVariable("id") Long id);

    @GetMapping("/internal/schedule/one")
    Result<ScheduleDTO> getOne(
            @RequestParam("coachId") Long coachId,
            @RequestParam("date") LocalDate date,
            @RequestParam("timeSlot") String timeSlot);

    @GetMapping("/internal/schedule/list-adjustable")
    Result<List<ScheduleDTO>> listAdjustable(
            @RequestParam("date") LocalDate date,
            @RequestParam("timeSlot") String timeSlot,
            @RequestParam("excludeCoachId") Long excludeCoachId);

    @PostMapping("/internal/schedule/increase/{id}")
    Result<Integer> increaseCurrentStudents(@PathVariable("id") Long id);

    @PostMapping("/internal/schedule/decrease/{id}")
    Result<Integer> decreaseCurrentStudents(@PathVariable("id") Long id);
}