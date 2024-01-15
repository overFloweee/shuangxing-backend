package com.hjw;


import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 启动类
 */
@SpringBootApplication
@MapperScan("com.hjw.mapper")
@EnableScheduling
public class ShuangxingApplication
{

    public static void main(String[] args) {
        SpringApplication.run(ShuangxingApplication.class, args);
    }

}

