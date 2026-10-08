package com.box.api.vehicle;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "driving-vehicle-service")
public interface VehicleFeignClient {

    @GetMapping("/internal/vehicle/by-id/{id}")
    VehicleDTO getById(@PathVariable("id") Long id);

    @GetMapping("/internal/vehicle/one")
    VehicleDTO getOne(
            @RequestParam("coachId") Long coachId,
            @RequestParam("status") String status
    );

    @GetMapping("/internal/vehicle/count")
    Long count();
}