package com.greenwave;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GreenWaveApplication {
    public static void main(String[] args) {
        SpringApplication.run(GreenWaveApplication.class, args);
    }
}
