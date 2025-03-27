package com.hmdp;

import com.hmdp.service.impl.ShopServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.Map;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private ShopServiceImpl shopService;

    @Test
    public void hashMapTest(){
        Map<Object, Object> objectObjectMap = Collections.emptyMap();
        if(objectObjectMap == null){
            System.out.println("1");
        }else if(objectObjectMap != null) {
            System.out.println("2");
        }
    }

    @Test
    void testSaveShop(){
        shopService.saveShop2Redis(1L,10L);
    }


}
