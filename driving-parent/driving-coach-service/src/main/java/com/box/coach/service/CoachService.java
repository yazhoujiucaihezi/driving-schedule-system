package com.box.coach.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.box.api.coach.CoachDTO;
import com.box.coach.dto.CoachCreateDTO;
import com.box.coach.dto.CoachQueryDTO;
import com.box.coach.dto.CoachUpdateDTO;
import com.box.coach.vo.CoachSimpleVO;
import com.box.coach.vo.CoachVO;

import java.time.LocalDate;
import java.util.List;

/**
 * 教练业务接口
 */
public interface CoachService {

    /** 分页查询教练 */
    Page<CoachVO> queryPage(CoachQueryDTO dto);

    /** 查询教练详情 */
    CoachVO getById(Long id);

    /** 新增教练 */
    CoachVO create(CoachCreateDTO dto);

    /** 修改教练 */
    CoachVO update(Long id, CoachUpdateDTO dto);

    /** 修改教练状态（NORMAL/LEAVE/STOPPED） */
    void updateStatus(Long id, String status);

    /** 查询可用教练简单列表（用于下拉选择） */
    List<CoachSimpleVO> simpleList();

    CoachDTO getCoachById(Long id);

    Long getCountByName(String name, LocalDate date, String timeSlot);
}
