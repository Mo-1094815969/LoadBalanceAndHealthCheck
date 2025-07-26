package com.example.healthcheck.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Data
@Configuration
@ConfigurationProperties(prefix = "banks")
public class BankUrlConfig {
    private CommonConfig common;
    private Map<String, BankConfig> configs = new HashMap<>();

    @Data
    public static class CommonConfig {
        private List<String> baseUrls;
    }

    @Data
    public static class BankConfig {
        private String bankId;
        private String bankName;
        private String path; // 新增：资方专属路径
        private List<UrlConfig> urls = new ArrayList<>();
        private List<Integer> weights; // 新增：权重列表

        // 获取所有URL字符串
        public List<String> getUrlStrings() {
            return urls.stream().map(UrlConfig::getUrl).collect(Collectors.toList());
        }
    }

    @Data
    public static class UrlConfig {
        private String url;
        private int weight = 1; // URL级别的权重
    }

    @PostConstruct
    public void init() {
        configs.forEach((bankId, config) -> {
            config.setBankId(bankId);

            // 如果已手动配置URLs，跳过自动生成
            if (!CollectionUtils.isEmpty(config.getUrls())) {
                return;
            }

            // 确保path不为空
            if (StringUtils.isEmpty(config.getPath())) {
                throw new IllegalArgumentException("资方 " + bankId + " 缺少路径配置");
            }

            // 获取权重配置（默认为1）
            List<Integer> weights = Optional.ofNullable(config.getWeights())
                    .orElseGet(() -> Collections.nCopies(common.getBaseUrls().size(), 1));

            // 动态生成URLs
            config.setUrls(IntStream.range(0, common.getBaseUrls().size())
                    .mapToObj(i -> {
                        UrlConfig urlConfig = new UrlConfig();
                        urlConfig.setUrl(UriComponentsBuilder
                                .fromHttpUrl(common.getBaseUrls().get(i))
                                .path(config.getPath())
                                .toUriString());
                        urlConfig.setWeight(i < weights.size() ? weights.get(i) : 1);
                        return urlConfig;
                    })
                    .collect(Collectors.toList()));
        });
    }
}