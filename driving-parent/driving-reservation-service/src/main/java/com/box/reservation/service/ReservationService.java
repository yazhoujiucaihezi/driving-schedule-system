package com.box.reservation.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.box.common.result.Result;
import com.box.reservation.dto.ReservationCancelDTO;
import com.box.reservation.dto.ReservationCreateDTO;
import com.box.reservation.dto.ReservationQueryDTO;
import com.box.reservation.dto.TrainingCompleteDTO;
import com.box.reservation.vo.ReservationOptionVO;
import com.box.reservation.vo.ReservationVO;

import java.time.LocalDate;

public interface ReservationService {
    /** 学员查询可预约方案 */
    ReservationOptionVO getOptions(Long studentId, LocalDate date, String timeSlot);
    /** 创建预约（仅学员） */
    ReservationVO create(ReservationCreateDTO dto, Long studentId);
    /** 取消预约 */
    void cancel(Long reservationId, ReservationCancelDTO dto, Long operatorId, String operatorRole);
    /** 查询我的预约 */
    Page<ReservationVO> myReservations(Long studentId, Integer page, Integer size);
    /** 教练查询今日预约名单 */
    Page<ReservationVO> coachToday(Long coachId, LocalDate date, Integer page, Integer size);
    /** 管理员分页查询全部预约 */
    Page<ReservationVO> adminQuery(ReservationQueryDTO dto);
    /** M8：教练完成练车 */
    void complete(Long reservationId, TrainingCompleteDTO dto, Long coachId);
    /** M8：教练标记缺席 */
    void markAbsent(Long reservationId, String reason, Long coachId);

    Long countByDate(String date);

    Long countByDateAndStatus(LocalDate date, String status);

    Long countByStudentIdAndStatus(Long studentId, String status);

    Long countByStudentIdAndIsAdjusted(Long studentId, boolean isAdjusted);
}
