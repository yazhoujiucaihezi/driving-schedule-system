package com.box.reservation.controller;

import com.box.reservation.ai.tool.ScheduleTools;
import com.box.reservation.dto.ai.AiToolParams;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/reservations/ai/test")
@RequiredArgsConstructor
public class AiTestController {

    private final ScheduleTools scheduleTools;
    @GetMapping("/trigger")
    public String testTool() {
        // 1. 伪造大模型传进来的参数
        AiToolParams params = new AiToolParams("唐国义", "2026-10-21", "MORNING");

        // 2. 暴力塞入一个真实的学员 ID（假设数据库里 ID 为 1 的学员是存在的）
        Map<String, Object> contextMap = Map.of("studentId", 1L); // 👈 就用 1L
        ToolContext context = new ToolContext(contextMap);

        return scheduleTools.querySchedule(params, context);
    }
}