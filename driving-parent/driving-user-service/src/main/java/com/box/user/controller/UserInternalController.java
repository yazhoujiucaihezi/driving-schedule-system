package com.box.user.controller;

import com.box.api.user.UserDTO;
import com.box.common.result.Result;
import com.box.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/user")
@RequiredArgsConstructor
public class UserInternalController {

    private final UserService userService;

    @GetMapping("/by-username/{username}")
    Result<UserDTO> getByUsername(@PathVariable("username") String username){
        UserDTO dto = userService.getByUsername(username);
        return Result.success(dto);
    }

    @GetMapping("/by-id/{userId}")
    Result<UserDTO> getById(@PathVariable("userId") Long userId){
        UserDTO dto = userService.getById(userId);
        return Result.success(dto);
    }
}
