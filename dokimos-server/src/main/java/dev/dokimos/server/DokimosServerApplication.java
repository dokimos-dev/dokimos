package dev.dokimos.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DokimosServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(DokimosServerApplication.class, args);
    }
}
