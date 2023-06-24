package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import io.netty.util.internal.StringUtil;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
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
    @Override
    public Result queryById(Long id) {
        //缓存穿透
        //Shop shop=queryWithPassThrough(id);

        //互斥锁
        Shop shop=queryWithMutex(id);
        if(shop==null)
        {
            return Result.fail("店铺不存在");
        }

        return Result.ok(shop);
    }
    public Shop queryWithMutex(Long id)
    {
        String key = RedisConstants.CACHE_SHOP_KEY+id;

        //1.redis中查缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //判断是否存在
        if(StrUtil.isNotBlank(shopJson))
        {
            Shop shop= JSONUtil.toBean(shopJson,Shop.class);
            return  shop;
        }
        if(shopJson!=null)
        {
            return null;
        }
        //4. 实现缓存重建
        //4.1. 获取互斥锁
        Shop shop= null;
        String lockKey="lock:shop:"+id;
        try {

            boolean isLock= tryLock(lockKey);
            //4.2. 判断是否成功
            if(!isLock)
            {
                //4.3. 失败则休眠
                Thread.sleep(50);
                return queryWithMutex(id);
            }

            //4.4. 成功则根据id查数据库
            //查询数据库
            if(StrUtil.isNotBlank(shopJson))
            {
                shop= JSONUtil.toBean(shopJson,Shop.class);
                return  shop;
            }
            if(shopJson!=null)
            {
                return null;
            }
            shop = getById(id);
            //模拟重建延时
            Thread.sleep(200);
            if(shop==null)
            {
                stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL,TimeUnit.MINUTES);
                return null;
            }
            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally {
            unlock(lockKey);
        }

        return shop;
    }
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
