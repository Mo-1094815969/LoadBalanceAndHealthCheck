package com.example.healthcheck.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Data
@Configuration
@ConfigurationProperties(prefix = "banks")
public class BankUrlConfig {
    private Map<String, BankConfig> configs = new HashMap<>();

    @Data
    public static class BankConfig {
        private String bankId;
        private String bankName;
        private List<UrlConfig> urls = new ArrayList<>(); // 改为URL配置列表
        private boolean critical = false; // 是否关键资方

//        // 获取所有URL字符串
//        public List<String> getUrlStrings() {
//            return urls.stream().map(UrlConfig::getUrl).collect(Collectors.toList());
//        }
    }

    @Data
    public static class UrlConfig {
        private String url;
        private int weight = 1; // URL级别的权重
    }
}