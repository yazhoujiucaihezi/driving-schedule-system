package com.box.training.controller;

import com.box.api.training.TrainingRecordCreateDTO;
import com.box.common.result.Result;
import com.box.training.entity.TrainingRecord;
import com.box.training.mapper.TrainingRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/training-record")
@RequiredArgsConstructor
public class TrainingRecordInternalController {

    private final TrainingRecordMapper trainingRecordMapper;

    @PostMapping("/create")
    public Result<Void> create(@RequestBody TrainingRecordCreateDTO dto) {
        TrainingRecord tr = new TrainingRecord();
        BeanUtils.copyProperties(dto, tr);
        trainingRecordMapper.insert(tr);
        return Result.success();
    }
}