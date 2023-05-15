package com.atwj.aiyou;

import com.atwj.aiyou.config.RedissonConfig;
import com.atwj.aiyou.model.domain.User;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.RList;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import javax.annotation.Resource;
import java.util.ArrayList;

@SpringBootTest
public class RedissonTest {
    @Resource
    private RedissonClient redissonClient;

    @Test
    void test() {
        ArrayList<String> list = new ArrayList<>();
        list.add("yupi");
        System.out.println(list.get(0));
        list.remove(0);

        RList<String> testList = redissonClient.getList("test-list");
        testList.add("yupi");
        System.out.println(testList.get(0));
        testList.remove(0);

    }
}
