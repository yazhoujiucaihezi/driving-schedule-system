package com.box.api.coach;


import com.box.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient("driving-coach-service")
public interface CoachFeignClient {

    @GetMapping("/internal/coach/by-id/{id}")
    Result<CoachDTO> getCoachById(@PathVariable("id") Long id);

}
