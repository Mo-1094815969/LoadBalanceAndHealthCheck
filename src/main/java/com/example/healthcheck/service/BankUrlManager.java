package com.example.healthcheck.service;

import com.example.healthcheck.config.BankUrlConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class BankUrlManager {

    private final BankUrlConfig bankUrlConfig;
    private final Map<String, String> urlToBankIdMap = new HashMap<>();
    private final Map<String, BankUrlConfig.BankConfig> bankConfigMap = new HashMap<>();

    @Autowired
    public BankUrlManager(BankUrlConfig bankUrlConfig) {
        this.bankUrlConfig = bankUrlConfig;
        initializeMappings();
    }

    private void initializeMappings() {
        bankUrlConfig.getConfigs().forEach((bankId, config) -> {
            bankConfigMap.put(bankId, config);
            config.getUrls().forEach(url -> urlToBankIdMap.put(url, bankId));
        });
    }

    public String getBankIdForUrl(String url) {
        return urlToBankIdMap.get(url);
    }

    public BankUrlConfig.BankConfig getBankConfig(String bankId) {
        return bankConfigMap.get(bankId);
    }

    public Map<String, BankUrlConfig.BankConfig> getAllBankConfigs() {
        return Collections.unmodifiableMap(bankConfigMap);
    }
}