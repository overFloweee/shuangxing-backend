package com.hjw.contant;

/**
 * redis key 的前缀常量
 */
public interface RedisKeyPrefixConstant
{
    /**
     * 首页 推荐用户 的缓存前缀
     */
    String USER_RECOMMEND = "shuangxing:user:recommend:";


    /**
     * 定时任务 的 分布式锁 前缀
     */
    String PRECACHEJOB_LOCK = "shuangxing:precachejob:lock";
}
