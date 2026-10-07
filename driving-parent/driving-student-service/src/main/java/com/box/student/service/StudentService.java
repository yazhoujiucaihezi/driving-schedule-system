package com.box.student.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.box.student.dto.StudentCreateDTO;
import com.box.student.dto.StudentQueryDTO;
import com.box.student.dto.StudentUpdateDTO;
import com.box.student.vo.StudentSimpleVO;
import com.box.student.vo.StudentVO;

import java.util.List;

public interface StudentService {
    Page<StudentVO> queryPage(StudentQueryDTO dto);
    StudentVO getById(Long id);
    StudentVO create(StudentCreateDTO dto);
    StudentVO update(Long id, StudentUpdateDTO dto);
    void updateStatus(Long id, String status);
    List<StudentSimpleVO> simpleList();
}
