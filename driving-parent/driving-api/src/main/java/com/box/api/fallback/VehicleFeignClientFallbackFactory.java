package com.box.api.fallback;

import com.box.api.vehicle.VehicleDTO;
import com.box.api.vehicle.VehicleFeignClient;
import com.box.common.result.Result;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 车辆服务降级兜底
 * <p>
 * 下游不可用时统一返回 code=503，调用方据此区分「车辆服务挂了」与「车辆不存在（data 为 null）」。
 * </p>
 */
@Slf4j
@Component
public class VehicleFeignClientFallbackFactory implements FallbackFactory<VehicleFeignClient> {

    @Override
    public VehicleFeignClient create(Throwable cause) {
        log.warn("调用车辆服务（driving-vehicle-service）失败，触发降级", cause);
        return new VehicleFeignClient() {

            @Override
            public Result<VehicleDTO> getById(Long id) {
                log.error("降级：获取车辆信息失败。车辆ID: {}", id);
                return Result.error(503, "车辆服务暂时不可用，请稍后再试");
            }

            @Override
            public Result<VehicleDTO> getOne(Long coachId, String status) {
                log.error("降级：按教练查询车辆失败。教练ID: {}, 车辆状态: {}", coachId, status);
                return Result.error(503, "车辆服务暂时不可用，请稍后再试");
            }

            @Override
            public Result<List<VehicleDTO>> getAll() {
                log.error("降级：查询全部车辆失败");
                return Result.error(503, "车辆服务暂时不可用，请稍后再试");
            }
        };
    }
}
