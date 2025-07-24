package com.example.healthcheck.service.lbstrategy;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;


@Component
public class RoundRobinStrategy implements LoadBalanceStrategy {
    private final AtomicInteger index = new AtomicInteger(0);

    @Override
    public String chooseUrl(List<String> urls) {
        if (urls.isEmpty()) return null;
        int idx = index.getAndUpdate(i -> (i + 1) % urls.size());
        return urls.get(idx);
    }
}