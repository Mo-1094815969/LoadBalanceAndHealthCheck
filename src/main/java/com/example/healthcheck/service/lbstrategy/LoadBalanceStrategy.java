package com.example.healthcheck.service.lbstrategy;

import java.util.List;

public interface LoadBalanceStrategy {
    String chooseUrl(List<String> urls);
}