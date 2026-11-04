package com.box.reservation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.box.api.schedule.ScheduleDTO;
import com.box.reservation.entity.Reservation;
import jakarta.validation.constraints.NotNull;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 预约 Mapper
 */
@Mapper
public interface ReservationMapper extends BaseMapper<Reservation> {

    @Update("update coach_schedule set current_students = current_students + 1" +
            " where id = #{scheduleId} and status = 'OPEN' and current_students < max_students")
    int increaseCurrentStudents(@Param("scheduleId") Long scheduleId);

    @Update("update coach_schedule set current_students = current_students - 1" +
            " where id = #{scheduleId} and current_students > 0")
    int decreaseCurrentStudents(@Param("scheduleId") Long scheduleId);

    @Select("select * from coach_schedule where id = #{scheduleId} and status = 'OPEN'")
    ScheduleDTO selectCSById(@NotNull(message = "排班ID不能为空") @Param("scheduleId") Long scheduleId);
}
