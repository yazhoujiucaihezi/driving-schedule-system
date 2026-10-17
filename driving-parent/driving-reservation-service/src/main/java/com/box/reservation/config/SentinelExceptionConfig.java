package com.box.reservation.config;

import com.alibaba.csp.sentinel.adapter.spring.webmvc.callback.BlockExceptionHandler;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.box.common.result.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;


@Configuration
public class SentinelExceptionConfig implements BlockExceptionHandler {
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, BlockException e) throws Exception {
        response.setContentType("application/json;charset=utf-8");
        response.setStatus(200);

        String msg = "系统繁忙，请稍后再试";

        if (e instanceof FlowException){
            msg = "当前抢约人数过多，请稍后再试！(限流)";
        }
        else if (e instanceof DegradeException){
            msg = "系统服务暂时降级，请稍后再试！(熔断)";
        }

        Result result = Result.error(429, msg);

        String jsonResult = new ObjectMapper().writeValueAsString(result);

        response.getWriter().write(jsonResult);
    }
}
