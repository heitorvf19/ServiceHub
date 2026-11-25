package br.ufscar.servicehub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

@SpringBootApplication
public class ServiceHubApplication {
    public static void main(String[] args) {
        SpringApplication.run(ServiceHubApplication.class, args);
    }

    @Bean
    Clock relogio() {
        return Clock.systemUTC();
    }
}
