package com.example.healthcheck.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class HealthCheckResult {
    private String url;
    private String status;
    private int statusCode;
    private String message;
    private long responseTime; // 毫秒
    private boolean connectionIssue; // 是否为连接问题
    private LocalDateTime timestamp = LocalDateTime.now();

    public HealthCheckResult(String url, String status, int statusCode,
                             String message, long responseTime, boolean connectionIssue) {
        this.url = url;
        this.status = status;
        this.statusCode = statusCode;
        this.message = message;
        this.responseTime = responseTime;
        this.connectionIssue = connectionIssue;
    }

    public String toLogString() {
        return String.format("[%s] %s - %s (Code: %d, Time: %dms, ConnectionIssue: %b)",
                status, url, message, statusCode, responseTime, connectionIssue);
    }

    // 判断是否真正不可用（连接问题）
    public boolean isTrulyUnavailable() {
        return "DOWN".equals(status) && connectionIssue;
    }
}