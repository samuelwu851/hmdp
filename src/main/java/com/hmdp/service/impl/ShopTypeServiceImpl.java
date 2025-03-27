package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.List;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.SHOP_TYPELIST;


@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * Do not forget to convert string to List
     * because data get from redis is string type!
     * @return
     */
    @Override
    public Result queryTypeList() {

        //1. query from redis whether it contains type list or not
        List<String> stl = stringRedisTemplate.opsForList().range(SHOP_TYPELIST, 0, -1);
        if (!stl.isEmpty()) {
            //2. true -> return type list
            List<ShopType> shopTypeList =stl.stream().
                    //string from redis to list which will be returned
                            map(str-> JSONUtil.toBean(str,ShopType.class)).
                            collect(Collectors.toList());
            return Result.ok(shopTypeList);
        }
        //3. false -> query from database and save it to redis
        List<ShopType> shopTypeList = query().orderByAsc("sort").list();
        //save it to redis
        shopTypeList.forEach(shopType -> stringRedisTemplate.opsForList().rightPush(SHOP_TYPELIST,JSONUtil.toJsonStr(shopType)));
        return Result.ok(shopTypeList);
    }
}
