package com.box.api.fallback;

import com.box.api.training.TrainingRecordCreateDTO;
import com.box.api.training.TrainingRecordFeignClient;
import com.box.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 训练记录服务降级兜底
 * <p>
 * 注意：写练车记录失败意味着「预约已完成但训练档案没落库」，属于数据不一致，
 * 调用方拿到 503 后必须有明确处理（重试或补偿），不能静默忽略。
 * </p>
 */
@Slf4j
@Component
public class TrainingRecordFeignClientFallbackFactory implements FallbackFactory<TrainingRecordFeignClient> {

    @Override
    public TrainingRecordFeignClient create(Throwable cause) {
        log.warn("调用训练记录服务（driving-training-service）失败，触发降级", cause);
        return dto -> {
            log.error("降级：写入练车记录失败。预约ID: {}, 学员ID: {}, 教练ID: {}",
                    dto == null ? null : dto.getReservationId(),
                    dto == null ? null : dto.getStudentId(),
                    dto == null ? null : dto.getCoachId());
            return Result.error(503, "训练记录服务暂时不可用，请稍后再试");
        };
    }
}
