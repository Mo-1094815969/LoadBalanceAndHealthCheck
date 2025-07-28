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
public class RemovedUrlsForRecoveryScheduler implements SchedulingConfigurer {

    private final HealthCheckService healthCheckService;

    @Value("${health.check.recovery-interval}")
    private long recoveryInterval;// 恢复检测间隔

    public RemovedUrlsForRecoveryScheduler(HealthCheckService healthCheckService) {
        this.healthCheckService = healthCheckService;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.setScheduler(Executors.newScheduledThreadPool(5));
        taskRegistrar.addTriggerTask(
                healthCheckService::checkRemovedUrlsForRecovery,
                triggerContext -> {
                    PeriodicTrigger trigger = new PeriodicTrigger(recoveryInterval, TimeUnit.SECONDS);
                    trigger.setInitialDelay(recoveryInterval);// 延迟检查
                    return trigger.nextExecutionTime(triggerContext);
                }
        );
    }
}