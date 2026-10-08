package com.box.student.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.box.api.coach.CoachDTO;
import com.box.api.coach.CoachFeignClient;
import com.box.api.student.StudentDTO;
import com.box.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import com.box.student.dto.StudentCreateDTO;
import com.box.student.dto.StudentQueryDTO;
import com.box.student.dto.StudentUpdateDTO;
import com.box.student.entity.Student;
import com.box.student.mapper.StudentMapper;
import com.box.student.service.StudentService;
import com.box.student.vo.StudentSimpleVO;
import com.box.student.vo.StudentVO;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 学员业务实现
 * <p>
 * 新增学员时必须校验主教练是否存在且为正常状态，
 * 修改学员时如果变更了 completed_training_count 必须 <= required_training_count。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class StudentServiceImpl implements StudentService {

    private final StudentMapper studentMapper;
    private final CoachFeignClient coachFeignClient;

    @Override
    public Page<StudentVO> queryPage(StudentQueryDTO dto) {
        LambdaQueryWrapper<Student> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(dto.getKeyword())) {
            wrapper.and(w -> w
                    .like(Student::getName, dto.getKeyword())
                    .or()
                    .like(Student::getPhone, dto.getKeyword()));
        }
        if (dto.getCoachId() != null) {
            wrapper.eq(Student::getMainCoachId, dto.getCoachId());
        }
        if (StringUtils.hasText(dto.getStatus())) {
            wrapper.eq(Student::getStatus, dto.getStatus());
        }
        wrapper.orderByDesc(Student::getCreateTime);

        Page<Student> page = studentMapper.selectPage(
                new Page<>(dto.getPage(), dto.getSize()), wrapper);

        Page<StudentVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(this::toVO).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public StudentVO getById(Long id) {
        return toVO(findStudent(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StudentVO create(StudentCreateDTO dto) {
        // 校验主教练是否存在且状态正常
        CoachDTO coachDTO = coachFeignClient.getCoachById(dto.getMainCoachId());
        if (coachDTO == null) {
            throw new BusinessException("所选教练不存在");
        }
        if (!"NORMAL".equals(coachDTO.getStatus())) {
            throw new BusinessException("所选教练当前不在职，无法分配学员");
        }

        Student student = new Student();
        BeanUtils.copyProperties(dto, student);
        student.setCompletedTrainingCount(0);
        student.setStatus("NORMAL");
        studentMapper.insert(student);

        return toVO(student);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StudentVO update(Long id, StudentUpdateDTO dto) {
        Student student = findStudent(id);

        // 如果更换了主教练，校验新教练是否存在且状态正常
        if (dto.getMainCoachId() != null && !dto.getMainCoachId().equals(student.getMainCoachId())) {
            CoachDTO coachDTO = coachFeignClient.getCoachById(dto.getMainCoachId());
            if (coachDTO == null) {
                throw new BusinessException("所选教练不存在");
            }
            if (!"NORMAL".equals(coachDTO.getStatus())) {
                throw new BusinessException("所选教练当前不在职，无法分配学员");
            }
        }

        // 校验应完成次数 >= 已完成次数
        if (dto.getRequiredTrainingCount() != null
                && dto.getRequiredTrainingCount() < student.getCompletedTrainingCount()) {
            throw new BusinessException("应完成练车次数不能小于已完成次数("
                    + student.getCompletedTrainingCount() + ")");
        }

        BeanUtils.copyProperties(dto, student, "id", "completedTrainingCount", "status", "createTime", "updateTime");
        studentMapper.updateById(student);

        return toVO(student);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, String status) {
        if (!List.of("NORMAL", "STOPPED").contains(status)) {
            throw new BusinessException("无效的学员状态：" + status);
        }
        Student student = findStudent(id);
        student.setStatus(status);
        studentMapper.updateById(student);
    }

    @Override
    public List<StudentSimpleVO> simpleList() {
        return studentMapper.selectList(
                        new LambdaQueryWrapper<Student>()
                                .eq(Student::getStatus, "NORMAL")
                                .orderByAsc(Student::getName))
                .stream()
                .map(s -> {
                    StudentSimpleVO vo = new StudentSimpleVO();
                    vo.setId(s.getId());
                    vo.setName(s.getName());
                    vo.setPhone(s.getPhone());
                    vo.setStatus(s.getStatus());
                    return vo;
                })
                .collect(Collectors.toList());
    }

    @Override
    public StudentDTO getDTObyId(Long id) {
        Student student = studentMapper.selectById(id);
        StudentDTO studentDTO = new StudentDTO();
        BeanUtils.copyProperties(student, studentDTO);
        return studentDTO;
    }

    @Override
    public List<StudentDTO> getDTOByUsername(String username) {
        LambdaQueryWrapper<Student> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(Student::getName, username);
        List<Student> list = studentMapper.selectList(wrapper);
        return list.stream().map(s -> {
            StudentDTO dto = new StudentDTO();
            BeanUtils.copyProperties(s, dto);
            return dto;
        }).toList();
    }

    @Override
    public void updateById(Long id) {
        Student s = new Student();
        s.setId(id);
        studentMapper.updateById(s);
    }

    @Override
    public Long count() {
        return studentMapper.selectCount(null);
    }

    private Student findStudent(Long id) {
        Student student = studentMapper.selectById(id);
        if (student == null) {
            throw new BusinessException("学员不存在");
        }
        return student;
    }

    /** 转换为 VO，并填充主教练姓名 */
    private StudentVO toVO(Student student) {
        StudentVO vo = new StudentVO();
        BeanUtils.copyProperties(student, vo);
        if (student.getMainCoachId() != null) {
            CoachDTO coachDTO = coachFeignClient.getCoachById(student.getMainCoachId());
            vo.setMainCoachName(coachDTO != null ? coachDTO.getName() : "未知");
        }
        return vo;
    }
}
