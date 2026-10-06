package com.box.common.web.config;

import com.box.common.web.interceptor.UserContextInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({SecurityConfig.class, WebConfig.class, UserContextInterceptor.class})
public class CommonWebAutoConfiguration {

}