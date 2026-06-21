package com.gzbgyl.crm.identity.application;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Configuration
class IdentityPasswordConfiguration {

    @Bean
    BCryptPasswordEncoder identityPasswordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
