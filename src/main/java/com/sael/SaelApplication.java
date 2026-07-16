package com.sael;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

// Exclude Spring Boot's auto-configured InMemoryUserDetailsManager.
// Authentication is handled entirely by JwtAuthFilter — no UserDetailsService needed.
@SpringBootApplication(exclude = {UserDetailsServiceAutoConfiguration.class})
@EnableScheduling
public class SaelApplication {
    public static void main(String[] args) {
        SpringApplication.run(SaelApplication.class, args);
    }
}
