package com.isp.platform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for HTTP Basic Authentication with in-memory users.
 * 
 * This configuration is intended for testing and POC environments.
 * For production, migrate to a UserDetailsService backed by a database or external provider.
 * 
 * TODO: Migrate to database-backed UserDetailsService for production use
 * TODO: Implement proper user management endpoints for production
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/provisioning/**").hasAnyRole("ADMIN", "TECH")
                        .requestMatchers("/billing/**").hasAnyRole("ADMIN", "BILLING")
                        .anyRequest().authenticated())
                .httpBasic(httpBasic -> {});
        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        // In-memory users for testing/POC
        // TODO: Replace with database-backed UserDetailsService for production
        // TODO: Move credentials to environment variables or secure configuration
        
        String adminPassword = System.getenv("BASIC_AUTH_ADMIN_PASSWORD");
        if (adminPassword == null || adminPassword.isEmpty()) {
            adminPassword = "admin123"; // Fallback for dev/testing only
        }
        
        String billingPassword = System.getenv("BASIC_AUTH_BILLING_PASSWORD");
        if (billingPassword == null || billingPassword.isEmpty()) {
            billingPassword = "billing123"; // Fallback for dev/testing only
        }
        
        UserDetails admin = User.builder()
                .username("admin")
                .password(passwordEncoder.encode(adminPassword))
                .roles("ADMIN", "TECH")
                .build();

        UserDetails billing = User.builder()
                .username("billing")
                .password(passwordEncoder.encode(billingPassword))
                .roles("BILLING")
                .build();

        return new InMemoryUserDetailsManager(admin, billing);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return passwordEncoder;
    }
}
