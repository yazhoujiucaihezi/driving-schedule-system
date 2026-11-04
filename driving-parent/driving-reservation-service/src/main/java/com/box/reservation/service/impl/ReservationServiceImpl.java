package com.box.reservation.service.impl;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.box.api.coach.CoachDTO;
import com.box.api.coach.CoachFeignClient;
import com.box.api.schedule.ScheduleDTO;
import com.box.api.schedule.ScheduleFeignClient;
import com.box.api.student.StudentDTO;
import com.box.api.student.StudentFeignClient;
import com.box.api.training.TrainingRecordCreateDTO;
import com.box.api.training.TrainingRecordFeignClient;
import com.box.api.vehicle.VehicleDTO;
import com.box.api.vehicle.VehicleFeignClient;
import com.box.common.exception.BusinessException;
import com.box.common.result.Result;
import com.box.reservation.common.constant.RabbitMQConstants;
import com.box.reservation.common.constant.RedisKeyConstants;
import com.box.reservation.common.constant.ReservationStatusConstants;
import com.box.reservation.dto.ReservationCancelDTO;
import com.box.reservation.dto.ReservationCreateDTO;
import com.box.reservation.dto.ReservationQueryDTO;
import com.box.reservation.dto.TrainingCompleteDTO;
import com.box.reservation.entity.Reservation;
import com.box.reservation.mapper.ReservationMapper;
import com.box.reservation.service.ReservationService;
import com.box.reservation.vo.ReservationOptionVO;
import com.box.reservation.vo.ReservationVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 预约业务实现
 * <p>
 * M6 阶段：加入 Redisson 分布式锁（锁粒度 scheduleId），确保并发预约不会超额。
 * 锁内重新校验所有条件，并以数据库条件 UPDATE 作为最终兜底。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationServiceImpl implements ReservationService {

    private final ReservationMapper reservationMapper;
    private final StudentFeignClient studentFeignClient;
    private final VehicleFeignClient vehicleFeignClient;
    private final CoachFeignClient coachFeignClient;
    private final ScheduleFeignClient scheduleFeignClient;
    private final TrainingRecordFeignClient trainingRecordFeignClient;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedissonClient redissonClient;
    private final RabbitTemplate rabbitTemplate;
    private final TransactionTemplate transactionTemplate;

    @Override
    public ReservationOptionVO getOptions(Long studentId, LocalDate date, String timeSlot) {
        ReservationOptionVO vo = new ReservationOptionVO();
        vo.setAdjustOptions(new ArrayList<>());   // ← 加这句
        Result<StudentDTO> result = studentFeignClient.getById(studentId);
        //判断如果是降级（code != 200），直接抛出降级的提示
        if (result == null || result.getCode() != 200) {
            // 直接把降级类里写的 "学员服务暂不可用" 抛出去
            throw new BusinessException(result != null ? result.getMessage() : "学员服务不可用");
        }

        // 正常获取数据
        StudentDTO student = result.getData();
        if (student == null) throw new BusinessException("学员信息不存在");
        ReservationOptionVO.MainCoachOption opt = new ReservationOptionVO.MainCoachOption();

        // 查询主教练排班
        ScheduleDTO mainSchedule = scheduleFeignClient.getOne(student.getMainCoachId(),date,timeSlot).getData();
        boolean mainFull = false;

        if (mainSchedule == null) {
            opt.setAvailable(false);
            opt.setMessage("当前日期和时间段暂无排班，请选择其他时间");
            mainFull = true;
        } else if (!"OPEN".equals(mainSchedule.getStatus())) {
            opt.setAvailable(false);
            opt.setMessage("该时间段排班已关闭");
            mainFull = true;
        } else if (mainSchedule.getCurrentStudents() >= mainSchedule.getMaxStudents()) {
            fillMinimalOption(opt, mainSchedule);
            opt.setAvailable(false);
            opt.setMessage("主教练当前时间段已满员，可选择调剂");
            mainFull = true; // M7: 主教练满员 → 推荐调剂
        } else {
            fillFullOption(opt, mainSchedule);
            opt.setAvailable(true);
            opt.setMessage("可预约");
        }

        vo.setMainCoachOption(opt);

        if (mainFull) {
            Result<List<ScheduleDTO>> scheduleResult = scheduleFeignClient.listAdjustable(date, timeSlot,student.getMainCoachId());

            Result<List<CoachDTO>> coachResult = coachFeignClient.selectAllByStatus();
            if (scheduleResult != null && scheduleResult.getData() != null
                    && coachResult != null && coachResult.getData() != null) {

                List<CoachDTO> dtoList = coachResult.getData();

                Map<Long, CoachDTO> coachMap = dtoList.stream()
                        .collect(Collectors.toMap(CoachDTO::getId, c -> c));
                List<ReservationOptionVO.AdjustOption> adjustOptions = new ArrayList<>();
                for (ScheduleDTO s : scheduleResult.getData()) {
                    CoachDTO coach = coachMap.get(s.getCoachId());
                    if (coach == null || !"NORMAL".equals(coach.getStatus())) continue;

                    // 车辆状态异常时不展示车牌，但候选本身仍然保留
                    String plateNumber = null;
                    if (s.getVehicleId() != null) {
                        Result<VehicleDTO> vr = vehicleFeignClient.getById(s.getVehicleId());
                        VehicleDTO v = vr == null ? null : vr.getData();
                        if (v != null && "NORMAL".equals(v.getStatus())) {
                            plateNumber = v.getPlateNumber();
                        }
                    }

                    ReservationOptionVO.AdjustOption option = getOption(s, coach, plateNumber);

                    adjustOptions.add(option);
                }
                List<ReservationOptionVO.AdjustOption> list = adjustOptions.stream()
                        .sorted(Comparator.comparing(ReservationOptionVO.AdjustOption::getRemainCount).reversed())
                        .limit(5)
                        .collect(Collectors.toList());
                vo.setAdjustOptions(list);
            }
        }
        return vo;
    }


    // ==================== 创建预约（M6：加入分布式锁 + 限流） ====================

    @Override
    @SentinelResource(value = "reservation:create",blockHandler = "createBlockHandler")
    public ReservationVO create(ReservationCreateDTO dto, Long studentId) {
        //防重复提交
        Result<StudentDTO> result = studentFeignClient.getById(studentId);
        if (result == null || result.getData() == null) {
            throw new BusinessException("信息错误，请重试");
        }
        Long mainCoachId = result.getData().getMainCoachId();
        Reservation reservation = new Reservation();
        Boolean b = stringRedisTemplate.opsForValue().setIfAbsent(RedisKeyConstants.LIMIT_RESERVATION + studentId, "1", 3, TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(b)){
            throw new BusinessException("提交频繁，请重试");
        }

        //获取锁
        RLock lock = redissonClient.getLock(RedisKeyConstants.LOCK_SCHEDULE + dto.getScheduleId());
        try {
            if (!lock.tryLock(2, 10, TimeUnit.SECONDS)){
                throw new BusinessException("预约人数过多,请稍后再试！");
            }
            transactionTemplate.executeWithoutResult(status->{
                ScheduleDTO scheduleDTO = reservationMapper.selectCSById(dto.getScheduleId());
                if (scheduleDTO == null) {
                    throw new BusinessException("排班已满");
                }

                LambdaQueryWrapper<Reservation> wrapper = new LambdaQueryWrapper<Reservation>()
                        .eq(Reservation::getStudentId, studentId)
                        .eq(Reservation::getScheduleId, dto.getScheduleId())
                        .eq(Reservation::getStatus, ReservationStatusConstants.SUCCESS);
                Long dupCount = reservationMapper.selectCount(wrapper);
                if (dupCount != null && dupCount > 0)
                {
                    throw new BusinessException("预约已存在,请取消或返回上一次预约");
                }

                boolean adjusted = dto.isAdjusted();
                boolean sameCoach = mainCoachId.equals(scheduleDTO.getCoachId());

                if (!sameCoach && !adjusted) {
                    throw new BusinessException("该排班不属于你的主教练，如需调剂请走调剂预约");
                }
                if (sameCoach && adjusted) {
                    throw new BusinessException("主教练的排班无需调剂");
                }
                if (adjusted && !StringUtils.hasText(dto.getAdjustReason())) {
                    throw new BusinessException("调剂预约必须填写原因");
                }

                reservation.setIsAdjusted(adjusted ? 1 : 0);
                reservation.setAdjustReason(adjusted ? dto.getAdjustReason() : null);

                // 校验全部通过后才占用名额；影响行数为 0 说明被并发抢走或已满
                int affected = reservationMapper.increaseCurrentStudents(dto.getScheduleId());
                if (affected == 0) {
                    throw new BusinessException("名额已满，请选择其他时段");
                }

                reservation.setScheduleId(dto.getScheduleId());
                reservation.setStudentId(studentId);
                reservation.setReservationDate(scheduleDTO.getScheduleDate());
                reservation.setStatus(ReservationStatusConstants.SUCCESS);
                reservation.setTimeSlot(scheduleDTO.getTimeSlot());
                reservation.setActualCoachId(scheduleDTO.getCoachId());
                reservation.setMainCoachId(mainCoachId);
                reservation.setVehicleId(scheduleDTO.getVehicleId());

                reservationMapper.insert(reservation);

                rabbitTemplate.convertAndSend(RabbitMQConstants.EXCHANGE_RESERVATION_SUCCESS,RabbitMQConstants.ROUTING_KEY_RESERVATION_SUCCESS,reservation.getId());
                rabbitTemplate.convertAndSend(RabbitMQConstants.QUEUE_RESERVATION_DELAY, Map.of("reservationId", reservation.getId()));
            });
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            if (lock.isHeldByCurrentThread())lock.unlock();
        }
        return toVO(reservation);
    }

    // ==================== 取消预约（M6：加锁保护 current_students 减操作） ====================

    @Override
    public void cancel(Long reservationId, ReservationCancelDTO dto, Long operatorId, String operatorRole) {
        Reservation r = reservationMapper.selectById(reservationId);
        if (r == null) {
            throw new BusinessException("预约不存在");
        }

        boolean isAdmin = "ADMIN".equals(operatorRole);
        boolean isOwner = operatorId != null && operatorId.equals(r.getStudentId());

        if (!isAdmin && !isOwner) {
            throw new BusinessException("无权取消该预约");
        }

        if (!r.getStatus().equals(ReservationStatusConstants.SUCCESS)) {
            throw new BusinessException("订单已完成或已取消");
        }
        RLock lock = redissonClient.getLock(RedisKeyConstants.LOCK_SCHEDULE + r.getScheduleId());
        try {
            if (!lock.tryLock(2, 10, TimeUnit.SECONDS)) {
                throw new BusinessException("系统繁忙，请稍后再试！");
            }
            transactionTemplate.executeWithoutResult(status -> {
                Reservation reservation = reservationMapper.selectById(reservationId);
                if (reservation == null) {
                    throw new BusinessException("预约不存在");
                }
                if (!ReservationStatusConstants.SUCCESS.equals(reservation.getStatus())) {
                    throw new BusinessException("订单已完成或已取消");
                }

                reservation.setStatus(ReservationStatusConstants.CANCELLED);
                reservation.setCancelTime(LocalDateTime.now());
                reservation.setCancelReason(dto.getReason());

                int i = reservationMapper.decreaseCurrentStudents(reservation.getScheduleId());
                if (i <= 0) {
                    throw new BusinessException("名额数据异常，取消失败，请联系管理员");
                }
                reservationMapper.updateById(reservation);
            });

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("取消处理超时，请稍后重试");
        } finally {
            if (lock.isHeldByCurrentThread()) lock.unlock();
        }


    }

    // ==================== 查询方法（不变） ====================

    @Override
    public Page<ReservationVO> myReservations(Long studentId, Integer page, Integer size) {
        Page<Reservation> p = reservationMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Reservation>()
                        .eq(Reservation::getStudentId, studentId)
                        .orderByDesc(Reservation::getCreateTime));
        Page<ReservationVO> voPage = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        voPage.setRecords(p.getRecords().stream().map(this::toVO).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public Page<ReservationVO> coachToday(Long coachId, LocalDate date, Integer page, Integer size) {
        if (date == null) date = LocalDate.now();
        Page<Reservation> p = reservationMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Reservation>()
                        .eq(Reservation::getActualCoachId, coachId)
                        .eq(Reservation::getReservationDate, date)
                        .orderByAsc(Reservation::getTimeSlot));
        Page<ReservationVO> voPage = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        voPage.setRecords(p.getRecords().stream().map(this::toVO).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public Page<ReservationVO> adminQuery(ReservationQueryDTO dto) {
        LambdaQueryWrapper<Reservation> w = new LambdaQueryWrapper<>();
        if (dto.getCoachId() != null) w.eq(Reservation::getActualCoachId, dto.getCoachId());
        if (dto.getReservationDate() != null) w.eq(Reservation::getReservationDate, dto.getReservationDate());
        if (StringUtils.hasText(dto.getTimeSlot())) w.eq(Reservation::getTimeSlot, dto.getTimeSlot());
        if (StringUtils.hasText(dto.getStatus())) w.eq(Reservation::getStatus, dto.getStatus());
        if (StringUtils.hasText(dto.getStudentName())) {
            var students = studentFeignClient.selectList(dto.getStudentName()).getData();
            if (!students.isEmpty()) {
                w.in(Reservation::getStudentId, students.stream().map(StudentDTO::getId).collect(Collectors.toList()));
            } else {
                w.eq(Reservation::getStudentId, -1L);
            }
        }
        w.orderByDesc(Reservation::getCreateTime);
        Page<Reservation> p = reservationMapper.selectPage(new Page<>(dto.getPage(), dto.getSize()), w);
        Page<ReservationVO> voPage = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        voPage.setRecords(p.getRecords().stream().map(this::toVO).collect(Collectors.toList()));
        return voPage;
    }

    // ==================== 内部方法 ====================


    private void fillFullOption(ReservationOptionVO.MainCoachOption opt, ScheduleDTO s) {
        opt.setScheduleId(s.getId());
        opt.setCoachId(s.getCoachId());
        opt.setCurrentStudents(s.getCurrentStudents());
        opt.setMaxStudents(s.getMaxStudents());
        opt.setRemainCount(s.getMaxStudents() - s.getCurrentStudents());
        opt.setScheduleDate(s.getScheduleDate().toString());
        opt.setTimeSlot(s.getTimeSlot());
        fillCoachAndVehicle(opt, s);
    }

    private void fillCoachAndVehicle(ReservationOptionVO.MainCoachOption opt, ScheduleDTO s) {
        Result<CoachDTO> c = coachFeignClient.getCoachById(s.getCoachId());
        opt.setCoachName(c != null && c.getData() != null ? c.getData().getName() : "未知");
        if (s.getVehicleId() != null) {
            Result<VehicleDTO> vr = vehicleFeignClient.getById(s.getVehicleId());
            VehicleDTO v = vr == null ? null : vr.getData();
            opt.setVehicleId(s.getVehicleId());
            opt.setPlateNumber(v != null ? v.getPlateNumber() : null);
        }
    }

    // ==================== M8：完成练车 + 标记缺席 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void complete(Long reservationId, TrainingCompleteDTO dto, Long coachId) {
        Reservation r = reservationMapper.selectById(reservationId);
        if (r == null) throw new BusinessException("预约不存在");
        if (!"SUCCESS".equals(r.getStatus())) throw new BusinessException("该预约已" + r.getStatus() + "，不能完成练车");
        if (!coachId.equals(r.getActualCoachId())) throw new BusinessException("只有实际带练教练可以完成该预约");

        // 更新预约状态
        r.setStatus("COMPLETED");
        reservationMapper.updateById(r);

        // 生成练车记录（durationMinutes 附加到备注中）
        TrainingRecordCreateDTO trDto = getTrainingRecordCreateDTO(dto, r);
        trainingRecordFeignClient.create(trDto);

        // 学员已完成练车次数 +1
        StudentDTO student = studentFeignClient.getById(r.getStudentId()).getData();
        if (student != null) {
            studentFeignClient.incrementCompleteCount(student.getId());
        }
    }

    @NonNull
    private static TrainingRecordCreateDTO getTrainingRecordCreateDTO(TrainingCompleteDTO dto, Reservation r) {
        String remark = dto.getCoachRemark();
        if (dto.getDurationMinutes() != null && dto.getDurationMinutes() > 0) {
            remark = (remark != null ? remark + " " : "") + "(练车时长: " + dto.getDurationMinutes() + " 分钟)";
        }

        TrainingRecordCreateDTO trDto = new TrainingRecordCreateDTO();
        trDto.setReservationId(r.getId());
        trDto.setStudentId(r.getStudentId());
        trDto.setCoachId(r.getActualCoachId());
        trDto.setVehicleId(r.getVehicleId());
        trDto.setTrainingDate(r.getReservationDate());
        trDto.setTimeSlot(r.getTimeSlot());
        trDto.setTrainingContent(dto.getTrainingContent());
        trDto.setCoachComment(remark);
        trDto.setResult("COMPLETED");
        return trDto;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markAbsent(Long reservationId, String reason, Long coachId) {
        Reservation r = reservationMapper.selectById(reservationId);
        if (r == null) throw new BusinessException("预约不存在");
        if (!"SUCCESS".equals(r.getStatus())) throw new BusinessException("该预约已" + r.getStatus() + "，不能标记缺席");
        if (!coachId.equals(r.getActualCoachId())) throw new BusinessException("只有实际带练教练可以标记该预约");

        r.setStatus("ABSENT");
        r.setCancelReason(reason);
        reservationMapper.updateById(r);
        // 缺席不生成 training_record，不增加 completed_training_count
    }

    @Override
    public Long countByDate(String date) {
        return reservationMapper.selectCount(new LambdaQueryWrapper<Reservation>().eq(Reservation::getReservationDate, date));
    }

    @Override
    public Long countByDateAndStatus(LocalDate date, String status) {
        return reservationMapper.selectCount(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getReservationDate, date)
                .eq(Reservation::getStatus, status));
    }

    @Override
    public Long countByStudentIdAndStatus(Long studentId, String status) {
        return reservationMapper.selectCount(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getStudentId, studentId)
                .eq(Reservation::getStatus, status));
    }

    @Override
    public Long countByStudentIdAndIsAdjusted(Long studentId, boolean isAdjusted) {
        return reservationMapper.selectCount(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getStudentId, studentId)
                .eq(Reservation::getIsAdjusted, isAdjusted));
    }

    // ==================== 内部方法 ====================

    private void fillMinimalOption(ReservationOptionVO.MainCoachOption opt, ScheduleDTO s) {
        opt.setScheduleId(s.getId());
        opt.setCoachId(s.getCoachId());
        opt.setCurrentStudents(s.getCurrentStudents());
        opt.setMaxStudents(s.getMaxStudents());
        opt.setRemainCount(0);
        fillCoachAndVehicle(opt, s);
    }

    private ReservationVO toVO(Reservation r) {
        ReservationVO vo = new ReservationVO();
        vo.setId(r.getId());
        vo.setStudentId(r.getStudentId());
        vo.setMainCoachId(r.getMainCoachId());
        vo.setActualCoachId(r.getActualCoachId());
        vo.setVehicleId(r.getVehicleId());
        vo.setScheduleId(r.getScheduleId());
        vo.setReservationDate(r.getReservationDate());
        vo.setTimeSlot(r.getTimeSlot());
        vo.setStatus(r.getStatus());
        vo.setIsAdjusted(r.getIsAdjusted());
        vo.setAdjustReason(r.getAdjustReason());
        vo.setCancelReason(r.getCancelReason());
        vo.setCreateTime(r.getCreateTime());
        vo.setCancelTime(r.getCancelTime());

        StudentDTO st = studentFeignClient.getById(r.getStudentId()).getData();
        if (st != null) { vo.setStudentName(st.getName()); vo.setStudentPhone(st.getPhone()); }
        if (r.getVehicleId() != null) {
            Result<VehicleDTO> vr = vehicleFeignClient.getById(r.getVehicleId());
            VehicleDTO v = vr == null ? null : vr.getData();
            if (v != null) vo.setPlateNumber(v.getPlateNumber());
        }
        return vo;
    }


    @NonNull
    private static ReservationOptionVO.AdjustOption getOption(ScheduleDTO s, CoachDTO coach, String plateNumber) {
        ReservationOptionVO.AdjustOption option = new ReservationOptionVO.AdjustOption();
        option.setScheduleId(s.getId());
        option.setCoachId(s.getCoachId());
        option.setCoachName(coach.getName());
        option.setVehicleId(s.getVehicleId());
        option.setPlateNumber(plateNumber);
        option.setMaxStudents(s.getMaxStudents());
        option.setCurrentStudents(s.getCurrentStudents());
        option.setRemainCount(s.getMaxStudents() - s.getCurrentStudents());
        return option;
    }


}
