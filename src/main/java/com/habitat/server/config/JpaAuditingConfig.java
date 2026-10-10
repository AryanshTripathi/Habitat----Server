package com.habitat.server.config;

import com.habitat.server.time.UserClock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.Optional;

@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "userDateTimeProvider")
public class JpaAuditingConfig {

    /** createdOn / updatedOn are stamped with the date in the requesting user's time zone, not the server's. */
    @Bean
    public DateTimeProvider userDateTimeProvider(UserClock userClock) {
        return () -> Optional.of(userClock.now());
    }
}
