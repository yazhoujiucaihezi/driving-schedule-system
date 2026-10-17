package com.box.api.fallback;

import com.box.api.coach.CoachDTO;
import com.box.api.coach.CoachFeignClient;
import com.box.common.result.Result;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Component
public class CoachFeignClientFallbackFactory implements FallbackFactory<CoachFeignClient> {

    @Override
    public CoachFeignClient create(Throwable cause) {
        System.err.println("🔥 预约服务调用教练服务失败，触发降级，原因: " + cause.getMessage());
        return new CoachFeignClient() {
            @Override
            public Result<CoachDTO> getCoachById(Long id) {
                return Result.error(503, "教练排班服务暂时不可用，请稍后再试！");
                }
            };
    }
}
