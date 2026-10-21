package com.box.api.fallback;

import com.box.api.schedule.ScheduleDTO;
import com.box.api.schedule.ScheduleFeignClient;
import com.box.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
public class ScheduleFeignClientFallback implements ScheduleFeignClient {

    @Override
    public Result<ScheduleDTO> getById(Long id) {
        log.error("调用排班服务（driving-schedule-service）获取排班信息失败，触发降级。排班ID: {}", id);
        return Result.error(503, "排班校验服务暂时不可用，请稍后再试");
    }

    @Override
    public Result<ScheduleDTO> getOne(Long coachId, LocalDate date, String timeSlot) {
        log.error("调用排班服务（driving-schedule-service）获取排班信息失败，触发降级。");
        return Result.error(503, "排班校验服务暂时不可用，请稍后再试");
    }

    @Override
    public Result<List<ScheduleDTO>> listAdjustable(LocalDate date, String timeSlot, Long excludeCoachId) {
        log.error("调用排班服务（driving-schedule-service）获取排班信息失败，触发降级。");
        return Result.error(503, "排班校验服务暂时不可用，请稍后再试");
    }

    @Override
    public Result<Integer> increaseCurrentStudents(Long id) {
        log.error("调用排班服务（driving-schedule-service）获取排班信息失败，触发降级。排班ID: {}", id);
        return Result.error(503, "排班校验服务暂时不可用，请稍后再试");
    }

    @Override
    public Result<Integer> decreaseCurrentStudents(Long id) {
        log.error("调用排班服务（driving-schedule-service）获取排班信息失败，触发降级。排班ID: {}", id);
        return Result.error(503, "排班校验服务暂时不可用，请稍后再试")   ;
    }

    @Override
    public Result<ScheduleDTO> getByCoach(Long coachId, LocalDate date, String timeSlot) {
        log.error("调用排班服务（driving-schedule-service）获取排班信息失败，触发降级。");
        return Result.error(503, "排班校验服务暂时不可用，请稍后再试");
    }
}