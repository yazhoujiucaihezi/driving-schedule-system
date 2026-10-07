package com.box.training.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.box.api.coach.CoachDTO;
import com.box.api.coach.CoachFeignClient;
import com.box.api.student.StudentDTO;
import com.box.api.student.StudentFeignClient;
import com.box.api.vehicle.VehicleDTO;
import com.box.api.vehicle.VehicleFeignClient;
import com.box.common.result.Result;
import com.box.training.dto.TrainingRecordQueryDTO;
import com.box.training.entity.TrainingRecord;
import com.box.training.mapper.TrainingRecordMapper;
import com.box.training.service.TrainingRecordService;
import com.box.training.vo.TrainingRecordVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TrainingRecordServiceImpl implements TrainingRecordService {

    private final TrainingRecordMapper trainingRecordMapper;
    private final CoachFeignClient coachFeignClient;
    private final StudentFeignClient studentFeignClient;
    private final VehicleFeignClient vehicleFeignClient;

    @Override
    public Page<TrainingRecordVO> myRecords(Long studentId, Integer page, Integer size) {
        Page<TrainingRecord> p = trainingRecordMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<TrainingRecord>().eq(TrainingRecord::getStudentId, studentId).orderByDesc(TrainingRecord::getCreateTime));
        return toVOPage(p);
    }

    @Override
    public Page<TrainingRecordVO> coachRecords(Long coachId, Integer page, Integer size) {
        Page<TrainingRecord> p = trainingRecordMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<TrainingRecord>().eq(TrainingRecord::getCoachId, coachId).orderByDesc(TrainingRecord::getCreateTime));
        return toVOPage(p);
    }

    @Override
    public Page<TrainingRecordVO> adminQuery(TrainingRecordQueryDTO dto) {
        LambdaQueryWrapper<TrainingRecord> w = new LambdaQueryWrapper<>();
        if (dto.getCoachId() != null) w.eq(TrainingRecord::getCoachId, dto.getCoachId());
        if (dto.getStartDate() != null) w.ge(TrainingRecord::getTrainingDate, dto.getStartDate());
        if (dto.getEndDate() != null) w.le(TrainingRecord::getTrainingDate, dto.getEndDate());
        if (StringUtils.hasText(dto.getStudentName())) {
            Result<List<StudentDTO>> students = studentFeignClient.selectList(dto.getStudentName());
            if (students.getData() != null && !students.getData().isEmpty()) w.in(TrainingRecord::getStudentId, students.getData().stream().map(StudentDTO::getId).collect(Collectors.toList()));
            else w.eq(TrainingRecord::getStudentId, -1L);
        }
        w.orderByDesc(TrainingRecord::getCreateTime);
        Page<TrainingRecord> p = trainingRecordMapper.selectPage(new Page<>(dto.getPage(), dto.getSize()), w);
        return toVOPage(p);
    }

    private Page<TrainingRecordVO> toVOPage(Page<TrainingRecord> p) {
        Page<TrainingRecordVO> voPage = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        voPage.setRecords(p.getRecords().stream().map(this::toVO).collect(Collectors.toList()));
        return voPage;
    }

    private TrainingRecordVO toVO(TrainingRecord r) {
        TrainingRecordVO vo = new TrainingRecordVO();
        vo.setId(r.getId()); vo.setReservationId(r.getReservationId());
        vo.setStudentId(r.getStudentId()); vo.setCoachId(r.getCoachId());
        vo.setVehicleId(r.getVehicleId()); vo.setTrainingDate(r.getTrainingDate());
        vo.setTimeSlot(r.getTimeSlot()); vo.setTrainingContent(r.getTrainingContent());
        vo.setResult(r.getResult()); vo.setCoachComment(r.getCoachComment());
        vo.setCreateTime(r.getCreateTime());
        Result<StudentDTO> s = studentFeignClient.getById(r.getStudentId());
        if (s != null) vo.setStudentName(s.getData().getName());
        CoachDTO c = coachFeignClient.getCoachById(r.getCoachId());
        if (c != null) vo.setCoachName(c.getName());
        if (r.getVehicleId() != null) {
            VehicleDTO v = vehicleFeignClient.getById(r.getVehicleId());
            if (v != null) vo.setPlateNumber(v.getPlateNumber());
        }
        return vo;
    }
}
