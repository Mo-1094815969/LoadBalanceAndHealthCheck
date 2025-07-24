package com.example.healthcheck.service.lbstrategy;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class RandomStrategy implements LoadBalanceStrategy {
    @Override
    public String chooseUrl(List<String> urls) {
        if (urls.isEmpty()) return null;
        int idx = ThreadLocalRandom.current().nextInt(urls.size());
        return urls.get(idx);
    }
}