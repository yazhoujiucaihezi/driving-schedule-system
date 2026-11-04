package com.box.schedule.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.box.api.coach.CoachDTO;
import com.box.api.coach.CoachFeignClient;
import com.box.api.vehicle.VehicleDTO;
import com.box.api.vehicle.VehicleFeignClient;
import com.box.common.exception.BusinessException;
import com.box.common.result.Result;
import com.box.schedule.dto.ScheduleBatchCreateDTO;
import com.box.schedule.dto.ScheduleCreateDTO;
import com.box.schedule.dto.ScheduleQueryDTO;
import com.box.schedule.dto.ScheduleUpdateDTO;
import com.box.schedule.entity.CoachSchedule;
import com.box.schedule.mapper.CoachScheduleMapper;
import com.box.schedule.service.CoachScheduleService;
import com.box.schedule.vo.ScheduleAvailableVO;
import com.box.schedule.vo.ScheduleVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 排班业务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CoachScheduleServiceImpl implements CoachScheduleService {

    private final CoachScheduleMapper scheduleMapper;
    private final CoachFeignClient coachFeignClient;
    private final VehicleFeignClient vehicleFeignClient;

    @Override
    public Page<ScheduleVO> queryPage(ScheduleQueryDTO dto) {
        LambdaQueryWrapper<CoachSchedule> w = new LambdaQueryWrapper<>();
        if (dto.getCoachId() != null) w.eq(CoachSchedule::getCoachId, dto.getCoachId());
        if (dto.getVehicleId() != null) w.eq(CoachSchedule::getVehicleId, dto.getVehicleId());
        if (dto.getScheduleDate() != null) w.eq(CoachSchedule::getScheduleDate, dto.getScheduleDate());
        if (dto.getStartDate() != null) w.ge(CoachSchedule::getScheduleDate, dto.getStartDate());
        if (dto.getEndDate() != null) w.le(CoachSchedule::getScheduleDate, dto.getEndDate());
        if (StringUtils.hasText(dto.getTimeSlot())) w.eq(CoachSchedule::getTimeSlot, dto.getTimeSlot());
        if (StringUtils.hasText(dto.getStatus())) w.eq(CoachSchedule::getStatus, dto.getStatus());
        w.orderByDesc(CoachSchedule::getScheduleDate).orderByAsc(CoachSchedule::getTimeSlot);

        Page<CoachSchedule> page = scheduleMapper.selectPage(new Page<>(dto.getPage(), dto.getSize()), w);
        Page<ScheduleVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(this::toVO).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public ScheduleVO getById(Long id) {
        return toVO(findSchedule(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScheduleVO create(ScheduleCreateDTO dto) {
        // 1. 校验教练
        Result<CoachDTO> coach = coachFeignClient.getCoachById(dto.getCoachId());
        if (coach == null) throw new BusinessException("教练不存在");
        if (!"NORMAL".equals(coach.getData().getStatus())) throw new BusinessException("教练已停用，不能创建排班");

        // 2. 校验时间段合法性
        if (!List.of("MORNING", "AFTERNOON").contains(dto.getTimeSlot())) {
            throw new BusinessException("无效的时间段，仅支持 MORNING / AFTERNOON");
        }

        // 3. 先校验教练重复，错误提示更符合业务语义
        checkDuplicate(dto.getCoachId(), dto.getScheduleDate(), dto.getTimeSlot());

        // 4. 再校验车辆冲突
        if (dto.getVehicleId() != null) {
            validateVehicle(dto.getVehicleId());
            checkVehicleConflict(dto.getVehicleId(), dto.getScheduleDate(), dto.getTimeSlot(), null);
        }

        CoachSchedule schedule = new CoachSchedule();
        BeanUtils.copyProperties(dto, schedule);
        schedule.setCurrentStudents(0);
        schedule.setStatus("OPEN");
        scheduleMapper.insert(schedule);
        return toVO(schedule);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScheduleVO update(Long id, ScheduleUpdateDTO dto) {
        CoachSchedule s = findSchedule(id);

        if (dto.getVehicleId() != null) {
            validateVehicle(dto.getVehicleId());
            checkVehicleConflict(dto.getVehicleId(), s.getScheduleDate(), s.getTimeSlot(), id);
            s.setVehicleId(dto.getVehicleId());
        }

        // maxStudents 不能小于 currentStudents
        if (dto.getMaxStudents() != null) {
            if (dto.getMaxStudents() < s.getCurrentStudents()) {
                throw new BusinessException("当前已有 " + s.getCurrentStudents() + " 人预约，不能将容量调小到 " + dto.getMaxStudents());
            }
            s.setMaxStudents(dto.getMaxStudents());
        }

        if (dto.getRemark() != null) {
            s.setRemark(dto.getRemark());
        }

        scheduleMapper.updateById(s);
        return toVO(s);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, String status) {
        if (!List.of("OPEN", "CLOSED", "CANCELLED").contains(status)) {
            throw new BusinessException("无效的排班状态：" + status);
        }
        CoachSchedule s = findSchedule(id);
        if ("CANCELLED".equals(status) && s.getCurrentStudents() > 0) {
            throw new BusinessException("当前已有 " + s.getCurrentStudents() + " 人预约，不能取消排班。请先关闭排班或处理已有预约");
        }
        s.setStatus(status);
        scheduleMapper.updateById(s);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Integer> batchCreate(ScheduleBatchCreateDTO dto) {
        Integer created = 0;
        LocalDate startDate = dto.getStartDate();
        long between = ChronoUnit.DAYS.between(startDate, dto.getEndDate()) + 1;
            List<CoachDTO> all = coachFeignClient.getByIds(dto.getCoachIds()).getData();
        if (all == null || all.isEmpty()) {
            return Map.of("created", 0, "skipped", 0);
        }

        Integer total = Math.toIntExact(all.size() * between * dto.getTimeSlots().size());
        List<CoachDTO> normal = all.stream().filter(coachDTO -> "NORMAL".equals(coachDTO.getStatus())).toList();
        List<Long> normalIds = new ArrayList<>();
        for (CoachDTO coachDTO : normal) {
            normalIds.add(coachDTO.getId());
        }
        Set<String> exists = new HashSet<>();
        List<CoachSchedule> existList = scheduleMapper.selectList(
                new LambdaQueryWrapper<CoachSchedule>()
                        .in(CoachSchedule::getCoachId, normalIds)
                        .between(CoachSchedule::getScheduleDate, startDate, dto.getEndDate())
        );
        for (CoachSchedule schedule : existList) {
            exists.add(scheduleKey(schedule.getCoachId(), schedule.getScheduleDate(), schedule.getTimeSlot()));
        }
        // 自动绑车：一次拿全部车，本地按状态筛出正常的，再按教练分组
        Map<Long, Long> vehicleMap = new HashMap<>();
        if (Boolean.TRUE.equals(dto.getAutoBindVehicle())) {
            Result<List<VehicleDTO>> vr = vehicleFeignClient.getAll();
            List<VehicleDTO> cars = normalVehicle(vr == null ? null : vr.getData());
            for (VehicleDTO car : cars) {
                vehicleMap.putIfAbsent(car.getCoachId(), car.getId());
            }
        }

        List<CoachSchedule> toInsert = new ArrayList<>();
        while (!startDate.isAfter(dto.getEndDate())) {
            for (CoachDTO coachDTO : normal) {
                for (String timeSlot : dto.getTimeSlots()) {
                    String key = scheduleKey(coachDTO.getId(), startDate, timeSlot);
                    if (exists.contains(key)) {
                        continue;
                    }
                    CoachSchedule coachSchedule = new CoachSchedule();
                    coachSchedule.setCoachId(coachDTO.getId());
                    coachSchedule.setVehicleId(vehicleMap.get(coachDTO.getId()));
                    coachSchedule.setScheduleDate(startDate);
                    coachSchedule.setTimeSlot(timeSlot);
                    coachSchedule.setMaxStudents(dto.getMaxStudents());
                    coachSchedule.setCurrentStudents(0);
                    coachSchedule.setStatus("OPEN");
                    coachSchedule.setRemark(dto.getRemark());
                    toInsert.add(coachSchedule);
                }
                if (!toInsert.isEmpty()){
                    for (CoachSchedule schedule : toInsert) {
                        scheduleMapper.insert(schedule);
                        created++;
                    }
                }
                toInsert.clear();
            }
            startDate = startDate.plusDays(1);
        }
        Integer skipped = total - created;
        Map<String,Integer> map =new HashMap<>();
        map.put("created",created);
        map.put("skipped",skipped);
        return map;

    }

    /** 从全部车辆里筛出状态正常的 */
    private List<VehicleDTO> normalVehicle(List<VehicleDTO> vehicles) {
        if (vehicles == null || vehicles.isEmpty()) {
            return new ArrayList<>();
        }
        List<VehicleDTO> normal = new ArrayList<>();
        for (VehicleDTO v : vehicles) {
            if ("NORMAL".equals(v.getStatus())) {
                normal.add(v);
            }
        }
        return normal;
    }

    /** 排班唯一键：教练ID_日期_时段，用于批量判重 */
    private String scheduleKey(Long coachId, LocalDate date, String timeSlot) {
        return coachId + "_" + date + "_" + timeSlot;
    }

    @Override
    public List<ScheduleAvailableVO> availableList(LocalDate date, String timeSlot) {        return scheduleMapper.selectList(new LambdaQueryWrapper<CoachSchedule>()
                        .eq(CoachSchedule::getScheduleDate, date)
                        .eq(CoachSchedule::getTimeSlot, timeSlot)
                        .eq(CoachSchedule::getStatus, "OPEN")
                        .apply("current_students < max_students"))
                .stream()
                .map(s -> {
                    ScheduleAvailableVO vo = new ScheduleAvailableVO();
                    BeanUtils.copyProperties(s, vo);
                    vo.setScheduleId(s.getId());
                    vo.setRemainCount(s.getMaxStudents() - s.getCurrentStudents());
                    // 填充教练名和车牌
                    Result<CoachDTO> c = coachFeignClient.getCoachById(s.getCoachId());
                    vo.setCoachName(c != null && c.getData() != null ? c.getData().getName() : "未知");
                    if (s.getVehicleId() != null) {
                        Result<VehicleDTO> vr = vehicleFeignClient.getById(s.getVehicleId());
                        VehicleDTO v = vr == null ? null : vr.getData();
                        vo.setPlateNumber(v != null ? v.getPlateNumber() : null);
                    }
                    return vo;
                })
                .collect(Collectors.toList());
    }

    @Override
    public Long countByDate(LocalDate date) {
        return scheduleMapper.selectCount(new LambdaQueryWrapper<CoachSchedule>()
                .eq(CoachSchedule::getScheduleDate, date));
    }

    @Override
    public CoachSchedule getByCoach(Long coachId, LocalDate date, String timeSlot) {
        return scheduleMapper.selectOne(new LambdaQueryWrapper<CoachSchedule>()
                .eq(CoachSchedule::getCoachId, coachId)
                .eq(CoachSchedule::getScheduleDate, date)
                .eq(CoachSchedule::getTimeSlot, timeSlot));
    }

    // ==================== 内部方法 ====================

    private CoachSchedule findSchedule(Long id) {
        CoachSchedule s = scheduleMapper.selectById(id);
        if (s == null) throw new BusinessException("排班不存在");
        return s;
    }

    private void validateVehicle(Long vehicleId) {
        Result<VehicleDTO> vr = vehicleFeignClient.getById(vehicleId);
        if (vr == null || vr.getData() == null) throw new BusinessException("车辆不存在");
        VehicleDTO v = vr.getData();
        if (!"NORMAL".equals(v.getStatus())) throw new BusinessException("车辆当前不可用，不能绑定排班");
    }

    /** 检查教练是否在同时段已有排班 */
    private void checkDuplicate(Long coachId, LocalDate date, String slot) {
        LambdaQueryWrapper<CoachSchedule> w = new LambdaQueryWrapper<CoachSchedule>()
                .eq(CoachSchedule::getCoachId, coachId)
                .eq(CoachSchedule::getScheduleDate, date)
                .eq(CoachSchedule::getTimeSlot, slot);
        if (scheduleMapper.selectCount(w) > 0) {
            throw new BusinessException("该教练当前时间段已有排班");
        }
    }

    /** 检查车辆是否在同时段已被其他排班占用 */
    private void checkVehicleConflict(Long vehicleId, LocalDate date, String slot, Long excludeId) {
        LambdaQueryWrapper<CoachSchedule> w = new LambdaQueryWrapper<CoachSchedule>()
                .eq(CoachSchedule::getVehicleId, vehicleId)
                .eq(CoachSchedule::getScheduleDate, date)
                .eq(CoachSchedule::getTimeSlot, slot);
        if (excludeId != null) w.ne(CoachSchedule::getId, excludeId);
        if (scheduleMapper.selectCount(w) > 0) {
            throw new BusinessException("该车辆当前时间段已被占用");
        }
    }

    private ScheduleVO toVO(CoachSchedule s) {
        ScheduleVO vo = new ScheduleVO();
        BeanUtils.copyProperties(s, vo);
        vo.setRemainCount(s.getMaxStudents() - s.getCurrentStudents());
        Result<CoachDTO> c = coachFeignClient.getCoachById(s.getCoachId());
        vo.setCoachName(c != null && c.getData() != null ? c.getData().getName() : "未知");
        if (s.getVehicleId() != null) {
            Result<VehicleDTO> vr = vehicleFeignClient.getById(s.getVehicleId());
            VehicleDTO v = vr == null ? null : vr.getData();
            vo.setPlateNumber(v != null ? v.getPlateNumber() : null);
        }
        return vo;
    }
}
