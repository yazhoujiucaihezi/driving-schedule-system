package com.box.training.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.box.common.result.Result;
import com.box.common.web.utils.UserContext;
import com.box.training.dto.TrainingRecordQueryDTO;
import com.box.training.service.TrainingRecordService;
import com.box.training.vo.TrainingRecordVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 练车记录控制器
 */
@RestController
@RequestMapping("/api/training-records")
@RequiredArgsConstructor
public class TrainingRecordController {

    private final TrainingRecordService trainingRecordService;

    /** 学员查询我的练车记录 */
    @GetMapping("/my")
    @PreAuthorize("hasRole('STUDENT')")
    public Result<Page<TrainingRecordVO>> my(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        Long studentId = UserContext.getRelatedId();
        return Result.success(trainingRecordService.myRecords(studentId, page, size));
    }

    /** 教练查询练车记录 */
    @GetMapping("/coach")
    @PreAuthorize("hasRole('COACH')")
    public Result<Page<TrainingRecordVO>> coach(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        Long coachId = UserContext.getRelatedId();
        return Result.success(trainingRecordService.coachRecords(coachId, page, size));
    }

    /** 管理员查询全部 */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Page<TrainingRecordVO>> list(TrainingRecordQueryDTO dto) {
        return Result.success(trainingRecordService.adminQuery(dto));
    }
}
