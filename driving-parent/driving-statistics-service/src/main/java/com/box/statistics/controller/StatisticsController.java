package com.box.statistics.controller;

import com.box.common.result.Result;
import com.box.common.utils.SecurityContextUtils;
import com.box.common.web.utils.UserContext;
import com.box.statistics.service.StatisticsService;
import com.box.statistics.vo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

/**
 * 统计数据控制器 — 统一 statistics 模块，按角色开放不同接口
 */
@RestController
@RequestMapping("/api/statistics")
@RequiredArgsConstructor
public class StatisticsController {

    private final StatisticsService statisticsService;

    @GetMapping("/admin/overview")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<AdminOverviewVO> adminOverview() { return Result.success(statisticsService.adminOverview()); }

    @GetMapping("/admin/coach-load")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<CoachLoadVO>> coachLoad() { return Result.success(statisticsService.coachLoad()); }

    @GetMapping("/admin/reservation-trend")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<ReservationTrendVO>> reservationTrend() { return Result.success(statisticsService.reservationTrend()); }

    @GetMapping("/admin/schedule-utilization")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<ScheduleUtilizationVO>> scheduleUtilization() { return Result.success(statisticsService.scheduleUtilization()); }

    @GetMapping("/admin/student-progress")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<StudentProgressVO>> studentProgress() { return Result.success(statisticsService.studentProgress()); }

    @GetMapping("/admin/recent-activities")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<RecentActivityVO>> recentActivities() { return Result.success(statisticsService.recentActivities()); }

    @GetMapping("/coach/overview")
    @PreAuthorize("hasRole('COACH')")
    public Result<CoachOverviewVO> coachOverview() {
        Long coachId = Objects.requireNonNull(SecurityContextUtils.getCurrentUser()).getRelatedId();
        return Result.success(statisticsService.coachOverview(coachId));
    }

    @GetMapping("/student/overview")
    @PreAuthorize("hasRole('STUDENT')")
    public Result<StudentOverviewVO> studentOverview() {
        Long studentId = UserContext.getRelatedId();
        return Result.success(statisticsService.studentOverview(studentId));
    }
}
