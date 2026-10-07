package com.box.vehicle.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.box.api.vehicle.VehicleDTO;
import com.box.vehicle.dto.VehicleCreateDTO;
import com.box.vehicle.dto.VehicleQueryDTO;
import com.box.vehicle.dto.VehicleUpdateDTO;
import com.box.vehicle.vo.VehicleSimpleVO;
import com.box.vehicle.vo.VehicleVO;

import java.util.List;

public interface VehicleService {
    Page<VehicleVO> queryPage(VehicleQueryDTO dto);
    VehicleVO getById(Long id);
    VehicleVO create(VehicleCreateDTO dto);
    VehicleVO update(Long id, VehicleUpdateDTO dto);
    void updateStatus(Long id, String status);
    List<VehicleSimpleVO> simpleList();

    VehicleDTO getVehicleDTOById(Long id);

    VehicleDTO getVehicleDTOByCoachId(Long coachId, String status);
}
