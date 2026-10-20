package com.box.reservation.controller;

import com.box.common.web.utils.UserContext;
import com.box.reservation.ai.tool.ScheduleTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/reservations/ai")
public class AiChatController {

    private final ChatClient chatClient;
    private final ScheduleTools scheduleTools;

    // 通过构造器注入 ChatClient.Builder 和 你的工具类
    public AiChatController(ChatClient.Builder chatClientBuilder, ScheduleTools scheduleTools) {
        this.chatClient = chatClientBuilder.build();
        this.scheduleTools = scheduleTools;
    }

    @GetMapping("/chat")
    public String chat(@RequestParam String message) {

        Long studentId = UserContext.getRelatedId();

        var toolContext = new ToolContext(Map.of("studentId", studentId));

        // 发起大模型对话
        return chatClient.prompt()
                .system("你是一个驾校助手。请注意，今天的日期是：" + LocalDate.now() + "。用户的相对时间（如明天、后天）请自行推算为绝对日期。")
                .user(message)
                .tools(scheduleTools)         // 把工具给大模型
                .toolContext(toolContext.getContext())     // 把后门数据塞进去
                .call()
                .content();
    }
}