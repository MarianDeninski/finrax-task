package com.finrax.interview_task.autoconfigure;

import com.finrax.interview_task.client.WithdrawalClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
class WithdrawalClientAutoConfiguration {

    @Bean
    WithdrawalClient withdrawalClient() {
        return new WithdrawalClient();
    }
}
