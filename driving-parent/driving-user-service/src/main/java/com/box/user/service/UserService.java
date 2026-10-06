package com.box.user.service;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.box.api.user.UserDTO;
import com.box.common.result.Result;
import com.box.user.dto.ResetPasswordDTO;
import com.box.user.dto.UserCreateDTO;
import com.box.user.dto.UserQueryDTO;
import com.box.user.vo.UserVO;

public interface UserService {
    Page<UserVO> queryPage(UserQueryDTO dto);
    UserVO create(UserCreateDTO dto);
    String resetPassword(Long userId, ResetPasswordDTO dto);
    void updateStatus(Long userId, String status);
    UserDTO getByUsername(String username);
    UserDTO getById(Long userId);
}
