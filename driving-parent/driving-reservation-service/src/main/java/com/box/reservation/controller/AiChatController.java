package com.box.reservation.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.box.common.web.utils.UserContext;
import com.box.reservation.ai.tool.ScheduleTools;
import com.box.reservation.common.constant.RedisKeyConstants;
import com.box.reservation.dto.ReservationCreateDTO;
import com.box.reservation.service.ReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/reservations/ai")
@RequiredArgsConstructor
public class AiChatController {

    private final ChatClient.Builder chatClientBuilder;
    private final ScheduleTools scheduleTools;
    private final StringRedisTemplate redisTemplate;
    private final ReservationService reservationService;

    @SuppressWarnings("CallToPrintStackTrace")
    @GetMapping("/chat")
    public String chat(@RequestParam String message) {

        Long studentId = UserContext.getRelatedId();

        var toolContext = new ToolContext(Map.of("studentId", studentId));

        // 发起大模型对话
        try {
            return chatClientBuilder
                    .build()
                    .prompt()
                    .system("你是一个驾校助手。请注意，今天的日期是：" + LocalDate.now() + "。用户的相对时间（如明天、后天）请自行推算为绝对日期。" + " \uD83D\uDEA8 极其重要的系统指令（必须严格遵守）：\n" +
                            "    1. 如果用户想预约，先调用 querySchedule 查询主教练名额。\n" +
                            "    2. 如果主教练满员，调用 queryAdjustableCoaches 查调剂教练。\n" +
                            "    3. 绝不允许你直接回复用户\"预约成功\"！你没有任何权限直接预约！\n" +
                            "    4. 你必须调用 createReservationDraft 工具生成草稿。\n" +
                            "    5. 拿到草稿工具返回的结果后，必须原样输出给用户（例如：\"草稿已生成，请确认：[DRAFT_xxx]\"）")
                    .user(message)
                    .tools(scheduleTools)         // 把工具给大模型
                    .toolContext(toolContext.getContext())     // 把数据塞进去
                    .call()
                    .content();
        } catch (Exception e) {
            e.printStackTrace();
            return "AI 调用异常: " + e.getMessage();
        }
    }

    @PostMapping("/confirm")
    public String confirm(@RequestParam String draftId) {
        String draftKey = RedisKeyConstants.AI_DRAFT + draftId;
        String draft = redisTemplate.opsForValue().get(draftKey);
        if (draft == null){
            return "草稿已过期或不存在，请重新告诉 AI 您的预约需求。";
        }
        try {
            // 2. 解析 JSON (这里用简单的 Fastjson 或 Jackson)
            JSONObject json = JSON.parseObject(draft);
            Long scheduleId = json.getLong("scheduleId");
            Long studentId = json.getLong("studentId");

            // 3. 组装你原来微服务下单用的 DTO
            ReservationCreateDTO dto = new ReservationCreateDTO();
            dto.setScheduleId(scheduleId);
            // dto.setIsAdjusted(false); // 如果是调剂，你需要在这里处理
            // 4. 调用create
            reservationService.create(dto, studentId);
            // 5. 下单成功，删除 Redis 草稿，防止重复点击
            redisTemplate.delete(draftKey);

            return "预约成功！您的排班 ID 是：" + scheduleId;

        } catch (Exception e) {
            // 如果 reservationService.create 抛出“已满员”异常，会在这里被捕获
            return "预约失败：" + e.getMessage();
        }
    }

}