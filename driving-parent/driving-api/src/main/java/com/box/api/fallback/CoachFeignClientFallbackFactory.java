package com.box.api.fallback;

import com.box.api.coach.CoachDTO;
import com.box.api.coach.CoachFeignClient;
import com.box.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
public class CoachFeignClientFallbackFactory implements FallbackFactory<CoachFeignClient> {

    @Override
    public CoachFeignClient create(Throwable cause) {
        log.warn("调用教练服务（driving-coach-service）失败，触发降级", cause);
        return new CoachFeignClient() {
            @Override
            public Result<CoachDTO> getCoachById(Long id) {
                log.error("降级：获取教练信息失败。教练ID: {}", id);
                return Result.error(503, "教练服务暂时不可用，请稍后再试");
            }

            @Override
            public Result<Long> getCountByName(String name, LocalDate date, String timeSlot) {
                log.error("降级：统计教练排班失败。教练名: {}, 日期: {}, 时段: {}", name, date, timeSlot);
                return Result.error(503, "教练服务暂时不可用，请稍后再试");
            }

            @Override
            public Result<CoachDTO> getCoachByName(String name) {
                log.error("降级：按姓名查询教练失败。教练名: {}", name);
                return Result.error(503, "教练服务暂时不可用，请稍后再试");
            }

            @Override
            public Result<List<CoachDTO>> selectAllByStatus() {
                log.error("降级：按status查询教练失败");
                return Result.error(503, "教练服务暂时不可用，请稍后再试");
            }

            @Override
            public Result<List<CoachDTO>> getAll() {
                log.error("降级：查询全部教练失败");
                return Result.error(503, "教练服务暂时不可用，请稍后再试");
            }

            @Override
            public Result<List<CoachDTO>> getByStatus(String status) {
                log.error("降级：按状态查询教练失败。状态: {}", status);
                return Result.error(503, "教练服务暂时不可用，请稍后再试");
            }

            @Override
            public Result<List<CoachDTO>> getByIds(List<Long> ids) {
                log.error("降级：按 id 批量查询教练失败。ids: {}", ids);
                return Result.error(503, "教练服务暂时不可用，请稍后再试");
            }
        };
    }
}
