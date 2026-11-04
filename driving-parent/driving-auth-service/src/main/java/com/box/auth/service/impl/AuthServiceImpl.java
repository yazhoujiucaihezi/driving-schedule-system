package com.box.auth.service.impl;
import com.box.api.user.UserDTO;
import com.box.api.user.UserFeignClient;
import com.box.auth.dto.LoginRequestDTO;
import com.box.auth.service.AuthService;
import com.box.auth.vo.LoginResponseVO;
import com.box.auth.vo.UserInfoVO;
import com.box.common.exception.BusinessException;
import com.box.common.result.Result;
import com.box.common.utils.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 认证业务实现
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final PasswordEncoder passwordEncoder;
    private final UserFeignClient userFeignClient;
    private final JwtUtils jwtUtils;

    /**
     * 用户登录
     * <p>
     * 验证流程：
     * 1. 根据 username 查询 sys_user 表；
     * 2. 校验账号是否存在、是否被禁用；
     * 3. 使用 Bcrypt 校验密码是否匹配；
     * 4. 生成 JWT Token 并返回。
     * </p>
     *
     * @param dto 登录请求（username + password）
     * @return LoginResponseVO（token + 用户信息）
     */
    @Override
    public LoginResponseVO login(LoginRequestDTO dto) {


        Result<UserDTO> result = userFeignClient.getByUsername(dto.getUsername());
        if (result == null || result.getCode() == 503){
            throw new BusinessException(503,"账号服务暂时不可用，请稍后重试");
        }

        if (result.getCode() != 200) {
            throw new BusinessException(result.getCode(), result.getMessage());
        }

        UserDTO user =result.getData();
        if (user == null || !StringUtils.hasText(dto.getPassword()) || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BusinessException("用户不存在或密码错误");
        }

        if ("DISABLE".equals(user.getStatus())) {
                throw new BusinessException("用户被禁用");
        }

        String token = jwtUtils.generateToken(user.getId(), user.getUsername(), user.getRole(), user.getRelatedId());
        return new LoginResponseVO(token,user.getId(),user.getUsername(),user.getRole(),user.getRelatedId());

    }

    /**
     * 查询当前登录用户的信息
     * <p>
     * 从 SecurityContext 中获取当前用户 ID，然后从数据库查询最新状态。
     * 这让前端可以在刷新页面后验证 Token 是否仍然有效。
     * </p>
     *
     * @param userId 当前登录用户ID（从 Token 中解析）
     */
    @Override
    public UserInfoVO getCurrentUser(Long userId) {
//        SysUser user = sysUserMapper.selectById(userId);
        Result<UserDTO> result = userFeignClient.getById(userId);
        UserDTO user = result.getData();


        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        return UserInfoVO.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .role(user.getRole())
                .relatedId(user.getRelatedId())
                .status(user.getStatus())
                .build();
    }
}
