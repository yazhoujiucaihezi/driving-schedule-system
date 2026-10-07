package com.box.api.training;

import com.box.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "driving-training-service")
public interface TrainingRecordFeignClient {

    @PostMapping("/internal/training-record/create")
    Result<Void> create(@RequestBody TrainingRecordCreateDTO dto);
}