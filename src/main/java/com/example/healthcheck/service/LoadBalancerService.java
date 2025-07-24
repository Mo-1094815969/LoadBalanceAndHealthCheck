package com.example.healthcheck.service;

import com.example.healthcheck.dto.HealthCheckResult;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class LoadBalancerService {

    @Value("${health.check.consider-http-errors-healthy}")
    private boolean considerHttpErrorsHealthy;

    private static final Logger logger = LoggerFactory.getLogger(LoadBalancerService.class);
//    private final CopyOnWriteArrayList<String> healthyUrls = new CopyOnWriteArrayList<>();
//    private final AtomicInteger currentIndex = new AtomicInteger(0);

    private final BankUrlManager bankUrlManager;
    private final Map<String, AtomicInteger> bankIndexMap = new ConcurrentHashMap<>();
    private final Map<String, List<String>> healthyBankUrls = new ConcurrentHashMap<>();

    @Autowired
    public LoadBalancerService(BankUrlManager bankUrlManager) {
        this.bankUrlManager = bankUrlManager;
        initializeBankStructures();
    }

    private void initializeBankStructures() {
        bankUrlManager.getAllBankConfigs().keySet().forEach(bankId -> {
            bankIndexMap.put(bankId, new AtomicInteger(0));
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

        int index = bankIndexMap.get(bankId).getAndUpdate(i -> (i + 1) % urls.size());
        return urls.get(index);
    }

    /**
     * 更新健康URL列表（按资方分组）
     */
    public void updateHealthyUrls(List<String> activeUrls, Map<String, HealthCheckResult> latestResults) {
        // 按资方分组健康URL
        Map<String, List<String>> newHealthyUrls = new HashMap<>();

        activeUrls.forEach(url -> {
            HealthCheckResult result = latestResults.get(url);
            if (result == null) return;

            String bankId = bankUrlManager.getBankIdForUrl(url);
            if (bankId == null) return;

            boolean isHealthy = considerHttpErrorsHealthy ||
                    ("UP".equals(result.getStatus()) ||
                            ("ERROR".equals(result.getStatus()) && !result.isTrulyUnavailable()));

            if (isHealthy) {
                newHealthyUrls.computeIfAbsent(bankId, k -> new ArrayList<>()).add(url);
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