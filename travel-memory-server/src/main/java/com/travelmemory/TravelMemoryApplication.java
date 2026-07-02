package com.travelmemory;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.travelmemory.mapper")
@SpringBootApplication
public class TravelMemoryApplication {

    public static void main(String[] args) {
        SpringApplication.run(TravelMemoryApplication.class, args);
    }
}
