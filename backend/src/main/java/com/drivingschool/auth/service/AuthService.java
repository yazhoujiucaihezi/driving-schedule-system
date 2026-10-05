package com.drivingschool.auth.service;

import com.drivingschool.auth.dto.LoginRequestDTO;
import com.drivingschool.auth.vo.LoginResponseVO;
import com.drivingschool.auth.vo.UserInfoVO;

/**
 * 认证业务接口
 * <p>
 * M2 阶段承担登录校验、Token 生成、用户信息查询职责。
 * 不包含注册功能（系统管理员在后台直接创建账号）。
 * </p>
 */
public interface AuthService {

    /** 用户登录：校验账号密码并生成 JWT Token */
    LoginResponseVO login(LoginRequestDTO dto);

    /** 查询当前登录用户的信息 */
    UserInfoVO getCurrentUser(Long userId);
}
