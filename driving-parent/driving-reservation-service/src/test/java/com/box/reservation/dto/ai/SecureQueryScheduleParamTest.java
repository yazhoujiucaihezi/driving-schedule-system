package com.box.reservation.dto.ai;
import com.box.reservation.dto.ai.AiToolParams;
import com.box.reservation.dto.ai.SecureQueryScheduleParam;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class SecureQueryScheduleParamTest {

    // 测试用例 1：正常情况，未来日期
    @Test
    public void testBuild_Success() {
        AiToolParams aiParams = new AiToolParams("唐国义", "2026-10-21");
        Long studentId = 1001L;
        SecureQueryScheduleParam result = SecureQueryScheduleParam.build(aiParams, studentId);
        Assertions.assertEquals("唐国义", result.coachName());
        Assertions.assertEquals(1001L, result.studentId());
    }

    // 测试用例 2：异常情况，过去日期（预期会抛出异常）
    @Test
    public void testBuild_PastDate_ThrowsException() {
        // 1. 准备一个过去的时间
        AiToolParams aiParams = new AiToolParams("唐国义", "2020-01-01");
        Long studentId = 1001L;
        Assertions.assertThrows(IllegalArgumentException.class, () -> SecureQueryScheduleParam.build(aiParams, studentId));
    }
}