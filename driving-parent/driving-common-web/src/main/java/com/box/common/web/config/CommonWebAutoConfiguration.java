package com.box.common.web.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan("com.box.common.web")   // 扫这个包下的所有 Bean
public class CommonWebAutoConfiguration {

}