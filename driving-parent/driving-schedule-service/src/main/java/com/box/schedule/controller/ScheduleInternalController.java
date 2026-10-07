package com.box.schedule.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.box.api.schedule.ScheduleDTO;
import com.box.common.result.Result;
import com.box.schedule.entity.CoachSchedule;
import com.box.schedule.mapper.CoachScheduleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/internal/schedule")
@RequiredArgsConstructor
public class ScheduleInternalController {

    private final CoachScheduleMapper scheduleMapper;

    @GetMapping("/{id}")
    public Result<ScheduleDTO> getById(@PathVariable("id") Long id) {
        return Result.success(toDTO(scheduleMapper.selectById(id)));
    }

    @GetMapping("/one")
    public Result<ScheduleDTO> getOne(@RequestParam Long coachId,
                                      @RequestParam LocalDate date,
                                      @RequestParam String timeSlot) {
        CoachSchedule s = scheduleMapper.selectOne(new LambdaQueryWrapper<CoachSchedule>()
                .eq(CoachSchedule::getCoachId, coachId)
                .eq(CoachSchedule::getScheduleDate, date)
                .eq(CoachSchedule::getTimeSlot, timeSlot));
        return Result.success(toDTO(s));
    }

    @GetMapping("/list-adjustable")
    public Result<List<ScheduleDTO>> listAdjustable(@RequestParam LocalDate date,
                                                    @RequestParam String timeSlot,
                                                    @RequestParam Long excludeCoachId) {
        List<CoachSchedule> list = scheduleMapper.selectList(new LambdaQueryWrapper<CoachSchedule>()
                .eq(CoachSchedule::getScheduleDate, date)
                .eq(CoachSchedule::getTimeSlot, timeSlot)
                .eq(CoachSchedule::getStatus, "OPEN")
                .ne(CoachSchedule::getCoachId, excludeCoachId)
                .apply("current_students < max_students"));
        return Result.success(list.stream().map(this::toDTO).toList());
    }

    @PostMapping("/increase/{id}")
    public Result<Integer> increase(@PathVariable Long id) {
        return Result.success(scheduleMapper.increaseCurrentStudents(id));
    }

    @PostMapping("/decrease/{id}")
    public Result<Integer> decrease(@PathVariable Long id) {
        return Result.success(scheduleMapper.decreaseCurrentStudents(id));
    }

    private ScheduleDTO toDTO(CoachSchedule s) {
        if (s == null) return null;
        ScheduleDTO d = new ScheduleDTO();
        BeanUtils.copyProperties(s, d);
        return d;
    }
}