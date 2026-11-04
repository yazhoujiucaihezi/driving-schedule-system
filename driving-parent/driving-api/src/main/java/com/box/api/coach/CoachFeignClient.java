package com.box.api.coach;


import com.box.api.fallback.CoachFeignClientFallbackFactory;
import com.box.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;


@FeignClient(name = "driving-coach-service", fallbackFactory = CoachFeignClientFallbackFactory.class)
public interface CoachFeignClient {

    @GetMapping("/internal/coach/by-id/{id}")
    Result<CoachDTO> getCoachById(@PathVariable("id") Long id);

    @GetMapping("/internal/coach/count/name")
    Result<Long> getCountByName(@RequestParam("name") String name,
                                   @RequestParam("date") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date,
                                   @RequestParam("timeSlot") String timeSlot);

    @GetMapping("/internal/coach/get-by-name")
    Result<CoachDTO> getCoachByName(@RequestParam("name") String name);

    @GetMapping("/internal/coach/all")
    Result<List<CoachDTO>> selectAllByStatus();

    /** 查全部教练，不区分状态 */
    @GetMapping("/internal/coach/list-all")
    Result<List<CoachDTO>> getAll();

    /** 按状态查教练 */
    @GetMapping("/internal/coach/by-status")
    Result<List<CoachDTO>> getByStatus(@RequestParam("status") String status);

    /** 按 id 列表批量查教练 */
    @GetMapping("/internal/coach/by-ids")
    Result<List<CoachDTO>> getByIds(@RequestParam("ids") List<Long> ids);
}
