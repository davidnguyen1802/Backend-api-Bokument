package com.qldapm_L01.backend_api.Config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
@Getter
public class SupabaseStorageConfig {

    @Value("${supabase.storage.url}")
    private String url;

    @Value("${supabase.storage.service-role-key}")
    private String serviceRoleKey;

    @Value("${supabase.storage.bucket-name}")
    private String bucketName;

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}

