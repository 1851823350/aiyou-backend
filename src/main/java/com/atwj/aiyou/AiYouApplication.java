package com.atwj.aiyou;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.atwj.aiyou.mapper")
@EnableScheduling
public class AiYouApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiYouApplication.class, args);
    }

}
