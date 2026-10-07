package com.box.schedule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.box.schedule.entity.CoachSchedule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CoachScheduleMapper extends BaseMapper<CoachSchedule> {

    @Update("UPDATE coach_schedule SET current_students = current_students + 1 " +
            "WHERE id = #{id} AND current_students < max_students")
    int increaseCurrentStudents(@Param("id") Long id);

    @Update("UPDATE coach_schedule SET current_students = current_students - 1 " +
            "WHERE id = #{id} AND current_students > 0")
    int decreaseCurrentStudents(@Param("id") Long id);
}
