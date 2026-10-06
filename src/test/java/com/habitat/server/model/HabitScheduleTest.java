package com.habitat.server.model;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class HabitScheduleTest {

    private HabitSchedule scheduleFrom(LocalDate effectiveFrom, LocalDate effectiveUntil) {
        HabitSchedule schedule = new HabitSchedule();
        schedule.setDayOfWeek(DayOfWeek.MONDAY);
        schedule.setStartTime(LocalTime.of(6, 0));
        schedule.setEndTime(LocalTime.of(7, 0));
        schedule.setEffectiveFrom(effectiveFrom);
        schedule.setEffectiveUntil(effectiveUntil);
        return schedule;
    }

    @Test
    void isCurrentlyActive_noEffectiveUntil_isTrue() {
        HabitSchedule schedule = scheduleFrom(LocalDate.of(2026, 1, 1), null);

        assertThat(schedule.isCurrentlyActive()).isTrue();
    }

    @Test
    void isCurrentlyActive_hasEffectiveUntil_isFalse() {
        HabitSchedule schedule = scheduleFrom(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1));

        assertThat(schedule.isCurrentlyActive()).isFalse();
    }

    @Test
    void isActiveOnDate_exactlyOnEffectiveFrom_isTrue() {
        // Regression test for the off-by-one bug: a row must be considered active on the very
        // day it starts, e.g. the day a brand-new schedule is added.
        LocalDate effectiveFrom = LocalDate.of(2026, 3, 2);
        HabitSchedule schedule = scheduleFrom(effectiveFrom, null);

        assertThat(schedule.isActiveOnDate(effectiveFrom)).isTrue();
    }

    @Test
    void isActiveOnDate_dayBeforeEffectiveFrom_isFalse() {
        LocalDate effectiveFrom = LocalDate.of(2026, 3, 2);
        HabitSchedule schedule = scheduleFrom(effectiveFrom, null);

        assertThat(schedule.isActiveOnDate(effectiveFrom.minusDays(1))).isFalse();
    }

    @Test
    void isActiveOnDate_dayAfterEffectiveFrom_openEnded_isTrue() {
        LocalDate effectiveFrom = LocalDate.of(2026, 3, 2);
        HabitSchedule schedule = scheduleFrom(effectiveFrom, null);

        assertThat(schedule.isActiveOnDate(effectiveFrom.plusYears(1))).isTrue();
    }

    @Test
    void isActiveOnDate_exactlyOnEffectiveUntil_isFalse() {
        // effectiveUntil is an exclusive end: the day a row is closed, it is no longer active.
        LocalDate effectiveFrom = LocalDate.of(2026, 1, 1);
        LocalDate effectiveUntil = LocalDate.of(2026, 2, 1);
        HabitSchedule schedule = scheduleFrom(effectiveFrom, effectiveUntil);

        assertThat(schedule.isActiveOnDate(effectiveUntil)).isFalse();
    }

    @Test
    void isActiveOnDate_dayBeforeEffectiveUntil_isTrue() {
        LocalDate effectiveFrom = LocalDate.of(2026, 1, 1);
        LocalDate effectiveUntil = LocalDate.of(2026, 2, 1);
        HabitSchedule schedule = scheduleFrom(effectiveFrom, effectiveUntil);

        assertThat(schedule.isActiveOnDate(effectiveUntil.minusDays(1))).isTrue();
    }

    @Test
    void isActiveOnDate_afterEffectiveUntil_isFalse() {
        LocalDate effectiveFrom = LocalDate.of(2026, 1, 1);
        LocalDate effectiveUntil = LocalDate.of(2026, 2, 1);
        HabitSchedule schedule = scheduleFrom(effectiveFrom, effectiveUntil);

        assertThat(schedule.isActiveOnDate(effectiveUntil.plusDays(1))).isFalse();
    }

    @Test
    void isActiveOnDate_sameDayEffectiveFromAndUntil_isAlwaysFalse() {
        // A row opened and closed on the same day (e.g. added and immediately removed again
        // within one syncSchedule call) should never resolve as active for that day.
        LocalDate day = LocalDate.of(2026, 1, 1);
        HabitSchedule schedule = scheduleFrom(day, day);

        assertThat(schedule.isActiveOnDate(day)).isFalse();
    }
}
