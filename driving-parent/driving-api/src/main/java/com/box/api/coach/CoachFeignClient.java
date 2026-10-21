package com.box.api.coach;


import com.box.api.fallback.CoachFeignClientFallbackFactory;
import com.box.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;


@FeignClient(name = "driving-coach-service", fallbackFactory = CoachFeignClientFallbackFactory.class)
public interface CoachFeignClient {

    @GetMapping("/internal/coach/by-id/{id}")
    Result<CoachDTO> getCoachById(@PathVariable("id") Long id);

    @GetMapping("/internal/coach/count/name")
    Result<Integer> getCountByName(@RequestParam("name") String name,
                                   @RequestParam("date") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date,
                                   @RequestParam("timeSlot") String timeSlot);

    @GetMapping("/internal/coach/get-by-name")
    Result<CoachDTO> getCoachByName(@RequestParam("name") String name);
}
