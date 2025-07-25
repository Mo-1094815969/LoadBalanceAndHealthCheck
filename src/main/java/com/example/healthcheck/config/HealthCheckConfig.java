package com.example.healthcheck.config;

import com.example.healthcheck.service.BankUrlManager;
import com.example.healthcheck.service.HealthCheckService;
import com.example.healthcheck.service.LoadBalancerService;
import com.example.healthcheck.service.impl.HealthCheckServiceImpl;
import com.example.healthcheck.service.lbstrategy.LoadBalanceStrategy;
import com.example.healthcheck.service.lbstrategy.RandomStrategy;
import com.example.healthcheck.service.lbstrategy.RoundRobinStrategy;
import com.example.healthcheck.service.lbstrategy.WeightedRoundRobinStrategy;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Configuration
@EnableScheduling
public class HealthCheckConfig {

    @Value("${health.restTemplate.poolLimit}")
    private String poolLimit;

    @Value("${health.restTemplate.routerLimit}")
    private String routerLimit;

    @Value("${health.restTemplate.socketTimeout}")
    private String socketTimeout;

    @Value("${health.restTemplate.connectionTimeout}")
    private String connectionTimeout;

    @Value("${health.restTemplate.connectionRequestTimeout}")
    private String connectionRequestTimeout;

    @Bean
    public RestTemplate restTemplate() {
        RestTemplate restTemplate = new RestTemplate(httpRequestFactory());
        restTemplate.getMessageConverters().set(1, new StringHttpMessageConverter(StandardCharsets.UTF_8));
        return restTemplate;
    }

    @Bean
    public ClientHttpRequestFactory httpRequestFactory() {
        return new HttpComponentsClientHttpRequestFactory(httpClient());
    }

    @Bean
    public HttpClient httpClient() {
        Registry<ConnectionSocketFactory> registry = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", PlainConnectionSocketFactory.getSocketFactory())
                .register("https", SSLConnectionSocketFactory.getSocketFactory())
                .build();
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager(registry);
        //设置整个连接池最大连接数 根据自己的场景决定
        connectionManager.setMaxTotal(Integer.parseInt(poolLimit));
        //路由是对maxTotal的细分
        connectionManager.setDefaultMaxPerRoute(Integer.parseInt(routerLimit));
        RequestConfig requestConfig = RequestConfig.custom()
                .setSocketTimeout(Integer.parseInt(socketTimeout)) //服务器返回数据(response)的时间，超过该时间抛出read timeout
                .setConnectTimeout(Integer.parseInt(connectionTimeout))//连接上服务器(握手成功)的时间，超出该时间抛出connect timeout
                .setConnectionRequestTimeout(Integer.parseInt(connectionRequestTimeout))//从连接池中获取连接的超时时间，超过该时间未拿到可用连接，会抛出org.apache.http.conn.ConnectionPoolTimeoutException: Timeout waiting for connection from pool
                .build();
        return HttpClientBuilder.create()
                .setDefaultRequestConfig(requestConfig)
                .setConnectionManager(connectionManager)
                .build();
    }

    @Bean
    public List<String> initialUrls(BankUrlManager bankUrlManager) {
        List<String> allUrls = new ArrayList<>();
        bankUrlManager.getAllBankConfigs().values().forEach(config -> {
            config.getUrls().forEach(urlConfig -> {
                allUrls.add(urlConfig.getUrl());
            });
        });
        return allUrls;
    }

    @Bean
    public CopyOnWriteArrayList<String> healthCheckList(List<String> initialUrls) { // 根据实际泛型类型替换 <?>
        return new CopyOnWriteArrayList<>(initialUrls);
    }

    @Bean
    public HealthCheckService healthCheckService(RestTemplate restTemplate,
                                                 List<String> initialUrls,
                                                 @Value("${health.check.max-failures}") int maxFailureThreshold,
                                                 @Value("${health.check.recovery-threshold}") int recoverySuccessThreshold,
                                                 LoadBalancerService loadBalancerService,
                                                 BankUrlManager bankUrlManager) {
        CopyOnWriteArrayList<String> activeUrls = new CopyOnWriteArrayList<>(initialUrls);
        return new HealthCheckServiceImpl(restTemplate, activeUrls,
                maxFailureThreshold,
                recoverySuccessThreshold,
                loadBalancerService,
                bankUrlManager);
    }

//    @Bean
//    public LoadBalanceStrategy roundRobinStrategy() {
//        return new RoundRobinStrategy();
//    }
//
//    @Bean
//    public LoadBalanceStrategy randomStrategy() {
//        return new RandomStrategy();
//    }

    @Bean
    public LoadBalanceStrategy weightedStrategy(BankUrlManager bankUrlManager) {
        return new WeightedRoundRobinStrategy(bankUrlManager);
    }
}