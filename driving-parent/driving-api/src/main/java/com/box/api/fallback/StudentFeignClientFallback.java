package com.box.api.fallback;

import com.box.api.student.StudentDTO;
import com.box.api.student.StudentFeignClient;
import com.box.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class StudentFeignClientFallback implements StudentFeignClient {

    @Override
    public Result<StudentDTO> getById(Long id) {
        // 记录异常日志
        log.error("调用学员服务（driving-student-service）获取学员信息失败，触发降级。学员ID: {}", id);
        // 返回统一的 503 业务异常
        return Result.error(503, "学员信息校验服务暂时不可用，请稍后再试");
    }

    @Override
    public Result<List<StudentDTO>> selectList(String username) {
        log.error("调用学员服务（driving-student-service）获取学员信息失败，触发降级。学员姓名: {}", username);
        return Result.error(503, "学员信息校验服务暂时不可用，请稍后再试");
    }

    @Override
    public Result<Void> incrementCompleteCount(Long id) {
        log.error("调用学员服务（driving-student-service）更新学员信息失败，触发降级。学员ID: {}", id);
        return Result.error(503, "学员信息校验服务暂时不可用，请稍后再试");
    }
}