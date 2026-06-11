package com.smartdorm.backend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@MapperScan("com.smartdorm.backend.mapper")
@SpringBootApplication
@EnableScheduling
public class SmartDormBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartDormBackendApplication.class, args);
    }
}
