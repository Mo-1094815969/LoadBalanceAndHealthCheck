package com.example.healthcheck.service;

import com.example.healthcheck.dto.HealthCheckResult;
import com.example.healthcheck.service.lbstrategy.LoadBalanceStrategy;
import com.example.healthcheck.service.lbstrategy.RandomStrategy;
import com.example.healthcheck.service.lbstrategy.RoundRobinStrategy;
import com.example.healthcheck.service.lbstrategy.WeightedRoundRobinStrategy;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@RefreshScope
public class LoadBalancerService {

    @Value("${health.check.consider-http-errors-healthy}")
    private boolean considerHttpErrorsHealthy;

    @Value("${loadbalancer.strategy}")
    private String strategyType;

    private static final Logger logger = LoggerFactory.getLogger(LoadBalancerService.class);
    private final BankUrlManager bankUrlManager;
    private final Map<String, List<String>> healthyBankUrls = new ConcurrentHashMap<>();

    private LoadBalanceStrategy strategy;
    // 新增三个策略的引用字段
    private final RoundRobinStrategy roundRobinStrategy;
    private final RandomStrategy randomStrategy;
    private final WeightedRoundRobinStrategy weightedStrategy;

    // 新增初始化方法
    @PostConstruct
    public void initStrategy() {
        // 确保配置已注入后再选择策略
        switch (strategyType.toLowerCase()) {
            case "random":
                this.strategy = randomStrategy;
                break;
            case "weighted":
                this.strategy = weightedStrategy;
                break;
            case "round-robin":
            default:
                this.strategy = roundRobinStrategy;
        }
        logger.info("使用负载均衡策略: {}", strategyType);
    }

    @Autowired
    public LoadBalancerService(BankUrlManager bankUrlManager, RoundRobinStrategy roundRobinStrategy, RandomStrategy randomStrategy, WeightedRoundRobinStrategy weightedStrategy) {
        this.bankUrlManager = bankUrlManager;
        this.roundRobinStrategy = roundRobinStrategy;
        this.randomStrategy = randomStrategy;
        this.weightedStrategy = weightedStrategy;
        initializeBankStructures();
    }

    private void initializeBankStructures() {
        bankUrlManager.getAllBankConfigs().keySet().forEach(bankId -> {
            healthyBankUrls.put(bankId, new CopyOnWriteArrayList<>());
        });
    }

    /**
     * 获取指定资方的下一个健康URL
     */
    public String getNextHealthyUrlForBank(String bankId) {
        List<String> urls = healthyBankUrls.get(bankId);
        if (urls == null || urls.isEmpty()) {
            return null;
        }

        return strategy.chooseUrl(urls);
    }

    /**
     * 更新健康URL列表（按资方分组）
     */
    public void updateHealthyUrls(List<String> activeUrls, Map<String, HealthCheckResult> latestResults) {
        // 按资方分组健康URL
        Map<String, List<String>> newHealthyUrls = new ConcurrentHashMap<>();

        activeUrls.forEach(url -> {
            HealthCheckResult result = latestResults.get(url);
            if (result == null) return;

            String bankId = bankUrlManager.getBankIdForUrl(url);
            if (bankId == null) return;

            boolean isHealthy = considerHttpErrorsHealthy ||
                    ("UP".equals(result.getStatus()) ||
                            ("ERROR".equals(result.getStatus()) && !result.isTrulyUnavailable()));

            if (isHealthy) {
                newHealthyUrls.computeIfAbsent(bankId, k -> new CopyOnWriteArrayList<>()).add(url);
            }
        });

        // 更新资方健康URL列表
        newHealthyUrls.forEach((bankId, urls) -> {
            List<String> currentList = healthyBankUrls.get(bankId);
            currentList.clear();
            currentList.addAll(urls);
        });

        logger.info("按资方更新的健康URLs: {}", newHealthyUrls);
    }

}