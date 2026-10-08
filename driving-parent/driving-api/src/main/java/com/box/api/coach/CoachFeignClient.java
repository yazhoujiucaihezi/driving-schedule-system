package com.box.api.coach;


import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient("driving-coach-service")
public interface CoachFeignClient {

    @GetMapping("/internal/coach/by-id/{id}")
    CoachDTO getCoachById(@PathVariable("id") Long id);

    @GetMapping("/internal/coach/count/status")
    Long getCoachCountByStatus(@RequestParam("status") String status);

}
