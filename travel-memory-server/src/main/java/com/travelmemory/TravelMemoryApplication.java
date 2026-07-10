package com.travelmemory;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.travelmemory.config.UploadCleanupProperties;

@MapperScan("com.travelmemory.mapper")
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(UploadCleanupProperties.class)
public class TravelMemoryApplication {

    public static void main(String[] args) {
        SpringApplication.run(TravelMemoryApplication.class, args);
    }
}
