package com.box.reservation.config;

import com.alibaba.csp.sentinel.adapter.spring.webmvc.callback.BlockExceptionHandler;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.box.common.result.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class SentinelExceptionConfig implements BlockExceptionHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, BlockException e) throws Exception {
        int code;
        String message;

        if (e instanceof FlowException) {
            // 流量超阈值
            code = 429;
            message = "当前预约人数过多，请稍后重试";
        } else if (e instanceof DegradeException) {
            // 熔断
            code = 503;
            message = "系统繁忙，请稍后重试";
        } else {
            code = 429;
            message = "请求被拒绝，请稍后重试";
        }

        // HTTP 状态保持 200：通信是成功的，限流是业务结果，用 code 表达
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.error(code, message)));
    }
}
