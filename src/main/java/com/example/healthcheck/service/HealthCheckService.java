package com.example.healthcheck.service;

public interface HealthCheckService {

    void performHealthChecks();

    void checkRemovedUrlsForRecovery();

}