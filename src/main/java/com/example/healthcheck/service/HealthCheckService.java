package com.example.healthcheck.service;

import com.example.healthcheck.dto.HealthCheckResult;

import java.util.List;
import java.util.Map;

public interface HealthCheckService {

    void performHealthChecks();

    void checkRemovedUrlsForRecovery();

    HealthCheckResult checkSingleUrl(String url);

}