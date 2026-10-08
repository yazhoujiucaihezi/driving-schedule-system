package com.box.coach.controller;

import com.box.api.coach.CoachDTO;
import com.box.coach.service.CoachService;
import com.box.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/coach")
@RequiredArgsConstructor
public class CoachInternalController {

    private final CoachService coachService;

    @GetMapping("/by-id/{id}")
    public Result<CoachDTO> getCoachById(@PathVariable("id") Long id) {
        CoachDTO coachDTO = coachService.getCoachById(id);
        if (coachDTO == null) {
            return Result.success(null);
        }
        return Result.success(coachDTO);
    }

    @GetMapping("/count/status")
    public Long getCoachCountByStatus(@RequestParam("status") String status) {
        return coachService.getCoachCountByStatus(status);
    }
}
