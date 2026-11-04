package com.box.common.web.interceptor;

import com.box.common.utils.JwtUserDetails;
import com.box.common.utils.SecurityContextUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 用户上下文拦截器 —— 从请求头还原登录身份
 * <p>
 * 网关验签通过后会把身份写入 X-User-* 请求头，本拦截器负责把它还原成
 * SecurityContext 中的认证对象，供后续 Controller / Service 读取。
 * </p>
 */
@Component
public class UserContextInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
        String userId = request.getHeader("X-User-Id");
        String role = request.getHeader("X-User-Role");
        String relatedId = request.getHeader("X-User-RelatedId");
        if (userId != null && role != null){
            JwtUserDetails jwtUserDetails = new JwtUserDetails(
                    Long.valueOf(userId),
                    null,
                    role,
                    relatedId == null ? null : Long.valueOf(relatedId));
            SecurityContextUtils.setAuthentication(jwtUserDetails);
        }
        return true;
    }
}
