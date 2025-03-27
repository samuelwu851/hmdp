package com.hmdp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class utilTest {
    @Test
    public void generateVerificationCode(){
        String random = "" + Math.random();
        System.out.println(random);
        String substring = random.substring(random.length() - 4);
        System.out.println(substring);
    }
}
