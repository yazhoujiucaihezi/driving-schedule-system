package com.box.coach.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.box.coach.entity.Coach;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;

@Mapper
public interface CoachMapper extends BaseMapper<Coach> {

    @Select("select ifnull(sum(max_students-current_students),0) " +
            "from coach_schedule " +
            " where coach_id = #{id} "+
            "and schedule_date = #{date} " +
            "and time_slot = #{timeSlot}")
    Long getCount(Long id, LocalDate date, String timeSlot);
}
