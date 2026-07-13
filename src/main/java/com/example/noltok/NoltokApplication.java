package com.example.noltok;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class NoltokApplication {

    public static void main(String[] args) {
        SpringApplication.run(NoltokApplication.class, args);
    }

}
