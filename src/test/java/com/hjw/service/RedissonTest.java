package com.hjw.service;


import org.junit.jupiter.api.Test;
import org.redisson.api.RList;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

@SpringBootTest
public class RedissonTest
{


    @Resource
    private RedissonClient redissonClient;

    @Test
    void test()
    {
        RList<String> list = redissonClient.getList("test-list");
        list.add("hjw");
        list.add("hjw123");

    }
}
