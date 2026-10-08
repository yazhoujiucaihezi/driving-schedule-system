package com.box.vehicle.controller;


import com.box.api.vehicle.VehicleDTO;
import com.box.common.result.Result;
import com.box.vehicle.service.VehicleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/vehicle")

public class VehicleInternalController {

    private final VehicleService vehicleService;

    @GetMapping("/by-id/{id}")
    public Result<VehicleDTO> getById(@PathVariable("id") Long id) {
        return Result.success(vehicleService.getVehicleDTOById(id));
    }

    @GetMapping("/by-one/{coachId}")
    public Result<VehicleDTO> getByOne(@RequestParam("coachId") Long coachId,
                                       @RequestParam("status") String status) {
        return Result.success(vehicleService.getVehicleDTOByCoachId(coachId, status));
    }

    @GetMapping("/count")
    public Result<Long> count() {
        return Result.success(vehicleService.count());
    }
}
