package com.atwj.aiyou.job;

import com.atwj.aiyou.model.domain.User;
import com.atwj.aiyou.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.scheduling.annotation.Scheduled;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 定时预热任务
 */
@Slf4j
public class PreCacheJob {
    @Resource
    private UserService userService;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Resource
    private RedissonClient redissonClient;

    //重点用户，及系统为其提前预热缓存数据
    private List<Long> userList = Arrays.asList(1L);

    //定时预热
    @Scheduled(cron = "0 0 0 * * *") //每天 0:00 执行
    public void preCacheJob() {
        //创建锁（锁资源存入redis当中）
        RLock lock = redissonClient.getLock("aiyou:preCacheJob:doCache:lock");
        try {
            /**
             * long var1, long var3, TimeUnit var5
             * 等待时间（0表示如果未获取锁不在等待），锁过期时间， 时间单位
             */
            if (lock.tryLock(0, 30000L, TimeUnit.MILLISECONDS)) {
                for (Long userId :
                        userList) {
                    QueryWrapper<User> queryWrapper = new QueryWrapper<>();
                    Page<User> userPage = userService.page(new Page<>(1, 20), queryWrapper);
                    String redisKey = "aiyou:user:recommend:%s" + userId;
                    ValueOperations<String, Object> valueOperations = redisTemplate.opsForValue();

                    try {
                        valueOperations.set(redisKey, userPage, 30000, TimeUnit.MILLISECONDS);
                    } catch (Exception e) {
                        log.error("redisKey set error");
                    }
                }
            }
        } catch (InterruptedException e) {
            log.error("preCacheJob error", e);
        } finally {
            //如果当前锁是当前线程所加，则释放锁
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
