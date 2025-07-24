package com.example.healthcheck.service.impl;

import com.example.healthcheck.dto.HealthCheckResult;
import com.example.healthcheck.service.BankUrlManager;
import com.example.healthcheck.service.HealthCheckService;
import com.example.healthcheck.service.LoadBalancerService;
import com.example.healthcheck.utils.DateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Service
public class HealthCheckServiceImpl implements HealthCheckService {

    @Value("${health.check.consider-http-errors-healthy}")
    private boolean considerHttpErrorsHealthy;

    @Value("${health.check.allow-partial-success}")
    private boolean allowPartialSuccess;

    private static final Logger logger = LoggerFactory.getLogger(HealthCheckServiceImpl.class);
    private final RestTemplate restTemplate;
    private final CopyOnWriteArrayList<String> activeUrls; // 当前监控的URL列表
    private final Map<String, HealthCheckResult> latestResults = new ConcurrentHashMap<>();
    private final List<HealthCheckResult> allResults = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, Integer> failureCounts = new ConcurrentHashMap<>(); // 失败计数器
    private final List<String> removedUrls = Collections.synchronizedList(new ArrayList<>()); // 被剔除的URL

    // 配置参数
    private final int maxFailureThreshold;
    private final int recoveryCheckInterval;
    private final int recoverySuccessThreshold;
    private final LoadBalancerService loadBalancerService;
    private final BankUrlManager bankUrlManager;

    public HealthCheckServiceImpl(RestTemplate restTemplate,
                                  CopyOnWriteArrayList<String> activeUrls,
                                  @Value("${health.check.max-failures}") int maxFailureThreshold,
                                  @Value("${health.check.recovery-interval}") int recoveryCheckInterval,
                                  @Value("${health.check.recovery-threshold}") int recoverySuccessThreshold,
                                  LoadBalancerService loadBalancerService,
                                  BankUrlManager bankUrlManager) {
        this.restTemplate = restTemplate;
        this.activeUrls = activeUrls;
        this.maxFailureThreshold = maxFailureThreshold;
        this.recoveryCheckInterval = recoveryCheckInterval;
        this.recoverySuccessThreshold = recoverySuccessThreshold;
        this.loadBalancerService = loadBalancerService;
        this.bankUrlManager = bankUrlManager;
    }

    @Scheduled(fixedRateString = "${health.check.interval}") // 每30秒执行一次
    @Override
    public void performHealthChecks() {
        logger.info("\n[健康检查] 开始健康检查 {}", DateUtil.nowFormat());
        logger.info("当前可用链接URLs: {}", activeUrls);

        List<String> urlsToCheck = new ArrayList<>(activeUrls);

        urlsToCheck.parallelStream().forEach(url -> {
            HealthCheckResult result = checkSingleUrl(url);
            latestResults.put(url, result);
            allResults.add(result);
            logger.info(result.toLogString());
            handleFailureCount(url, result);
        });

        // 更新负载均衡器的健康URL列表 - 传递最新结果
        loadBalancerService.updateHealthyUrls(activeUrls, latestResults);

        logger.info("[健康检查] 健康检查完成 {}", DateUtil.nowFormat());
        logger.info("移除URLs: {}", removedUrls);
    }

    // 新增：每5分钟检查被移除的URL是否恢复
    @Scheduled(fixedRateString = "${health.check.recovery-interval}")
    public void checkRemovedUrlsForRecovery() {
        if (removedUrls.isEmpty()) {
            return;
        }

        logger.info("\n[恢复检测] 开始恢复检测: {}", DateUtil.nowFormat());
        logger.info("检测移除的URLs: {}", removedUrls);

        // 创建被移除URL列表的快照
        List<String> urlsToCheck = new ArrayList<>(removedUrls);

        urlsToCheck.forEach(url -> {
            HealthCheckResult result = checkSingleUrl(url);
            logger.info("[恢复]: {}", result.toLogString());

            // 如果检测成功，尝试恢复URL
            if ("UP".equals(result.getStatus())) {
                // 增加恢复成功计数
                int recoveryCount = failureCounts.getOrDefault(url + "_recovery", 0) + 1;
                failureCounts.put(url + "_recovery", recoveryCount);

                // 达到恢复阈值则重新加入监控
                if (recoveryCount >= recoverySuccessThreshold) {
                    if (removedUrls.remove(url)) {
                        activeUrls.add(url);
                        failureCounts.remove(url); // 重置失败计数
                        failureCounts.remove(url + "_recovery"); // 重置恢复计数
                        logger.info("✅ 已将URL恢复至检测列表: {}", result.toLogString());
                    }
                }
            } else {
                // 检测失败则重置恢复计数
                failureCounts.remove(url + "_recovery");
            }
        });
        logger.info("[恢复检测] 完成恢复检测: {}", DateUtil.nowFormat());
    }

    // 处理失败计数和自动剔除逻辑 - 优化版
    private void handleFailureCount(String url, HealthCheckResult result) {
        String bankId = bankUrlManager.getBankIdForUrl(url);
        String bankName = bankUrlManager.getBankConfig(bankId).getBankName();
        if (bankId == null) {
            logger.warn("URL {} 未关联到任何资方", url);
            return;
        }

        // 只对连接问题（DOWN状态）进行失败计数
        if ("DOWN".equals(result.getStatus())) {
            // 增加失败计数
            int count = failureCounts.getOrDefault(url, 0) + 1;
            failureCounts.put(url, count);

            // 超过阈值则剔除
            if (count >= maxFailureThreshold) {
                if (activeUrls.remove(url)) {
                    removedUrls.add(url);
                    failureCounts.remove(url); // 重置计数器
                    logger.info("⚠️ 已将URL从检测列表中移除: {} (资方: {})", url, bankName);

                    // 记录详细原因
                    logger.debug("移除原因: {}", result.getMessage());
                }
            }
        } else {
            // 成功时重置失败计数
            failureCounts.remove(url);
        }
    }

    @Override
    public HealthCheckResult checkSingleUrl(String url) {
        long startTime = System.currentTimeMillis();
        String status = "DOWN";
        int statusCode = 0;
        String message = "";
        boolean isConnectionIssue = false;

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    String.class
            );

            HttpStatus httpStatus = response.getStatusCode();
            statusCode = httpStatus.value();

            // 优化状态判断逻辑
            if (isSuccessStatusCode(httpStatus)) {
                status = "UP";
                message = httpStatus.getReasonPhrase();
            } else if (considerHttpErrorsHealthy) {
                status = "UP"; // 即使HTTP错误也视为健康
                message = "HTTP ERROR: " + httpStatus.getReasonPhrase();
            } else {
                status = "ERROR";
                message = httpStatus.getReasonPhrase();
            }

        } catch (ResourceAccessException e) {
            status = "DOWN";
            message = analyzeException(e);
            isConnectionIssue = true;
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            statusCode = e.getRawStatusCode();
            if (considerHttpErrorsHealthy) {
                status = "UP"; // 即使HTTP错误也视为健康
                message = "HTTP ERROR: " + e.getStatusText();
            } else {
                status = "ERROR";
                message = e.getStatusText();
            }
        } catch (Exception e) {
            status = "UNKNOWN";
            message = e.getMessage();
        }

        long responseTime = System.currentTimeMillis() - startTime;
        return new HealthCheckResult(url, status, statusCode, message, responseTime, isConnectionIssue);
    }

    private String analyzeException(ResourceAccessException e) {
        Throwable cause = e.getCause();

        if (cause instanceof java.net.ConnectException) {
            return "连接被拒绝: 服务未启动或端口关闭";
        } else if (cause instanceof java.net.SocketTimeoutException) {
            return "连接超时: 防火墙拦截或网络问题";
        } else if (cause instanceof java.net.UnknownHostException) {
            return "未知主机: 域名解析失败";
        }
        return "连接失败: " + e.getMessage();
    }

    // 判断HTTP状态码是否成功
    private boolean isSuccessStatusCode(HttpStatus status) {
        if (allowPartialSuccess) {
            // 允许部分成功状态码（如206）
            return status.is2xxSuccessful() || status.value() == 206;
        }
        // 仅200-299视为成功
        return status.is2xxSuccessful();
    }
}