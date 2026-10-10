package com.habitat.server.time;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * The single place that decides what "today" and "now" are.
 *
 * <p>The date a habit is completed on, whether it is scheduled today, streaks and the heatmap all depend on the
 * calendar date, and that depends on the user's time zone. Clients send their IANA zone (for example
 * {@code Asia/Kolkata}) in the {@value #TIMEZONE_HEADER} header; this class reads it from the current request. When
 * the header is missing or not a valid zone, the configured default is used ({@code habitat.default-timezone}, or the
 * server's own zone if that is blank).
 *
 * <p>Code must not call {@code LocalDate.now()} / {@code LocalDateTime.now()} directly; use this instead.
 * Schedule times (start/end) are wall-clock times and are never converted.
 */
@Component
public class UserClock {
    public static final String TIMEZONE_HEADER = "X-Timezone";

    private static final Logger log = LoggerFactory.getLogger(UserClock.class);

    private final Clock clock;
    private final ZoneId defaultZone;

    public UserClock(Clock clock, @Value("${habitat.default-timezone:}") String defaultZoneId) {
        this.clock = clock;
        this.defaultZone = StringUtils.hasText(defaultZoneId) ? ZoneId.of(defaultZoneId.trim()) : clock.getZone();
    }

    /** The time zone for the current request, or the default one outside a request or when the header is unusable. */
    public ZoneId zone() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            String header = servletAttributes.getRequest().getHeader(TIMEZONE_HEADER);
            if (StringUtils.hasText(header)) {
                try {
                    return ZoneId.of(header.trim());
                } catch (DateTimeException e) {
                    log.warn("Ignoring invalid {} header value '{}'; using {}", TIMEZONE_HEADER, header, defaultZone);
                }
            }
        }
        return defaultZone;
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(zone()));
    }

    public LocalDateTime now() {
        return LocalDateTime.now(clock.withZone(zone()));
    }
}
