package com.box.coach.controller;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.box.common.result.Result;
import com.box.common.utils.SecurityContextUtils;
import com.box.coach.dto.CoachCreateDTO;
import com.box.coach.dto.CoachQueryDTO;
import com.box.coach.dto.CoachUpdateDTO;
import com.box.coach.service.CoachService;
import com.box.coach.vo.CoachSimpleVO;
import com.box.coach.vo.CoachVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

/**
 * 教练管理控制器
 */
@RestController
@RequestMapping("/api/coaches")
@RequiredArgsConstructor
public class CoachController {

    private final CoachService coachService;

    /** 分页查询教练 — 管理员可查全部，教练只能查自己 */
    @GetMapping
    public Result<Page<CoachVO>> list(CoachQueryDTO dto) {
        // 教练角色只能查看自己的信息
        if ("COACH".equals(SecurityContextUtils.getCurrentUserRole())) {
            Long coachId = SecurityContextUtils.getCurrentUser().getRelatedId();
            CoachVO vo = coachService.getById(coachId);
            Page<CoachVO> page = new Page<>(1, 1, 1);
            page.setRecords(List.of(vo));
            return Result.success(page);
        }
        return Result.success(coachService.queryPage(dto));
    }

    @GetMapping("/simple")
    public Result<List<CoachSimpleVO>> simpleList() {
        return Result.success(coachService.simpleList());
    }

    @GetMapping("/{id}")
    public Result<CoachVO> detail(@PathVariable Long id) {
        return Result.success(coachService.getById(id));
    }

    /** 新增教练 — 仅管理员 */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<CoachVO> create(@Valid @RequestBody CoachCreateDTO dto) {
        return Result.success("新增教练成功", coachService.create(dto));
    }

    /** 修改教练 — 仅管理员 */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<CoachVO> update(@PathVariable Long id, @Valid @RequestBody CoachUpdateDTO dto) {
        return Result.success("修改教练成功", coachService.update(id, dto));
    }

    /** 修改教练状态 — 仅管理员 */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        coachService.updateStatus(id, body.get("status"));
        return Result.success("状态修改成功");
    }
}
