package com.example.healthcheck;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class HealthCheckApplication {
    public static void main(String[] args) {
        SpringApplication.run(HealthCheckApplication.class, args);
    }
}