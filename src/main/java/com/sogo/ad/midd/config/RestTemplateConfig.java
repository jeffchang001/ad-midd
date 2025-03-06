package com.sogo.ad.midd.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        // 創建支援 PATCH 方法的 HttpComponentsClientHttpRequestFactory
        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();
        
        // 設定超時時間 (毫秒)
        requestFactory.setConnectTimeout(5000);
        requestFactory.setReadTimeout(10000);
        
        // 使用自訂的 requestFactory 創建 RestTemplate
        RestTemplate restTemplate = new RestTemplate(requestFactory);
        
        return restTemplate;
    }
}
