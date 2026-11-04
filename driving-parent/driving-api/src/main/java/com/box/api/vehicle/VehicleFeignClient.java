package com.box.api.vehicle;

import com.box.api.fallback.VehicleFeignClientFallbackFactory;
import com.box.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "driving-vehicle-service", fallbackFactory = VehicleFeignClientFallbackFactory.class)
public interface VehicleFeignClient {

    @GetMapping("/internal/vehicle/by-id/{id}")
    Result<VehicleDTO> getById(@PathVariable("id") Long id);

    @GetMapping("/internal/vehicle/one")
    Result<VehicleDTO> getOne(
            @RequestParam("coachId") Long coachId,
            @RequestParam("status") String status
    );

    /** 查全部车辆 */
    @GetMapping("/internal/vehicle/list-all")
    Result<List<VehicleDTO>> getAll();

}
