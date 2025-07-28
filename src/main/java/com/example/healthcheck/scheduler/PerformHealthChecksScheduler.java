package com.example.healthcheck.scheduler;

import com.example.healthcheck.service.HealthCheckService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.PeriodicTrigger;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Component
@RefreshScope
public class PerformHealthChecksScheduler implements SchedulingConfigurer {

    private final HealthCheckService healthCheckService;

    @Value("${health.check.interval}")
    private long interval;// 检测间隔

    public PerformHealthChecksScheduler(HealthCheckService healthCheckService) {
        this.healthCheckService = healthCheckService;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.setScheduler(Executors.newScheduledThreadPool(5));
        taskRegistrar.addTriggerTask(
                healthCheckService::performHealthChecks,
                triggerContext -> {
                    PeriodicTrigger trigger = new PeriodicTrigger(interval, TimeUnit.SECONDS);
                    return trigger.nextExecutionTime(triggerContext);
                }
        );
    }
}
