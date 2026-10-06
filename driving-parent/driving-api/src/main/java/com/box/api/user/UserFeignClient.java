package com.box.api.user;

import com.box.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "driving-user-service")
public interface UserFeignClient {

    @GetMapping("/internal/user/by-username/{username}")
    Result<UserDTO> getByUsername(@PathVariable("username") String username);

    @GetMapping("/internal/user/by-id/{userId}")
    Result<UserDTO> getById(@PathVariable("userId") Long userId);
}