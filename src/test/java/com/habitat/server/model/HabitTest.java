package com.habitat.server.model;

import com.habitat.server.exception.DuplicateScheduleException;
import com.habitat.server.testsupport.HabitTestDataFactory;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HabitTest {

    @Test
    void addSchedule_setsBackReferenceToThisHabit() {
        Habit habit = HabitTestDataFactory.aHabit();
        HabitSchedule schedule = HabitTestDataFactory.aSchedule(DayOfWeek.MONDAY, LocalTime.of(6, 0), LocalTime.of(7, 0));

        habit.addSchedule(schedule);

        assertThat(habit.getHabitSchedule()).containsExactly(schedule);
        assertThat(schedule.getHabit()).isSameAs(habit);
    }

    @Test
    void syncSchedule_existingDayPresentInIncoming_updatesInPlace_keepsSameInstance() {
        Habit habit = HabitTestDataFactory.aHabit();
        HabitSchedule monday = HabitTestDataFactory.aSchedule(DayOfWeek.MONDAY, LocalTime.of(6, 0), LocalTime.of(7, 0));
        habit.addSchedule(monday);

        HabitSchedule incomingMonday = HabitTestDataFactory.aSchedule(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(9, 0));

        habit.syncSchedule(List.of(incomingMonday));

        assertThat(habit.getHabitSchedule()).hasSize(1);
        HabitSchedule result = habit.getHabitSchedule().get(0);
        assertThat(result).isSameAs(monday); // same row/object, not replaced — proves no delete+recreate churn
        assertThat(result.getStartTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(result.getEndTime()).isEqualTo(LocalTime.of(9, 0));
    }

    @Test
    void syncSchedule_newDayInIncoming_addsScheduleWithBackReferenceSet() {
        Habit habit = HabitTestDataFactory.aHabit();
        HabitSchedule monday = HabitTestDataFactory.aSchedule(DayOfWeek.MONDAY, LocalTime.of(6, 0), LocalTime.of(7, 0));
        habit.addSchedule(monday);

        HabitSchedule incomingMonday = HabitTestDataFactory.aSchedule(DayOfWeek.MONDAY, LocalTime.of(6, 0), LocalTime.of(7, 0));
        HabitSchedule incomingWednesday = HabitTestDataFactory.aSchedule(DayOfWeek.WEDNESDAY, LocalTime.of(7, 0), LocalTime.of(8, 0));

        habit.syncSchedule(List.of(incomingMonday, incomingWednesday));

        assertThat(habit.getHabitSchedule()).hasSize(2);
        assertThat(habit.getHabitSchedule())
            .filteredOn(s -> s.getDayOfWeek() == DayOfWeek.WEDNESDAY)
            .first()
            .satisfies(s -> assertThat(s.getHabit()).isSameAs(habit));
    }

    @Test
    void syncSchedule_dayMissingFromIncoming_removesIt() {
        Habit habit = HabitTestDataFactory.aHabit();
        HabitSchedule monday = HabitTestDataFactory.aSchedule(DayOfWeek.MONDAY, LocalTime.of(6, 0), LocalTime.of(7, 0));
        HabitSchedule wednesday = HabitTestDataFactory.aSchedule(DayOfWeek.WEDNESDAY, LocalTime.of(7, 0), LocalTime.of(8, 0));
        habit.addSchedule(monday);
        habit.addSchedule(wednesday);

        HabitSchedule incomingMonday = HabitTestDataFactory.aSchedule(DayOfWeek.MONDAY, LocalTime.of(6, 0), LocalTime.of(7, 0));

        // Only MONDAY is sent back - WEDNESDAY should be dropped from the habit entirely
        habit.syncSchedule(List.of(incomingMonday));

        assertThat(habit.getHabitSchedule())
            .extracting(HabitSchedule::getDayOfWeek)
            .containsExactly(DayOfWeek.MONDAY);
    }

    @Test
    void syncSchedule_combinedAddUpdateRemove_handlesAllThreeCasesInOneCall() {
        Habit habit = HabitTestDataFactory.aHabit();
        HabitSchedule monday = HabitTestDataFactory.aSchedule(DayOfWeek.MONDAY, LocalTime.of(6, 0), LocalTime.of(7, 0));
        HabitSchedule wednesday = HabitTestDataFactory.aSchedule(DayOfWeek.WEDNESDAY, LocalTime.of(7, 0), LocalTime.of(8, 0));
        habit.addSchedule(monday);
        habit.addSchedule(wednesday);

        // MONDAY: time changed, should survive as the same row.
        // WEDNESDAY: omitted, should be removed.
        // FRIDAY: new, should be added.
        HabitSchedule incomingMonday = HabitTestDataFactory.aSchedule(DayOfWeek.MONDAY, LocalTime.of(5, 0), LocalTime.of(6, 0));
        HabitSchedule incomingFriday = HabitTestDataFactory.aSchedule(DayOfWeek.FRIDAY, LocalTime.of(18, 0), LocalTime.of(19, 0));

        habit.syncSchedule(List.of(incomingMonday, incomingFriday));

        assertThat(habit.getHabitSchedule())
            .extracting(HabitSchedule::getDayOfWeek)
            .containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);

        HabitSchedule survivingMonday = habit.getHabitSchedule().stream()
            .filter(s -> s.getDayOfWeek() == DayOfWeek.MONDAY)
            .findFirst().orElseThrow();
        assertThat(survivingMonday).isSameAs(monday);
        assertThat(survivingMonday.getStartTime()).isEqualTo(LocalTime.of(5, 0));

        HabitSchedule newFriday = habit.getHabitSchedule().stream()
            .filter(s -> s.getDayOfWeek() == DayOfWeek.FRIDAY)
            .findFirst().orElseThrow();
        assertThat(newFriday.getHabit()).isSameAs(habit);
    }

    @Test
    void syncSchedule_emptyIncomingList_removesAllExistingSchedules() {
        Habit habit = HabitTestDataFactory.aHabit();
        habit.addSchedule(HabitTestDataFactory.aSchedule(DayOfWeek.MONDAY, LocalTime.of(6, 0), LocalTime.of(7, 0)));
        habit.addSchedule(HabitTestDataFactory.aSchedule(DayOfWeek.WEDNESDAY, LocalTime.of(7, 0), LocalTime.of(8, 0)));

        habit.syncSchedule(List.of());

        assertThat(habit.getHabitSchedule()).isEmpty();
    }

    @Test
    void syncSchedule_startingFromNoSchedules_addsAllIncoming() {
        Habit habit = HabitTestDataFactory.aHabit();

        HabitSchedule monday = HabitTestDataFactory.aSchedule(DayOfWeek.MONDAY, LocalTime.of(6, 0), LocalTime.of(7, 0));
        HabitSchedule wednesday = HabitTestDataFactory.aSchedule(DayOfWeek.WEDNESDAY, LocalTime.of(7, 0), LocalTime.of(8, 0));

        habit.syncSchedule(List.of(monday, wednesday));

        assertThat(habit.getHabitSchedule()).hasSize(2);
        assertThat(habit.getHabitSchedule()).allSatisfy(s -> assertThat(s.getHabit()).isSameAs(habit));
    }

    @Test
    void syncSchedule_duplicateDayInIncomingList_throwsDuplicateScheduleException() {
        Habit habit = HabitTestDataFactory.aHabit();

        HabitSchedule mondayMorning = HabitTestDataFactory.aSchedule(DayOfWeek.MONDAY, LocalTime.of(6, 0), LocalTime.of(7, 0));
        HabitSchedule mondayEvening = HabitTestDataFactory.aSchedule(DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(19, 0));

        assertThatThrownBy(() -> habit.syncSchedule(List.of(mondayMorning, mondayEvening)))
            .isInstanceOf(DuplicateScheduleException.class);
    }

    @Test
    void syncSchedule_duplicateIncomingDayMatchingAnExistingDay_alsoThrows() {
        Habit habit = HabitTestDataFactory.aHabit();
        habit.addSchedule(HabitTestDataFactory.aSchedule(DayOfWeek.MONDAY, LocalTime.of(6, 0), LocalTime.of(7, 0)));

        HabitSchedule incomingA = HabitTestDataFactory.aSchedule(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(9, 0));
        HabitSchedule incomingB = HabitTestDataFactory.aSchedule(DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(11, 0));

        assertThatThrownBy(() -> habit.syncSchedule(List.of(incomingA, incomingB)))
            .isInstanceOf(DuplicateScheduleException.class);
    }

    @Test
    void addLog_setsBackReferenceToThisHabit() {
        Habit habit = HabitTestDataFactory.aHabit();
        HabitLog log = new HabitLog();
        log.setCompletionDate(java.time.LocalDate.of(2026, 1, 5));

        habit.addLog(log);

        assertThat(habit.getHabitLog()).containsExactly(log);
        assertThat(log.getHabit()).isSameAs(habit);
    }

    @Test
    void recordCompletion_streakContinues_incrementsCurrentStreak() {
        Habit habit = HabitTestDataFactory.aHabit();
        habit.setCurrentStreak(3);
        habit.setMaxStreak(3);
        habit.setXpPerCompletion(10);
        habit.setTotalXpEarned(30);

        habit.recordCompletion(true);

        assertThat(habit.getCurrentStreak()).isEqualTo(4);
        assertThat(habit.getMaxStreak()).isEqualTo(4);
        assertThat(habit.getTotalXpEarned()).isEqualTo(40);
    }

    @Test
    void recordCompletion_streakBroken_resetsCurrentStreakToOne() {
        Habit habit = HabitTestDataFactory.aHabit();
        habit.setCurrentStreak(5);
        habit.setMaxStreak(5);

        habit.recordCompletion(false);

        assertThat(habit.getCurrentStreak()).isEqualTo(1);
    }

    @Test
    void recordCompletion_streakReset_doesNotLowerMaxStreak() {
        Habit habit = HabitTestDataFactory.aHabit();
        habit.setCurrentStreak(10);
        habit.setMaxStreak(10);

        habit.recordCompletion(false);

        assertThat(habit.getCurrentStreak()).isEqualTo(1);
        assertThat(habit.getMaxStreak())
            .as("max streak should remain the historical peak, not drop when the current streak resets")
            .isEqualTo(10);
    }

    @Test
    void recordCompletion_alwaysAddsXpPerCompletion_regardlessOfStreakOutcome() {
        Habit habit = HabitTestDataFactory.aHabit();
        habit.setXpPerCompletion(15);
        habit.setTotalXpEarned(0);

        habit.recordCompletion(false);

        assertThat(habit.getTotalXpEarned()).isEqualTo(15);
    }

    @Test
    void addSchedule_dayAlreadyExists_throwsDuplicateScheduleException() {
        Habit habit = HabitTestDataFactory.aHabit();
        habit.addSchedule(HabitTestDataFactory.aSchedule(DayOfWeek.MONDAY, LocalTime.of(6, 0), LocalTime.of(7, 0)));

        HabitSchedule secondMonday = HabitTestDataFactory.aSchedule(DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(19, 0));

        assertThatThrownBy(() -> habit.addSchedule(secondMonday))
            .isInstanceOf(DuplicateScheduleException.class);
    }
}
