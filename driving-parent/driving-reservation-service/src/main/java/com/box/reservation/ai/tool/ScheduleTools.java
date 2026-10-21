package com.box.reservation.ai.tool;

import com.box.api.coach.CoachDTO;
import com.box.api.coach.CoachFeignClient;
import com.box.api.schedule.ScheduleDTO;
import com.box.api.schedule.ScheduleFeignClient;
import com.box.api.student.StudentDTO;
import com.box.api.student.StudentFeignClient;
import com.box.common.result.Result;
import com.box.reservation.common.constant.RedisKeyConstants;
import com.box.reservation.dto.ai.AiToolParams;
import com.box.reservation.dto.ai.SecureQueryScheduleParam;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class ScheduleTools {

    private final CoachFeignClient coachFeignClient;
    private final StringRedisTemplate stringRedisTemplate;
    private final ScheduleFeignClient scheduleFeignClient;
    private final StudentFeignClient studentFeignClient;
    @Tool(description = "查询指定教练在指定日期的剩余名额。注意：教练名称可以是简称（如'张三'、'张教练'），不需要强求全名。")
    public String querySchedule(AiToolParams params, ToolContext toolContext) {
        System.out.println("=== 大模型传过来的日期: [" + params.dateString() + "]");
        System.out.println("=== 大模型传过来的时段: [" + params.timeSlot() + "]");
        Long studentId = (Long) toolContext.getContext().get("studentId");
        if (studentId == null) {
            return "用户未登录";
        }

        String slot = params.timeSlot();

        if (slot == null || slot.isBlank()) {
            slot = "MORNING"; // 默认上午
        }

        try {
            // 构建安全查询参数
            SecureQueryScheduleParam secure = SecureQueryScheduleParam.build(params, studentId);
            Integer count = coachFeignClient.getCountByName(secure.coachName(), secure.date(), slot).getData();
            if (count == null) {
                return "查询失败：" + "没有这个教练";
            }
            return "查询成功：" + secure.coachName() + " 在该时段还有 " + count + " 个名额，可以预约。";
        }
        catch (Exception e){
            return "查询失败" + e.getMessage();
        }
    }

    @Tool(description = "当主教练满员时，查询同一天同一时段其他还有空位的教练列表")
    public String queryAdjustableCoaches(AiToolParams params, ToolContext toolContext) {
        Long studentId = (Long) toolContext.getContext().get("studentId");
        if (studentId == null) {
            return "用户未登录，无法查询调剂教练。";
        }

        try {
            // 1. 参数校验与解析（复用你的 build 方法，把 String 转 LocalDate 并校验）
            SecureQueryScheduleParam secure = SecureQueryScheduleParam.build(params, studentId);

            // 2. 查出当前学员的主教练 ID
            Result<StudentDTO> studentResult = studentFeignClient.getById(studentId);
            if (studentResult == null || studentResult.getData() == null) {
                return "未找到当前学员信息。";
            }
            Long mainCoachId = studentResult.getData().getMainCoachId();

            // 3. 调用排班服务，查询可调剂的排班列表
            // 假设你原有的接口叫 listAdjustable(date, timeSlot, excludeCoachId)
            Result<List<ScheduleDTO>> result = scheduleFeignClient.listAdjustable(secure.date(), secure.timeSlot(), mainCoachId);

            if (result == null || result.getCode() != 200 || result.getData() == null || result.getData().isEmpty()) {
                return "当前时段没有任何教练有空余名额。";
            }

            // 4. 组装成大模型能看懂的文本
            String text = getString(result);
            if (text.equals("当前时段以下教练还有空位：\n")) {
                return "当前时段没有其他教练有空余名额。";
            }
            return text;
        } catch (Exception e) {
            return "查询调剂教练失败：" + e.getMessage();
        }
    }

    @NonNull
    private static String getString(Result<List<ScheduleDTO>> result) {
        StringBuilder sb = new StringBuilder("当前时段以下教练还有空位：\n");
        for (ScheduleDTO schedule : result.getData()) {
            // 这里最好再查一下教练名字，如果 ScheduleDTO 里已经带了 coachName 直接用即可
            // 假设直接用了 coachId 或者 coachName
            int remain = schedule.getMaxStudents() - schedule.getCurrentStudents();
            if (remain > 0) {
                // 假设 ScheduleDTO 里有 coachName 字段
                sb.append(String.format("- 教练：%s，剩余名额：%d，排班ID：%d\n",
                        schedule.getCoachId(),
                        remain,
                        schedule.getId()));
            }
        }

        return sb.toString();
    }

    @Tool(description = "生成预约草稿，用户确认后才会真正预约")
    public String createReservationDraft(AiToolParams params, ToolContext toolContext) {
        Long studentId = (Long) toolContext.getContext().get("studentId");
        if (studentId == null) {
            return "用户未登录";
        }
        try {
            // 1. 构建安全参数
            SecureQueryScheduleParam secure = SecureQueryScheduleParam.build(params, studentId);

            //2. 查教练（请把 getByName 换成你真实调用的方法）
            System.out.println("=== 准备调 coachFeignClient");
            Result<CoachDTO> coachResult = coachFeignClient.getCoachByName(secure.coachName());
            System.out.println("=== coachFeignClient 返回结果: " + coachResult);
            if (coachResult == null || coachResult.getCode() != 200 || coachResult.getData() == null) {
                return "未找到该教练，无法生成草稿。";
            }
            Long coachId = coachResult.getData().getId();

            // 3. 查排班实体（请把 getByCoach 换成你真实的排班服务方法）
            System.out.println("=== 准备调 scheduleFeignClient");
            Result<ScheduleDTO> schedResult = scheduleFeignClient.getByCoach(coachId, secure.date(), secure.timeSlot());
            System.out.println("=== scheduleFeignClient 返回结果: " + schedResult);
            if (schedResult == null || schedResult.getCode() != 200 || schedResult.getData() == null) {
                return "该教练在此时段没有排班，无法生成草稿。";
            }
            ScheduleDTO schedule = schedResult.getData();

            // 4. 判断名额
            int remain = schedule.getMaxStudents() - schedule.getCurrentStudents();
            if (remain <= 0) {
                return "该时段已满员，无法生成预约草稿。";
            }

            // 5. 拿到真实的 scheduleId
            Long realScheduleId = schedule.getId();

            // 6. 存 Redis
            String draftId = "DRAFT_" + UUID.randomUUID().toString().substring(0, 8);
            String redisKey = RedisKeyConstants.AI_DRAFT + draftId;
            String draftData = "{\"scheduleId\":" + realScheduleId + ",\"studentId\":" + studentId + "}";

            stringRedisTemplate.opsForValue().set(redisKey, draftData, 5, TimeUnit.MINUTES);

            return "预约草稿已生成，请原样输出以下凭证等待用户确认：[" + draftId + "]";

        } catch (Exception e) {
            return "生成预约草稿失败：" + e.getMessage();
        }
    }

}