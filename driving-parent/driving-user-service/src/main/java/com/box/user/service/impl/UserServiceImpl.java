package com.box.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.box.api.coach.CoachDTO;
import com.box.api.coach.CoachFeignClient;
import com.box.api.user.UserDTO;
import com.box.common.exception.BusinessException;
import com.box.common.result.Result;
import com.box.user.dto.ResetPasswordDTO;
import com.box.user.dto.UserCreateDTO;
import com.box.user.dto.UserQueryDTO;
import com.box.user.entity.Student;
import com.box.user.entity.SysUser;
import com.box.user.mapper.StudentMapper;
import com.box.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import com.box.user.service.UserService;
import com.box.user.vo.UserVO;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 账号管理实现
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final SysUserMapper sysUserMapper;
    private final StudentMapper studentMapper;
    private final PasswordEncoder passwordEncoder;
    private final CoachFeignClient coachFeignClient;

    @Override
    public Page<UserVO> queryPage(UserQueryDTO dto) {
        LambdaQueryWrapper<SysUser> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(dto.getKeyword())) {
            w.like(SysUser::getUsername, dto.getKeyword());
        }
        if (StringUtils.hasText(dto.getRole())) w.eq(SysUser::getRole, dto.getRole());
        if (StringUtils.hasText(dto.getStatus())) w.eq(SysUser::getStatus, dto.getStatus());
        w.orderByAsc(SysUser::getRole).orderByAsc(SysUser::getId);

        Page<SysUser> page = sysUserMapper.selectPage(new Page<>(dto.getPage(), dto.getSize()), w);
        Page<UserVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(this::toVO).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVO create(UserCreateDTO dto) {
        if (!List.of("COACH", "STUDENT").contains(dto.getRole())) {
            throw new BusinessException("只能创建教练或学员账号");
        }

        // 校验 username 不重复
        Long count = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, dto.getUsername()));
        if (count > 0) throw new BusinessException("该账号已存在");

        // 校验绑定对象
        if ("COACH".equals(dto.getRole())) {
            if (dto.getRelatedId() == null) throw new BusinessException("教练账号必须绑定教练ID");
            Result<CoachDTO> coachDTO = coachFeignClient.getCoachById(dto.getRelatedId());
            if (coachDTO == null) throw new BusinessException("绑定的教练不存在");
            // 一个教练只能有一个账号
            Long exist = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getRole, "COACH").eq(SysUser::getRelatedId, dto.getRelatedId()));
            if (exist > 0) throw new BusinessException("该教练已有账号");
        } else {
            if (dto.getRelatedId() == null) throw new BusinessException("学员账号必须绑定学员ID");
            Student student = studentMapper.selectById(dto.getRelatedId());
            if (student == null) throw new BusinessException("绑定的学员不存在");
            Long exist = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getRole, "STUDENT").eq(SysUser::getRelatedId, dto.getRelatedId()));
            if (exist > 0) throw new BusinessException("该学员已有账号");
        }

        SysUser user = new SysUser();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(StringUtils.hasText(dto.getPassword()) ? dto.getPassword() : "123456"));
        user.setRole(dto.getRole());
        user.setRelatedId(dto.getRelatedId());
        user.setStatus("ENABLE");
        sysUserMapper.insert(user);

        UserVO vo = toVO(user);
        vo.setRelatedName(getRelatedName(user));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String resetPassword(Long userId, ResetPasswordDTO dto) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) throw new BusinessException("账号不存在");
        String newPwd = StringUtils.hasText(dto.getNewPassword()) ? dto.getNewPassword() : "123456";
        user.setPassword(passwordEncoder.encode(newPwd));
        sysUserMapper.updateById(user);
        return newPwd;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long userId, String status) {
        if (!List.of("ENABLE", "DISABLE").contains(status)) throw new BusinessException("无效状态");
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) throw new BusinessException("账号不存在");
        if ("ADMIN".equals(user.getRole())) throw new BusinessException("不能停用管理员账号");
        user.setStatus(status);
        sysUserMapper.updateById(user);
    }

    @Override
    public UserDTO getByUsername(String username) {
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        UserDTO userDTO = new UserDTO();
        BeanUtils.copyProperties(user, userDTO);
        return userDTO;
    }

    @Override
    public UserDTO getById(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        UserDTO userDTO = new UserDTO();
        BeanUtils.copyProperties(user, userDTO);
        return userDTO;
    }

    private UserVO toVO(SysUser u) {
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(u, vo);
        vo.setRelatedName(getRelatedName(u));
        return vo;
    }

    private String getRelatedName(SysUser u) {
        if (u.getRelatedId() == null) return null;
        if ("COACH".equals(u.getRole())) {
            Result<CoachDTO> r = coachFeignClient.getCoachById(u.getRelatedId());
            if (r == null || r.getData() == null) return null;
            return r.getData().getName();
        }
        if ("STUDENT".equals(u.getRole())) {
            Student s = studentMapper.selectById(u.getRelatedId());
            return s != null ? s.getName() : null;
        }
        return null;
    }
}
