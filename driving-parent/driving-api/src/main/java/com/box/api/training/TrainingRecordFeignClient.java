package com.box.api.training;

import com.box.api.fallback.TrainingRecordFeignClientFallbackFactory;
import com.box.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "driving-training-service", fallbackFactory = TrainingRecordFeignClientFallbackFactory.class)
public interface TrainingRecordFeignClient {

    @PostMapping("/internal/training-record/create")
    Result<Void> create(@RequestBody TrainingRecordCreateDTO dto);
}