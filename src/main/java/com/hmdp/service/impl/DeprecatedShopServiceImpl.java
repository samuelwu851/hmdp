package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.BooleanUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;


@Deprecated
public class DeprecatedShopServiceImpl {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IShopService shopService;
    /**
     * This method implements preventing cache penetration,
     * (which means: data is not exists in database and every request will reach database)
     * but there's must be some way better to deal with
     if(MapUtil.isNotEmpty(entries) && entries.size() != 1)
     if("".equals(entries.get(""))){
     return Result.fail("shop is not exists, and return cache");
     }
     HashMap nullMap = new HashMap<>();
     nullMap.put("","");
     stringRedisTemplate.opsForHash().putAll(CACHE_SHOP_KEY + id,nullMap);
     */

    public Result queryWithPassThrough(Long id) {
        /**
         * Only three kinds of situation:
         * 1. shop exists -> return
         * 2. shop is not exists and return ""
         * 3. Shop object is not exists -> query database
         */
        //1. query cache from redis
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(CACHE_SHOP_KEY + id);
        //2. determine if it's exist
        if(MapUtil.isNotEmpty(entries) && entries.size() != 1){
            //3. ture -> return
            Shop shop = BeanUtil.fillBeanWithMap(entries,new Shop(),false);
            return Result.ok(shop);
        }
        if("".equals(entries.get(""))){
            return Result.fail("shop is not exists, and return cache");
        }

        //4. false -> query database by id
        Shop shop = shopService.getById(id);
        //5. if there's no such shop -> return false
        if (shop == null) {
            HashMap nullMap = new HashMap();
            nullMap.put("","");
            stringRedisTemplate.opsForHash().putAll(CACHE_SHOP_KEY + id,nullMap);
            stringRedisTemplate.opsForHash().getOperations().expire(CACHE_SHOP_KEY + id, CACHE_NULL_TTL, TimeUnit.MINUTES);
            return Result.fail("shop is not exists");
        }
        //6. exist -> return true
    /**
     * Noticed that setFieldValueEditor has higher priority than setIgnoreNullValue(true).
     * So there would be NPE when we don't determine fieldValue about whether it's null.
     * If we want to set a default value for some fields, we cannot have filtered
     * before the value is given to it.
     *
     * Since there are some fields of bean are null, and null is not allowed to
     * use toString().
     * BUT: It's not enough to solve the problem if we just add "" after fieldValue.
     *      Because when cache is hit, "" cannot be converted to int
     *
     * So we need to determine null value in "setFieldValueEditor"
     */
        Map<String, Object> shopMap = BeanUtil.beanToMap(shop, new HashMap<>(1),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> {
                            if(fieldValue == null){
                                fieldValue = "0";
                            }else {
                                fieldValue = fieldValue + "";
                            }
                            return fieldValue;
                        }));
        stringRedisTemplate.opsForHash().putAll(CACHE_SHOP_KEY + id, shopMap);
        stringRedisTemplate.opsForHash().getOperations().expire(CACHE_SHOP_KEY + id, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return Result.ok(shop);
    }



    /**
     * This method solve the problem of cache breakdown
     * by using mutex
     *
     * @param id Shop id
     */
    public Shop queryWithMutex(Long id) {
        //1. query cache from redis
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(CACHE_SHOP_KEY + id);
        //2. determine if it's exist
        if(MapUtil.isNotEmpty(entries) && entries.size() != 1){
            //3. ture -> return
            Shop shop = BeanUtil.fillBeanWithMap(entries,new Shop(),false);
            return shop;
        }
        if("".equals(entries.get(""))){
            return null;
        }

        //4. rebuild cache
        //4.1 try to get mutex
        String lockKey = LOCK_SHOP_KEY + id;
        boolean  isLock = tryLock(lockKey);
        //4.2 determine true or false
        // false -> sleep and try to get mutex again
        Shop shop = null;
        try {
            if(!isLock){
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            // true -> create a new thread to rebuild cache(save it to redis)
            shop = shopService.getById(id);
            //5. if there's no such shop -> return false
            if (shop == null) {
                HashMap nullMap = new HashMap<>();
                nullMap.put("","");
                stringRedisTemplate.opsForHash().putAll(CACHE_SHOP_KEY + id,nullMap);
                stringRedisTemplate.opsForHash().getOperations().expire(CACHE_SHOP_KEY + id, CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            // If shop exists, then save it to redis in the form of map
            Map<String, Object> shopMap = BeanUtil.beanToMap(shop, new HashMap<>(1),
                    CopyOptions.create()
                            .setIgnoreNullValue(true)
                            .setFieldValueEditor((fieldName, fieldValue) -> {
                                if(fieldValue == null){
                                    fieldValue = "0";
                                }else {
                                    fieldValue = fieldValue + "";
                                }
                                return fieldValue;
                            }));
            stringRedisTemplate.opsForHash().putAll(CACHE_SHOP_KEY + id, shopMap);
            stringRedisTemplate.opsForHash().getOperations().expire(CACHE_SHOP_KEY + id, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            unlock(lockKey);
        }
        return shop;
    }

    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }
}
