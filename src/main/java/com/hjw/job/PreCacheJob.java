package com.hjw.job;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hjw.common.ResultUtils;
import com.hjw.mapper.UserMapper;
import com.hjw.model.domain.User;
import com.hjw.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.hjw.contant.RedisKeyPrefixConstant.PRECACHEJOB_LOCK;
import static com.hjw.contant.RedisKeyPrefixConstant.USER_RECOMMEND;

/**
 * 缓存预热 任务
 */
@Component
@Slf4j
public class PreCacheJob
{

    @Resource
    private UserService userService;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;


    @Resource
    private RedissonClient redissonClient;

    // 重点用户
    private List<Long> mainUserList = Arrays.asList(100127L, 100128L);

    // 每天执行，缓存预热推荐用户
    @Scheduled(cron = "0 7 17 * * *")
    public void doCacheRecommendUser()
    {
        RLock lock = redissonClient.getLock(PRECACHEJOB_LOCK);

        try
        {
            if (lock.tryLock(0, 30000, TimeUnit.MILLISECONDS))
            {
                for (Long userId : mainUserList)
                {

                    // 查询数据库
                    LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
                    Page<User> page = userService.page(new Page<>(1, 20), wrapper);

                    // 缓存
                    String key = USER_RECOMMEND + userId;
                    try
                    {
                        // 并写入缓存
                        redisTemplate
                                .opsForValue()
                                .set(key, page, 10, TimeUnit.MINUTES);
                    }
                    catch (Exception e)
                    {
                        log.error("redis set key error", e);
                    }

                }
            }

        }
        catch (InterruptedException e)
        {
            log.error("doPreCacheRecommendJob Error", e);
        }
        finally
        {
            // 只能释放自己线程的锁
            if (lock.isHeldByCurrentThread())
            {
                lock.unlock();

            }
        }


    }


}
