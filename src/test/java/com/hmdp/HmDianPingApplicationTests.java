package com.hmdp;

import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisIDWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
class HmDianPingApplicationTests {
    @Resource
    private RedisIDWorker redisIDWorker;
    @Resource
    private ShopServiceImpl shopService;
    private ExecutorService es= Executors.newFixedThreadPool(500);
    @Test
    void testIdWorker() throws InterruptedException {
        Runnable task=()->{
            for (int i = 0; i < 100; i++) {
                long id= redisIDWorker.nextId("order");
                System.out.println(id);
            }
        };
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }


    }
    @Test
    void testSaveShop() throws InterruptedException {
        shopService.saveShop2Redis(1L,10L);
    }


}
