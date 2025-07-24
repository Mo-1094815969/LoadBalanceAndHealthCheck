package com.example.healthcheck.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// BankUrlConfig.java - 资方URL配置
@Data
@Configuration
@ConfigurationProperties(prefix = "banks")
public class BankUrlConfig {
    private Map<String, BankConfig> configs = new HashMap<>();
    
    @Data
    public static class BankConfig {
        private String bankId;
        private String bankName;
        private List<String> urls = new ArrayList<>();
        private int weight = 1; // 负载均衡权重
        private boolean critical = false; // 是否关键资方
    }
}