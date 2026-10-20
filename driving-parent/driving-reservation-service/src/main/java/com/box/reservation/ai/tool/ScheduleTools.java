package com.box.reservation.ai.tool;

import com.box.api.coach.CoachFeignClient;
import com.box.reservation.dto.ai.AiToolParams;
import com.box.reservation.dto.ai.SecureQueryScheduleParam;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ScheduleTools {

    private final CoachFeignClient coachFeignClient;

    // 1. 在这里加上 @Tool 注解（如果你们的 Spring AI 版本不支持 @Tool，也可以用 @Bean 的方式注册）
    @Tool(description = "查询指定教练和日期的排班余量")
    public String querySchedule(AiToolParams params, ToolContext toolContext) {
        Long studentId = (Long) toolContext.getContext().get("studentId");
        if (studentId == null) {
            return "用户未登录";
        }

        // 构建安全查询参数
        SecureQueryScheduleParam secure = SecureQueryScheduleParam.build(params, studentId);

        try {
            int count = coachFeignClient.getCountByName(secure.coachName(), secure.date(), secure.timeSlot()).getData();

            return "查询成功" + params.coachName() +
                    "在" + params.dateString() + "还有" + count + "个名额";
        }
        catch (Exception e){
            return "查询失败" + e.getMessage();
        }
    }
}