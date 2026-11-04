package com.box.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@EnableDiscoveryClient
@ComponentScan(basePackages = {"com.box.gateway", "com.box.common"})
public class GatewayApplication {
    static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}