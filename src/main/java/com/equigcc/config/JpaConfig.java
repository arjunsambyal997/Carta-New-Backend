package com.equigcc.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** Kept off the main class so web-slice tests don't need a JPA context. */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
