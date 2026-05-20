package com.redis.redissearchdemo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "demo.dataset.initialize-on-startup=false")
class RedisSearchDemoApplicationTests {

    @Test
    void contextLoads() {
    }

}
