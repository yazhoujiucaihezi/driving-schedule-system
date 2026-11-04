package com.box.api.fallback;

import com.box.api.user.UserDTO;
import com.box.api.user.UserFeignClient;
import com.box.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 账号服务降级兜底
 * <p>
 * 关键：登录链路上必须能区分「账号服务不可用」与「用户不存在」。
 * 这里统一返回 code=503，AuthServiceImpl 据此给出「账号服务暂时不可用」而不是「用户名或密码错误」。
 * </p>
 */
@Slf4j
@Component
public class UserFeignClientFallbackFactory implements FallbackFactory<UserFeignClient> {

    @Override
    public UserFeignClient create(Throwable cause) {
        log.warn("调用账号服务（driving-user-service）失败，触发降级", cause);
        return new UserFeignClient() {

            @Override
            public Result<UserDTO> getByUsername(String username) {
                log.error("降级：按用户名查询账号失败。用户名: {}", username);
                return Result.error(503, "账号服务暂时不可用，请稍后再试");
            }

            @Override
            public Result<UserDTO> getById(Long userId) {
                log.error("降级：按ID查询账号失败。用户ID: {}", userId);
                return Result.error(503, "账号服务暂时不可用，请稍后再试");
            }
        };
    }
}
