package com.example.healthcheck.service.lbstrategy;

import com.example.healthcheck.service.BankUrlManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class WeightedRoundRobinStrategy implements LoadBalanceStrategy {
    private final BankUrlManager bankUrlManager;

    // 存储每个资方的权重状态
    private final Map<String, Map<String, AtomicInteger>> currentWeights = new ConcurrentHashMap<>();
    private final Map<String, Map<String, AtomicInteger>> effectiveWeights = new ConcurrentHashMap<>();

    @Autowired
    public WeightedRoundRobinStrategy(BankUrlManager bankUrlManager) {
        this.bankUrlManager = bankUrlManager;
    }

    @Override
    public String chooseUrl(List<String> urls) {
        if (urls.isEmpty()) return null;

        // 获取第一个URL的资方ID（假设同一个资方）
        String bankId = bankUrlManager.getBankIdForUrl(urls.get(0));
        if (bankId == null) {
            // 如果没有资方ID，使用简单轮询
            return new RoundRobinStrategy().chooseUrl(urls);
        }

        // 初始化资方的权重状态
        initializeBankWeights(bankId, urls);

        Map<String, AtomicInteger> bankCurrentWeights = currentWeights.get(bankId);
        Map<String, AtomicInteger> bankEffectiveWeights = effectiveWeights.get(bankId);

        // 计算总权重
        int totalWeight = urls.stream()
                .mapToInt(url -> bankEffectiveWeights.get(url).get())
                .sum();

        String selectedUrl = null;
        int maxWeight = Integer.MIN_VALUE;

        // 选择当前权重最大的URL
        for (String url : urls) {
            int current = bankCurrentWeights.get(url).addAndGet(
                    bankEffectiveWeights.get(url).get());

            if (current > maxWeight) {
                maxWeight = current;
                selectedUrl = url;
            }
        }

        // 更新选中URL的当前权重
        if (selectedUrl != null) {
            bankCurrentWeights.get(selectedUrl).addAndGet(-totalWeight);
        }

        return selectedUrl;
    }

    private void initializeBankWeights(String bankId, List<String> urls) {
        currentWeights.computeIfAbsent(bankId, k -> new ConcurrentHashMap<>());
        effectiveWeights.computeIfAbsent(bankId, k -> new ConcurrentHashMap<>());

        Map<String, AtomicInteger> bankCurrentWeights = currentWeights.get(bankId);
        Map<String, AtomicInteger> bankEffectiveWeights = effectiveWeights.get(bankId);

        for (String url : urls) {
            bankCurrentWeights.computeIfAbsent(url, k -> new AtomicInteger(0));
            bankEffectiveWeights.computeIfAbsent(url, k ->
                    new AtomicInteger(bankUrlManager.getUrlWeight(url)));
        }
    }
}