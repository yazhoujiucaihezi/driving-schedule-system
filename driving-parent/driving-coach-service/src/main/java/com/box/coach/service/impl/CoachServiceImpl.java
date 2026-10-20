package com.box.coach.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.box.api.coach.CoachDTO;
import com.box.coach.dto.CoachCreateDTO;
import com.box.coach.dto.CoachQueryDTO;
import com.box.coach.dto.CoachUpdateDTO;
import com.box.coach.entity.Coach;
import com.box.coach.mapper.CoachMapper;
import com.box.coach.service.CoachService;
import com.box.coach.vo.CoachSimpleVO;
import com.box.coach.vo.CoachVO;
import com.box.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 教练业务实现
 */
@Service
@RequiredArgsConstructor
public class CoachServiceImpl implements CoachService {

    private final CoachMapper coachMapper;

    @Override
    public Page<CoachVO> queryPage(CoachQueryDTO dto) {
        LambdaQueryWrapper<Coach> wrapper = new LambdaQueryWrapper<>();
        // 关键词搜索：匹配姓名或手机号
        if (StringUtils.hasText(dto.getKeyword())) {
            wrapper.and(w -> w
                    .like(Coach::getName, dto.getKeyword())
                    .or()
                    .like(Coach::getPhone, dto.getKeyword()));
        }
        if (StringUtils.hasText(dto.getStatus())) {
            wrapper.eq(Coach::getStatus, dto.getStatus());
        }
        wrapper.orderByDesc(Coach::getCreateTime);

        Page<Coach> page = coachMapper.selectPage(
                new Page<>(dto.getPage(), dto.getSize()), wrapper);

        Page<CoachVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(this::toVO).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public CoachVO getById(Long id) {
        Coach coach = findCoach(id);
        return toVO(coach);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CoachVO create(CoachCreateDTO dto) {
        // 校验手机号是否重复
        checkPhoneDuplicate(dto.getPhone(), null);

        Coach coach = new Coach();
        BeanUtils.copyProperties(dto, coach);
        coach.setStatus("NORMAL");
        coachMapper.insert(coach);

        return toVO(coach);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CoachVO update(Long id, CoachUpdateDTO dto) {
        Coach coach = findCoach(id);

        // 校验手机号是否与其他教练重复
        checkPhoneDuplicate(dto.getPhone(), id);

        BeanUtils.copyProperties(dto, coach, "id", "status", "createTime", "updateTime");
        coachMapper.updateById(coach);

        return toVO(coach);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, String status) {
        if (!List.of("NORMAL", "LEAVE", "STOPPED").contains(status)) {
            throw new BusinessException("无效的教练状态：" + status);
        }
        Coach coach = findCoach(id);
        coach.setStatus(status);
        coachMapper.updateById(coach);
    }

    @Override
    public List<CoachSimpleVO> simpleList() {
        return coachMapper.selectList(
                        new LambdaQueryWrapper<Coach>()
                                .eq(Coach::getStatus, "NORMAL")
                                .orderByAsc(Coach::getName))
                .stream()
                .map(c -> {
                    CoachSimpleVO vo = new CoachSimpleVO();
                    vo.setId(c.getId());
                    vo.setName(c.getName());
                    vo.setStatus(c.getStatus());
                    return vo;
                })
                .collect(Collectors.toList());
    }

    @Override
    public CoachDTO getCoachById(Long id) {
        CoachDTO coachDTO = new CoachDTO();
        Coach coach = coachMapper.selectById(id);
        BeanUtils.copyProperties(coach, coachDTO);
        return coachDTO;
    }

    @Override
    public Long getCountByName(String name, LocalDate date, String timeSlot) {
        Coach coach = coachMapper.selectOne(new LambdaQueryWrapper<Coach>().eq(Coach::getName, name));
        Long id = coach.getId();
        return coachMapper.getCount(id, date, timeSlot);
    }

    // ==================== 内部方法 ====================

    /** 查询教练，不存在则抛异常 */
    private Coach findCoach(Long id) {
        Coach coach = coachMapper.selectById(id);
        if (coach == null) {
            throw new BusinessException("教练不存在");
        }
        return coach;
    }

    /** 校验手机号是否已被其他教练使用 */
    private void checkPhoneDuplicate(String phone, Long excludeId) {
        LambdaQueryWrapper<Coach> wrapper = new LambdaQueryWrapper<Coach>()
                .eq(Coach::getPhone, phone);
        if (excludeId != null) {
            wrapper.ne(Coach::getId, excludeId);
        }
        Long count = coachMapper.selectCount(wrapper);
        if (count > 0) {
            throw new BusinessException("该手机号已被其他教练使用");
        }
    }

    private CoachVO toVO(Coach coach) {
        CoachVO vo = new CoachVO();
        BeanUtils.copyProperties(coach, vo);
        return vo;
    }
}
