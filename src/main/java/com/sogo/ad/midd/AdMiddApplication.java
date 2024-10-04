package com.sogo.ad.midd;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
public class AdMiddApplication {

	public static void main(String[] args) {
		System.setProperty("com.sun.jndi.ldap.object.disableEndpointIdentification", "true");
		SpringApplication.run(AdMiddApplication.class, args);
	}

	@Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

}
