package com.example.healthcheck;

import com.example.healthcheck.service.LoadBalancerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class DemoApplicationTests {

    @Autowired
    LoadBalancerService loadBalancerService;

    @Test
    void contextLoads() throws InterruptedException {
        while (true){
            Thread.sleep(2*1000);
            System.out.println(loadBalancerService.getNextHealthyUrlForBank("1035"));
        }
    }

}
