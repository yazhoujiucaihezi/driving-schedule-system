package com.box.reservation.service.impl;

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
import com.box.reservation.common.constant.RedisKeyConstants;
import com.box.reservation.config.MQConfig;
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
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
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
    @Override
    public ReservationOptionVO getOptions(Long studentId, LocalDate date, String timeSlot) {
        Result<StudentDTO> result = studentFeignClient.getById(studentId);
        //判断如果是降级（code != 200），直接抛出降级的提示
        if (result == null || result.getCode() != 200) {
            // 直接把降级类里写的 "学员服务暂不可用" 抛出去
            throw new BusinessException(result != null ? result.getMessage() : "学员服务不可用");
        }

        // 正常获取数据
        StudentDTO student = result.getData();
        if (student == null) throw new BusinessException("学员信息不存在");
        ReservationOptionVO vo = new ReservationOptionVO();
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

        // M7: 主教练不可用 → 查询可调剂教练
        if (mainFull) {
            var adjustList = scheduleFeignClient
                    .listAdjustable(date, timeSlot, student.getMainCoachId())
                    .getData()
                    .stream()
                    .filter(s -> !s.getCoachId().equals(student.getMainCoachId())) // 排除主教练
                    .filter(s -> {
                        Result<CoachDTO> c = coachFeignClient.getCoachById(s.getCoachId());
                        return c != null && "NORMAL".equals(c.getData().getStatus());
                    })
                    .sorted((a, b) -> Integer.compare(
                            b.getMaxStudents() - b.getCurrentStudents(),
                            a.getMaxStudents() - a.getCurrentStudents()))
                    .limit(5)
                    .map(s -> {
                        ReservationOptionVO.AdjustOption ao = new ReservationOptionVO.AdjustOption();
                        ao.setScheduleId(s.getId());
                        ao.setCoachId(s.getCoachId());
                        ao.setCurrentStudents(s.getCurrentStudents());
                        ao.setMaxStudents(s.getMaxStudents());
                        ao.setRemainCount(s.getMaxStudents() - s.getCurrentStudents());
                        ao.setRecommendReason("该教练当前剩余 " + ao.getRemainCount() + " 个名额");
                        Result<CoachDTO> c = coachFeignClient.getCoachById(s.getCoachId());
                        ao.setCoachName(c != null ? c.getData().getName() : "未知");
                        if (s.getVehicleId() != null) {
                            VehicleDTO v = vehicleFeignClient.getById(s.getVehicleId());
                            if (v != null && "NORMAL".equals(v.getStatus())) {
                                ao.setVehicleId(s.getVehicleId());
                                ao.setPlateNumber(v.getPlateNumber());
                            }
                        }
                        return ao;
                    })
                    .collect(Collectors.toList());
            vo.setAdjustOptions(adjustList);
        }

        return vo;
    }

    // ==================== 创建预约（M6：加入分布式锁 + 限流） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    @RabbitListener(queues = "reservation.success.queue")
    public ReservationVO create(ReservationCreateDTO dto, Long studentId) {
        Long scheduleId = dto.getScheduleId();

        // --- 1. 防重复点击：学员级限流（轻量） ---
        String limitKey = RedisKeyConstants.LIMIT_RESERVATION + studentId;
        if (stringRedisTemplate.hasKey(limitKey)) {
            throw new BusinessException("请勿频繁提交预约，请稍后再试");
        }
        stringRedisTemplate.opsForValue().set(limitKey, "1", Duration.ofSeconds(5));

        // --- 2. 加锁前基础校验 ---
        // 先获取完整的 Result 结果
        Result<StudentDTO> studentResult = studentFeignClient.getById(studentId);

        // 判断是否触发了降级或者调用失败
        if (studentResult == null || studentResult.getCode() != 200) {
            // 直接把降级类里写的 "学员服务暂不可用" 抛出去
            throw new BusinessException(studentResult != null ? studentResult.getMessage() : "学员服务不可用");
        }

        // 正常获取数据
        StudentDTO student = studentResult.getData();
        if (student == null) throw new BusinessException("学员信息不存在");

        // --- 3. Redisson 分布式锁 ---
        String lockKey = RedisKeyConstants.LOCK_SCHEDULE + scheduleId;
        RLock lock = redissonClient.getLock(lockKey);
        boolean locked = false;

        try {
            // 等待 3 秒获取锁，锁自动释放时间 10 秒
            locked = lock.tryLock(3, 10, TimeUnit.SECONDS);
            if (!locked) {
                throw new BusinessException("当前预约人数较多，请稍后重试");
            }

            // --- 4. 锁内重新查询排班（避免使用加锁前可能过期的数据） ---
            Result<ScheduleDTO> schedResult = scheduleFeignClient.getById(scheduleId);
            if (schedResult == null || schedResult.getCode() != 200) {
                throw new BusinessException(schedResult != null ? schedResult.getMessage() : "排班服务不可用");
            }
            ScheduleDTO schedule = schedResult.getData();
            if (schedule == null) throw new BusinessException("排班不存在");
            if (!"OPEN".equals(schedule.getStatus())) throw new BusinessException("该排班未开放预约");

            // 锁内校验容量
            if (schedule.getCurrentStudents() >= schedule.getMaxStudents()) {
                throw new BusinessException("该时间段已满员或不可预约");
            }

            // 锁内校验：主教练预约 → schedule.coach 必须是主教练；调剂预约 → 允许其他教练
            boolean adjusted = dto.isAdjusted();
            if (!adjusted && !schedule.getCoachId().equals(student.getMainCoachId())) {
                throw new BusinessException("非主教练排班，请使用调剂预约");
            }
            if (adjusted && schedule.getCoachId().equals(student.getMainCoachId())) {
                throw new BusinessException("主教练有空位，无需调剂预约");
            }

            // 锁内校验重复预约
            Long dup = reservationMapper.selectCount(new LambdaQueryWrapper<Reservation>()
                    .eq(Reservation::getStudentId, studentId)
                    .eq(Reservation::getReservationDate, schedule.getScheduleDate())
                    .eq(Reservation::getTimeSlot, schedule.getTimeSlot())
                    .eq(Reservation::getStatus, "SUCCESS"));
            if (dup > 0) throw new BusinessException("你已预约该时间段，不能重复预约");

            // --- 5. 数据库条件 UPDATE 作为最终兜底 ---
            int updated = reservationMapper.increaseCurrentStudents(scheduleId);
            if (updated == 0) throw new BusinessException("该时间段已满员或不可预约");

            // --- 6. 创建预约记录 ---
            Reservation r = new Reservation();
            r.setStudentId(studentId);
            r.setMainCoachId(student.getMainCoachId());
            r.setActualCoachId(schedule.getCoachId());
            r.setVehicleId(schedule.getVehicleId());
            r.setScheduleId(schedule.getId());
            r.setReservationDate(schedule.getScheduleDate());
            r.setTimeSlot(schedule.getTimeSlot());
            r.setStatus("SUCCESS");
            r.setIsAdjusted(adjusted ? 1 : 0);
            if (adjusted) {
                r.setAdjustReason(StringUtils.hasText(dto.getAdjustReason())
                        ? dto.getAdjustReason() : "主教练当前时间段已满，选择其他教练临时练车");
            }
            r.setCreateTime(LocalDateTime.now());
            reservationMapper.insert(r);
            Map<String, Object> msg = new HashMap<>();
            msg.put("reservationId", r.getId());
            msg.put("studentId", r.getStudentId());
            msg.put("coachId", r.getActualCoachId());
            msg.put("date", r.getReservationDate().toString());
            msg.put("timeSlot", r.getTimeSlot());
            log.info("预约成功！ ：{}", msg);
            rabbitTemplate.convertAndSend(MQConfig.EXCHANGE,MQConfig.ROUTING_KEY, msg);

            Map<String, Object> delayMsg = new HashMap<>();
            delayMsg.put("reservationId", r.getId());
            rabbitTemplate.convertAndSend("",MQConfig.DQUEUE, delayMsg);
            log.info("已发送延迟消息");

            return toVO(r);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("预约处理中断，请稍后重试");
        } finally {
            // 锁必须释放，且只释放当前线程持有的锁
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    // ==================== 取消预约（M6：加锁保护 current_students 减操作） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long reservationId, ReservationCancelDTO dto, Long operatorId, String operatorRole) {
        // 查询预约
        Reservation r = reservationMapper.selectById(reservationId);
        if (r == null) throw new BusinessException("预约不存在");
        if (!"SUCCESS".equals(r.getStatus())) throw new BusinessException("该预约不能取消");

        if ("STUDENT".equals(operatorRole) && !r.getStudentId().equals(operatorId)) {
            throw new BusinessException("只能取消自己的预约");
        }

        // 加排班锁，防止并发取消失败
        String lockKey = RedisKeyConstants.LOCK_SCHEDULE + r.getScheduleId();
        RLock lock = redissonClient.getLock(lockKey);
        boolean locked = false;

        try {
            locked = lock.tryLock(3, 10, TimeUnit.SECONDS);
            if (!locked) {
                throw new BusinessException("系统繁忙，请稍后重试");
            }

            // 锁内更新状态
            r.setStatus("CANCELLED");
            r.setCancelTime(LocalDateTime.now());
            if (StringUtils.hasText(dto.getReason())) {
                r.setCancelReason(dto.getReason());
            }
            reservationMapper.updateById(r);

            // 条件减少当前人数（不会减到负数）
            reservationMapper.decreaseCurrentStudents(r.getScheduleId());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("取消处理中断，请稍后重试");
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
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
        opt.setCoachName(c != null ? c.getData().getName() : "未知");
        if (s.getVehicleId() != null) {
            VehicleDTO v = vehicleFeignClient.getById(s.getVehicleId());
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
            VehicleDTO v = vehicleFeignClient.getById(r.getVehicleId());
            if (v != null) vo.setPlateNumber(v.getPlateNumber());
        }
        return vo;
    }


}
