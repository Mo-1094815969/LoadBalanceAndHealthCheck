package com.example.healthcheck.scheduler;

import com.example.healthcheck.service.HealthCheckService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.PeriodicTrigger;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class RemovedUrlsForRecoveryScheduler implements SchedulingConfigurer {

    private final HealthCheckService healthCheckService;

    private final ThreadPoolTaskScheduler threadPoolTaskScheduler;

    @Value("${health.check.recovery-interval}")
    private long recoveryInterval;// 恢复检测间隔

    public RemovedUrlsForRecoveryScheduler(HealthCheckService healthCheckService, ThreadPoolTaskScheduler threadPoolTaskScheduler) {
        this.healthCheckService = healthCheckService;
        this.threadPoolTaskScheduler = threadPoolTaskScheduler;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.setScheduler(threadPoolTaskScheduler);
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