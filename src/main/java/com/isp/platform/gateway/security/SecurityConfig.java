package com.isp.platform.gateway.security;

import com.isp.platform.gateway.auth.UserAccount;
import com.isp.platform.gateway.auth.UserAccountRepository;
import com.isp.platform.gateway.tenant.TenantContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import java.util.stream.Collectors;

/**
 * Security configuration using HTTP Basic Authentication with database-backed user store.
 * Removed JWT support - Basic Auth validates credentials against UserAccount table.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final UserAccountRepository userAccountRepository;

    public SecurityConfig(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(basic -> {});
        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            // Note: In a multi-tenant system, we would need tenant context
            // For Basic Auth, we'll look up user by username across all tenants
            // In production, consider adding tenant identifier to username (e.g., user@tenant)
            UserAccount userAccount = userAccountRepository.findByUsername(username)
                    .filter(UserAccount::isEnabled)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
            
            // Set tenant context from user's tenant
            TenantContext.setCurrentTenant(userAccount.getTenantId());
            
            // Map domain roles to Spring Security GrantedAuthority
            var authorities = userAccount.getRoles().stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                    .collect(Collectors.toList());
            
            return User.builder()
                    .username(userAccount.getUsername())
                    .password(userAccount.getPasswordHash())
                    .authorities(authorities)
                    .disabled(!userAccount.isEnabled())
                    .build();
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}
