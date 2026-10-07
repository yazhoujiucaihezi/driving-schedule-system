package com.box.coach.controller;

import com.box.api.coach.CoachDTO;
import com.box.coach.service.CoachService;
import com.box.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
