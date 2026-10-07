package com.box.vehicle;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@SpringBootApplication
@EnableMethodSecurity
@EnableFeignClients(basePackages = "com.box.api")
public class VehicleServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(VehicleServiceApplication.class, args);
    }
}
