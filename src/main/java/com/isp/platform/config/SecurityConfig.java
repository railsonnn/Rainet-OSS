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
        UserDetails admin = User.builder()
                .username("admin")
                .password(passwordEncoder().encode("admin123"))
                .roles("ADMIN", "TECH")
                .build();

        UserDetails billing = User.builder()
                .username("billing")
                .password(passwordEncoder().encode("billing123"))
                .roles("BILLING")
                .build();

        return new InMemoryUserDetailsManager(admin, billing);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
