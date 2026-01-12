package com.isp.platform.gateway.security;

import com.isp.platform.gateway.auth.UserAccount;
import com.isp.platform.gateway.auth.UserAccountRepository;
import com.isp.platform.gateway.tenant.TenantResolverFilter;
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
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

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
                        .requestMatchers("/actuator/health", "/auth/login", "/auth/refresh").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(basic -> {})
                .addFilterBefore(new TenantResolverFilter(), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    /**
     * UserDetailsService that loads users from the database.
     * This replaces the in-memory user configuration with DB-backed authentication.
     */
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            // Find user across all tenants by username
            // Note: In a multi-tenant setup, you might want to include tenant context
            UserAccount userAccount = userAccountRepository.findAll().stream()
                    .filter(u -> u.getUsername().equals(username) && u.isEnabled())
                    .findFirst()
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

            return User.builder()
                    .username(userAccount.getUsername())
                    .password(userAccount.getPasswordHash())
                    .authorities(userAccount.getRoles().stream()
                            .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                            .toList())
                    .build();
        };
    }
}
