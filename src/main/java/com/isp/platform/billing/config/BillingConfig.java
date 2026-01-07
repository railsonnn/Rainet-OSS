package com.isp.platform.billing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration for billing module dependencies.
 */
@Configuration
public class BillingConfig {

    /**
     * RestTemplate bean for PIX gateway HTTP communication.
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
