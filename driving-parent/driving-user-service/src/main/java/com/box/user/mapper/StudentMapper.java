package com.box.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.box.user.entity.Student;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StudentMapper extends BaseMapper<Student> {
}
