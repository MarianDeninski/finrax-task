package com.finrax.interview_task.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI walletServiceApi() {
        return new OpenAPI().info(new Info()
                .title("Crypto Wallet Service")
                .version("1.0.0"));
    }
}
