package com.hjw.config;


import lombok.Data;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "spring.redis")
@Data
public class RedissonConfig
{

    private String host;

    private String port;

    private String database;

    @Bean
    public RedissonClient redissonClient()
    {
        // 创建配置
        Config config = new Config();
        String redisAddress = "redis://" + host + ":" + port;
        config
                .useSingleServer()
                .setAddress(redisAddress)
                .setDatabase(Integer.parseInt(database));


        // 创建实例
        return Redisson.create(config);
    }
}
