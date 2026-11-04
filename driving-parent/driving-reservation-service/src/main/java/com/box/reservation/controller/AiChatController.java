package com.box.reservation.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.box.common.web.utils.UserContext;
import com.box.reservation.common.constant.RedisKeyConstants;
import com.box.reservation.dto.ReservationCreateDTO;
import com.box.reservation.service.ReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/reservations/ai")
@RequiredArgsConstructor
@Slf4j
public class AiChatController {

    private final StringRedisTemplate redisTemplate;
    private final ReservationService reservationService;
    private final ChatClient reservationChatClient;

    @GetMapping("/chat")
    public String chat(@RequestParam String message) {
        Long studentId = UserContext.getRelatedId();
        if (studentId == null){
            return "请先登录";
        }
        try {
            String systemPrompt = """
                    你是驾校预约助手。今天的日期是 %s。
                    
                    你能做的事：
                    1. 查某位教练某个时段还剩几个名额
                    2. 主教练满了时，列出同日期同时段其他有空位的教练
                    3. 名额充足时生成预约草稿，交给用户确认
                    
                    规则：
                    - 你没有直接下单的权限。只能查询和生成草稿，必须等用户明确确认后，由用户在页面上点确认完成下单。
                    - 调用工具时，日期参数一律传 yyyy-MM-dd 格式的绝对日期。用户说「明天」「下周三」时，必须先根据今天日期换算成具体日期，绝不能把这类相对描述直接传给工具。
                    - 用户没说清教练姓名或日期时，先问清楚再调用工具。
                    - 工具返回的错误文本原样转述给用户，不要编造名额或预约结果。
                    """.formatted(LocalDate.now());
            return  reservationChatClient.prompt()
                    .system(systemPrompt)                                        // ← 新增：把上面拼好的 prompt 传进去
                    .user(message)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID,studentId))
                    .toolContext(Map.of("studentId", studentId))
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("AI 对话失败", e);
            return "预约助手暂不可用，请稍后重试";
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
            dto.setAdjusted(false); // 如果是调剂，你需要在这里处理
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