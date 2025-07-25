package com.example.healthcheck.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@ComponentScan(basePackages = "com.example.healthcheck")
@EnableScheduling
public class CommonAutoConfiguration {
}
