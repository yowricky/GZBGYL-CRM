package com.gzbgyl.crm.identity.application;

import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ExplicitScopeConfiguration {

    @Bean
    @ConditionalOnMissingBean(ExplicitScopeProvider.class)
    ExplicitScopeProvider emptyExplicitScopeProvider() {
        return userId -> Set.of();
    }
}
