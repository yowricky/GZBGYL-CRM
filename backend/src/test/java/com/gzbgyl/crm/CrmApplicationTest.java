package com.gzbgyl.crm;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.annotation.AnnotatedElementUtils;

import static org.assertj.core.api.Assertions.assertThat;

class CrmApplicationTest {

    @Test
    void applicationIsConfiguredAsSpringBootApplication() {
        assertThat(AnnotatedElementUtils.hasAnnotation(
                CrmApplication.class,
                SpringBootApplication.class
        )).isTrue();
    }
}
