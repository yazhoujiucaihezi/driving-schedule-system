package com.box.common.web.utils;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class UserContext {

    public static Long getRelatedId() {
        return getLong();
    }

    private static Long getLong() {
        String v = getHeader();
        return v == null ? null : Long.valueOf(v);
    }

    private static String getHeader() {
        ServletRequestAttributes a =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (a == null) return null;
        HttpServletRequest req = a.getRequest();
        return req.getHeader("X-User-RelatedId");
    }
}