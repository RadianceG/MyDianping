package com.hmdp.config;

import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.Redisson;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissionConfig {
    @Bean
    public RedissonClient redissonClient()
    {
        Config config=new Config();
        config.useSingleServer().setAddress("redis://172.29.48.136:6379").setPassword("123456");
        return Redisson.create(config);
    }
}
