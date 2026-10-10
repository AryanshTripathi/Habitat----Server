package com.habitat.server.time;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class UserClockTest {

    // 2026-10-06 22:00 UTC is already 2026-10-07 03:30 in India.
    private static final Instant INSTANT = Instant.parse("2026-10-06T22:00:00Z");
    private static final Clock FIXED = Clock.fixed(INSTANT, ZoneOffset.UTC);

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    private void requestWithTimezoneHeader(String value) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (value != null) {
            request.addHeader(UserClock.TIMEZONE_HEADER, value);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    void today_usesTheZoneFromTheHeader() {
        UserClock userClock = new UserClock(FIXED, "UTC");

        requestWithTimezoneHeader("Asia/Kolkata");

        assertThat(userClock.today()).isEqualTo(LocalDate.of(2026, 10, 7));
        assertThat(userClock.now()).isEqualTo(LocalDateTime.of(2026, 10, 7, 3, 30));
    }

    @Test
    void today_sameInstantDiffersAcrossZones() {
        UserClock userClock = new UserClock(FIXED, "UTC");

        requestWithTimezoneHeader("UTC");
        assertThat(userClock.today()).isEqualTo(LocalDate.of(2026, 10, 6));

        requestWithTimezoneHeader("America/Los_Angeles");
        assertThat(userClock.today()).isEqualTo(LocalDate.of(2026, 10, 6));

        requestWithTimezoneHeader("Pacific/Kiritimati");
        assertThat(userClock.today()).isEqualTo(LocalDate.of(2026, 10, 7));
    }

    @Test
    void zone_acceptsTheOlderCalcuttaAlias() {
        requestWithTimezoneHeader("Asia/Calcutta");

        assertThat(new UserClock(FIXED, "UTC").today()).isEqualTo(LocalDate.of(2026, 10, 7));
    }

    @Test
    void zone_headerWithSurroundingSpaces_isTrimmed() {
        requestWithTimezoneHeader("  Asia/Kolkata ");

        assertThat(new UserClock(FIXED, "UTC").zone()).isEqualTo(ZoneId.of("Asia/Kolkata"));
    }

    @Test
    void zone_missingHeader_usesTheConfiguredDefault() {
        requestWithTimezoneHeader(null);

        assertThat(new UserClock(FIXED, "Asia/Kolkata").zone()).isEqualTo(ZoneId.of("Asia/Kolkata"));
    }

    @Test
    void zone_invalidHeader_fallsBackToTheDefaultInsteadOfFailing() {
        requestWithTimezoneHeader("Not/AZone");

        UserClock userClock = new UserClock(FIXED, "UTC");

        assertThat(userClock.zone()).isEqualTo(ZoneId.of("UTC"));
        assertThat(userClock.today()).isEqualTo(LocalDate.of(2026, 10, 6));
    }

    @Test
    void zone_outsideARequest_usesTheDefault() {
        assertThat(new UserClock(FIXED, "Asia/Kolkata").zone()).isEqualTo(ZoneId.of("Asia/Kolkata"));
    }

    @Test
    void zone_blankDefault_fallsBackToTheClocksOwnZone() {
        Clock inTokyo = Clock.fixed(INSTANT, ZoneId.of("Asia/Tokyo"));

        assertThat(new UserClock(inTokyo, "").zone()).isEqualTo(ZoneId.of("Asia/Tokyo"));
        assertThat(new UserClock(inTokyo, "   ").zone()).isEqualTo(ZoneId.of("Asia/Tokyo"));
    }
}
