package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import io.netty.util.internal.StringUtil;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private CacheClient cacheClient;
    @Override
    public Result queryById(Long id) {
        //缓存穿透
        Shop shop=cacheClient
                .queryWithPassThrough(RedisConstants.CACHE_SHOP_KEY,id,Shop.class,this::getById,RedisConstants.CACHE_SHOP_TTL,TimeUnit.MINUTES);

        //互斥锁
        //Shop shop=queryWithMutex(id);

        //逻辑过期
        //Shop shop=queryWithLogicalExpire(id);
        if(shop==null)
        {
            return Result.fail("店铺不存在");
        }

        return Result.ok(shop);
    }
    public Shop queryWithLogicalExpire(Long id)
    {
        String key = RedisConstants.CACHE_SHOP_KEY+id;

        //1.redis中查缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //判断是否存在
        if(StrUtil.isBlank(shopJson))
        {
            return  null;
        }
        //命中，反序列化，判断是否过期
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        JSONObject data=(JSONObject) redisData.getData();
        Shop shop = JSONUtil.toBean(data, Shop.class);
        LocalDateTime expireTime=redisData.getExpireTime();
        if(expireTime.isAfter(LocalDateTime.now()))
        {
            //未过期直接返回
            return shop;
        }
        String lockKey=RedisConstants.LOCK_SHOP_KEY+id;
        boolean isLock = tryLock(lockKey);
        if(isLock)
        {
            //已过期，需要重建
            //缓存重建：获取互斥锁，判断是否枷锁成功，成功开启独立线程重建
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    this.saveShop2Redis(id,10L);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } finally {
                    unlock(lockKey);
                }
            });
        }
        shopJson = stringRedisTemplate.opsForValue().get(key);
        //判断是否存在
        if(StrUtil.isBlank(shopJson))
        {
            return  null;
        }
        //命中，反序列化，判断是否过期
        redisData = JSONUtil.toBean(shopJson, RedisData.class);
        data=(JSONObject) redisData.getData();
        shop = JSONUtil.toBean(data, Shop.class);
        expireTime=redisData.getExpireTime();
        if(expireTime.isAfter(LocalDateTime.now()))
        {
            //未过期直接返回
            return shop;
        }

        //返回过期信息
        return shop;
    }

//    public Shop queryWithMutex(Long id)
//    {
//        String key = RedisConstants.CACHE_SHOP_KEY+id;
//
//        //1.redis中查缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        //判断是否存在
//        if(StrUtil.isNotBlank(shopJson))
//        {
//            Shop shop= JSONUtil.toBean(shopJson,Shop.class);
//            return  shop;
//        }
//        if(shopJson!=null)
//        {
//            return null;
//        }
//        //4. 实现缓存重建
//        //4.1. 获取互斥锁
//        Shop shop= null;
//        String lockKey="lock:shop:"+id;
//        try {
//
//            boolean isLock= tryLock(lockKey);
//            //4.2. 判断是否成功
//            if(!isLock)
//            {
//                //4.3. 失败则休眠
//                Thread.sleep(50);
//                return queryWithMutex(id);
//            }
//
//            //4.4. 成功则根据id查数据库
//            //查询数据库
//            if(StrUtil.isNotBlank(shopJson))
//            {
//                shop= JSONUtil.toBean(shopJson,Shop.class);
//                return  shop;
//            }
//            if(shopJson!=null)
//            {
//                return null;
//            }
//            shop = getById(id);
//            //模拟重建延时
//            Thread.sleep(200);
//            if(shop==null)
//            {
//                stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL,TimeUnit.MINUTES);
//                return null;
//            }
//            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        }finally {
//            unlock(lockKey);
//        }
//
//        return shop;
//    }
//    public Shop queryWithPassThrough(Long id)
//    {
//        String key = RedisConstants.CACHE_SHOP_KEY+id;
//
//        //1.redis中查缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        //判断是否存在
//        if(StrUtil.isNotBlank(shopJson))
//        {
//            Shop shop= JSONUtil.toBean(shopJson,Shop.class);
//            return  shop;
//        }
//        if(shopJson!=null)
//        {
//            return null;
//        }
//        //查询数据库
//        Shop shop=getById(id);
//        if(shop==null)
//        {
//            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL,TimeUnit.MINUTES);
//            return null;
//        }
//        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
//
//        return shop;
//    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id=shop.getId();
        if(id==null)
        {
            return  Result.fail("店铺不能为空");
        }
        //1.更新数据库
        updateById(shop);
        //2.删除缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY+id);
        return Result.ok();
    }
    public void saveShop2Redis(Long id,Long expireSeconds) throws InterruptedException {
        //1. 查询店铺数据
        Shop shop=getById(id);
        Thread.sleep(200);
        //2. 封装逻辑过期时间
        RedisData redisData=new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));
    }
    private static  final ExecutorService CACHE_REBUILD_EXECUTOR= Executors.newFixedThreadPool(10);

    private boolean tryLock(String key)
    {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
    private void unlock(String key)
    {
        stringRedisTemplate.delete(key);
    }
}
