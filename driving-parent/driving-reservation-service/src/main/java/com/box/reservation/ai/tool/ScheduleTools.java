package com.box.reservation.ai.tool;

import com.box.api.coach.CoachDTO;
import com.box.api.coach.CoachFeignClient;
import com.box.api.schedule.ScheduleDTO;
import com.box.api.schedule.ScheduleFeignClient;
import com.box.api.student.StudentFeignClient;
import com.box.common.result.Result;
import com.box.reservation.common.constant.RedisKeyConstants;
import com.box.reservation.dto.ai.AiToolParams;
import com.box.reservation.dto.ai.SecureQueryScheduleParam;
import com.box.reservation.service.impl.ReservationServiceImpl;
import com.box.reservation.vo.ReservationOptionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ScheduleTools {

    private final CoachFeignClient coachFeignClient;
    private final StringRedisTemplate stringRedisTemplate;
    private final ScheduleFeignClient scheduleFeignClient;
    private final StudentFeignClient studentFeignClient;
    private final ReservationServiceImpl reservationService;
    @Tool
    public String querySchedule(AiToolParams params, ToolContext toolContext) {
        Map<String, Object> context = toolContext.getContext();
        Long studentId = (Long) context.get("studentId");
        if (studentId == null){
            return "未登录，请先登录";
        }

        String coachName = params.coachName();

        try {
            Result<CoachDTO> coachDTOResult = coachFeignClient.getCoachByName(coachName);
            CoachDTO data = coachDTOResult.getData();
            SecureQueryScheduleParam build = SecureQueryScheduleParam.build(params, studentId);
            String name = data.getName();
            LocalDate date = build.date();
            String timeSlot = build.timeSlot();
            Result<Long> result = coachFeignClient.getCountByName(name, date, timeSlot);
            Long count = result.getData();
            if (count == 0){
                return "当前"+ name + "教练已经没有名额啦！";
            }
            return "当前" + name + "教练还有" + count + "个名额！";
        } catch (Exception e) {
            return "查询失败：" + e.getMessage();
        }

    }

    @Tool
    public String queryAdjustableCoaches(AiToolParams params, ToolContext toolContext) {
        Object studentId = toolContext.getContext().get("studentId");
        if (studentId == null){
            return "未登录，请先登录";
        }

        ReservationOptionVO options = null;
        try {
            SecureQueryScheduleParam build = SecureQueryScheduleParam.build(params, (Long) studentId);
            LocalDate date = build.date();
            String timeSlot = build.timeSlot();
            options = reservationService.getOptions((Long) studentId, date, timeSlot);
        } catch (Exception e) {
            return "查询失败:"+e.getMessage();
        }
        List<ReservationOptionVO.AdjustOption> adjustOptions = options.getAdjustOptions();
        String collect = adjustOptions.stream().map(o -> o.getCoachName() + ".还剩" + o.getRemainCount() + "个名额")
                .collect(Collectors.joining("\n"));
        if (collect.isEmpty()){
            return "没有教练有空";
        }
        return "有空的教练有"+ collect;
    }

    @Tool
    public String createReservationDraft(AiToolParams params, ToolContext toolContext) {
        Object studentId = toolContext.getContext().get("studentId");
        if (studentId == null) {
            return "未登录，请先登录";
        }
        try {
            SecureQueryScheduleParam build = SecureQueryScheduleParam.build(params, (Long) studentId);

            Long remain = coachFeignClient.getCountByName(build.coachName(), build.date(), build.timeSlot()).getData();
            if (remain == null || remain <= 0) {
                return "教练「" + build.coachName() + "」在该时段已无空位，建议换个时间或其他教练";
            }

            Result<CoachDTO> coachResult = coachFeignClient.getCoachByName(build.coachName());
            CoachDTO coach = coachResult == null ? null : coachResult.getData();
            if (coach == null) {
                return "未找到教练「" + build.coachName() + "」，请确认名字是否正确";
            }

            Result<ScheduleDTO> scheduleResult =
                    scheduleFeignClient.getByCoach(coach.getId(), build.date(), build.timeSlot());
            ScheduleDTO schedule = scheduleResult == null ? null : scheduleResult.getData();
            if (schedule == null) {
                return "教练「" + coach.getName() + "」在该时段没有排班";
            }

            String draftId = UUID.randomUUID().toString();
            // 存 JSON，字段名要和 AiChatController#confirm 里取的一致
            String value = String.format(
                    "{\"studentId\":%d,\"scheduleId\":%d,\"date\":\"%s\",\"timeSlot\":\"%s\",\"coachName\":\"%s\"}",
                    studentId, schedule.getId(), build.date(), build.timeSlot(), coach.getName());
            stringRedisTemplate.opsForValue()
                    .set(RedisKeyConstants.AI_DRAFT + draftId, value, 2, TimeUnit.MINUTES);

            return String.format("草稿已生成：%s %s，教练 %s，剩余 %d 个名额。2 分钟内确认有效，凭证：[DRAFT_%s]",
                    build.date(),
                    "MORNING".equals(build.timeSlot()) ? "上午" : "下午",
                    coach.getName(),
                    remain,
                    draftId);
        } catch (Exception e) {
            return "创建草稿失败：" + e.getMessage();
        }
    }

}

