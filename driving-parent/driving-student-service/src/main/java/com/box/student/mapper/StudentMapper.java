package com.box.student.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import com.box.student.entity.Student;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface StudentMapper extends BaseMapper<Student> {

    @Update("UPDATE student SET completed_training_count = completed_training_count + 1 WHERE id = #{id}")
    void incrementCompleteCount(Long id);
}
