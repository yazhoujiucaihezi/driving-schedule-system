package com.box.api.student;

import com.box.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@FeignClient(name = "driving-student-service")
public interface StudentFeignClient {

    @GetMapping("/internal/student/by-id/{id}")
    Result<StudentDTO> getById(@PathVariable("id") Long id);

    @GetMapping("/internal/student/by-username/{username}")
    Result<List<StudentDTO>> selectList(@PathVariable("username") String username);

}