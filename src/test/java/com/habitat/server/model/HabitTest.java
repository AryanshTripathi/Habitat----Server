package com.habitat.server.model;

import com.habitat.server.exception.DuplicateScheduleException;
import com.habitat.server.testsupport.HabitTestDataFactory;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HabitTest {

    @Test
    void addSchedule_setsBackReferenceToThisHabit() {
        Habit habit = HabitTestDataFactory.aHabit();
        HabitSchedule schedule = HabitTestDataFactory.aSchedule(DayOfWeek.MONDAY, LocalTime.of(6, 0), LocalTime.of(7, 0));

        habit.addSchedule(schedule, LocalDate.now());

        assertThat(habit.getHabitSchedule()).containsExactly(schedule);
        assertThat(schedule.getHabit()).isSameAs(habit);
    }

    @Test
    void syncSchedule_existingDayPresentInIncoming_updatesInPlace_keepsSameInstance() {
        Habit habit = HabitTestDataFactory.aHabit();
        HabitSchedule monday = HabitTestDataFactory.aSchedule(DayOfWeek.MONDAY, LocalTime.of(6, 0), LocalTime.of(7, 0));
        habit.addSchedule(monday, LocalDate.now());

        HabitSchedule incomingMonday = HabitTestDataFactory.aSchedule(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(9, 0));

        habit.syncSchedule(List.of(incomingMonday), LocalDate.now());

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
        habit.addSchedule(monday, LocalDate.now());

        HabitSchedule incomingMonday = HabitTestDataFactory.aSchedule(DayOfWeek.MONDAY, LocalTime.of(6, 0), LocalTime.of(7, 0));
        HabitSchedule incomingWednesday = HabitTestDataFactory.aSchedule(DayOfWeek.WEDNESDAY, LocalTime.of(7, 0), LocalTime.of(8, 0));

        habit.syncSchedule(List.of(incomingMonday, incomingWednesday), LocalDate.now());

        assertThat(habit.getHabitSchedule()).hasSize(2);
        assertThat(habit.getHabitSchedule())
            .filteredOn(s -> s.getDayOfWeek() == DayOfWeek.WEDNESDAY)
            .first()
            .satisfies(s -> assertThat(s.getHabit()).isSameAs(habit));
    }

    @Test
    void syncSchedule_dayMissingFromIncoming_removesItFromActiveSchedule_butKeepsItInHistory() {
        Habit habit = HabitTestDataFactory.aHabit();
        HabitSchedule monday = HabitTestDataFactory.aSchedule(DayOfWeek.MONDAY, LocalTime.of(6, 0), LocalTime.of(7, 0));
        HabitSchedule wednesday = HabitTestDataFactory.aSchedule(DayOfWeek.WEDNESDAY, LocalTime.of(7, 0), LocalTime.of(8, 0));
        habit.addSchedule(monday, LocalDate.now());
        habit.addSchedule(wednesday, LocalDate.now());

        HabitSchedule incomingMonday = HabitTestDataFactory.aSchedule(DayOfWeek.MONDAY, LocalTime.of(6, 0), LocalTime.of(7, 0));

        // Only MONDAY is sent back - WEDNESDAY should drop out of the *active* schedule
        habit.syncSchedule(List.of(incomingMonday), LocalDate.now());

        assertThat(habit.getActiveHabitSchedule())
            .as("removed day must no longer count as currently scheduled")
            .extracting(HabitSchedule::getDayOfWeek)
            .containsExactly(DayOfWeek.MONDAY);

        assertThat(habit.getHabitSchedule())
            .as("no data loss - the closed row must still exist in the full history")
            .extracting(HabitSchedule::getDayOfWeek)
            .containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY);
        assertThat(wednesday.getEffectiveUntil())
            .as("the closed row must record when it stopped being active")
            .isEqualTo(LocalDate.now());
    }

    @Test
    void syncSchedule_combinedAddUpdateRemove_handlesAllThreeCasesInOneCall() {
        Habit habit = HabitTestDataFactory.aHabit();
        HabitSchedule monday = HabitTestDataFactory.aSchedule(DayOfWeek.MONDAY, LocalTime.of(6, 0), LocalTime.of(7, 0));
        HabitSchedule wednesday = HabitTestDataFactory.aSchedule(DayOfWeek.WEDNESDAY, LocalTime.of(7, 0), LocalTime.of(8, 0));
        habit.addSchedule(monday, LocalDate.now());
        habit.addSchedule(wednesday, LocalDate.now());

        // MONDAY: time changed, should survive as the same row.
        // WEDNESDAY: omitted, should be removed.
        // FRIDAY: new, should be added.
        HabitSchedule incomingMonday = HabitTestDataFactory.aSchedule(DayOfWeek.MONDAY, LocalTime.of(5, 0), LocalTime.of(6, 0));
        HabitSchedule incomingFriday = HabitTestDataFactory.aSchedule(DayOfWeek.FRIDAY, LocalTime.of(18, 0), LocalTime.of(19, 0));

        habit.syncSchedule(List.of(incomingMonday, incomingFriday), LocalDate.now());

        assertThat(habit.getActiveHabitSchedule())
            .extracting(HabitSchedule::getDayOfWeek)
            .containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);
        assertThat(habit.getHabitSchedule())
            .as("removed WEDNESDAY must still exist as closed history, not deleted")
            .extracting(HabitSchedule::getDayOfWeek)
            .containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY);

        HabitSchedule survivingMonday = habit.getActiveHabitSchedule().stream()
            .filter(s -> s.getDayOfWeek() == DayOfWeek.MONDAY)
            .findFirst().orElseThrow();
        assertThat(survivingMonday).isSameAs(monday);
        assertThat(survivingMonday.getStartTime()).isEqualTo(LocalTime.of(5, 0));

        HabitSchedule newFriday = habit.getActiveHabitSchedule().stream()
            .filter(s -> s.getDayOfWeek() == DayOfWeek.FRIDAY)
            .findFirst().orElseThrow();
        assertThat(newFriday.getHabit()).isSameAs(habit);
    }

    @Test
    void syncSchedule_emptyIncomingList_closesAllActiveSchedules_butKeepsThemInHistory() {
        Habit habit = HabitTestDataFactory.aHabit();
        habit.addSchedule(HabitTestDataFactory.aSchedule(DayOfWeek.MONDAY, LocalTime.of(6, 0), LocalTime.of(7, 0)), LocalDate.now());
        habit.addSchedule(HabitTestDataFactory.aSchedule(DayOfWeek.WEDNESDAY, LocalTime.of(7, 0), LocalTime.of(8, 0)), LocalDate.now());

        habit.syncSchedule(List.of(), LocalDate.now());

        assertThat(habit.getActiveHabitSchedule()).isEmpty();
        assertThat(habit.getHabitSchedule()).hasSize(2);
        assertThat(habit.getHabitSchedule()).allSatisfy(s -> assertThat(s.getEffectiveUntil()).isEqualTo(LocalDate.now()));
    }

    @Test
    void syncSchedule_startingFromNoSchedules_addsAllIncoming() {
        Habit habit = HabitTestDataFactory.aHabit();

        HabitSchedule monday = HabitTestDataFactory.aSchedule(DayOfWeek.MONDAY, LocalTime.of(6, 0), LocalTime.of(7, 0));
        HabitSchedule wednesday = HabitTestDataFactory.aSchedule(DayOfWeek.WEDNESDAY, LocalTime.of(7, 0), LocalTime.of(8, 0));

        habit.syncSchedule(List.of(monday, wednesday), LocalDate.now());

        assertThat(habit.getHabitSchedule()).hasSize(2);
        assertThat(habit.getHabitSchedule()).allSatisfy(s -> assertThat(s.getHabit()).isSameAs(habit));
    }

    @Test
    void syncSchedule_duplicateDayInIncomingList_throwsDuplicateScheduleException() {
        Habit habit = HabitTestDataFactory.aHabit();

        HabitSchedule mondayMorning = HabitTestDataFactory.aSchedule(DayOfWeek.MONDAY, LocalTime.of(6, 0), LocalTime.of(7, 0));
        HabitSchedule mondayEvening = HabitTestDataFactory.aSchedule(DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(19, 0));

        assertThatThrownBy(() -> habit.syncSchedule(List.of(mondayMorning, mondayEvening), LocalDate.now()))
            .isInstanceOf(DuplicateScheduleException.class);
    }

    @Test
    void syncSchedule_duplicateIncomingDayMatchingAnExistingDay_alsoThrows() {
        Habit habit = HabitTestDataFactory.aHabit();
        habit.addSchedule(HabitTestDataFactory.aSchedule(DayOfWeek.MONDAY, LocalTime.of(6, 0), LocalTime.of(7, 0)), LocalDate.now());

        HabitSchedule incomingA = HabitTestDataFactory.aSchedule(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(9, 0));
        HabitSchedule incomingB = HabitTestDataFactory.aSchedule(DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(11, 0));

        assertThatThrownBy(() -> habit.syncSchedule(List.of(incomingA, incomingB), LocalDate.now()))
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
    void undoCompletion_removesLogAndAppliesRecomputedStreak_leavesMaxStreakUntouched() {
        Habit habit = HabitTestDataFactory.aHabit();
        habit.setXpPerCompletion(10);
        habit.setTotalXpEarned(10);
        habit.setCurrentStreak(1);
        habit.setMaxStreak(5); // deliberately higher than anything this undo should touch

        HabitLog log = new HabitLog();
        log.setCompletionDate(java.time.LocalDate.of(2026, 1, 5));
        habit.addLog(log);

        habit.undoCompletion(log, 0);

        assertThat(habit.getHabitLog()).doesNotContain(log);
        assertThat(habit.getCurrentStreak()).isEqualTo(0);
        assertThat(habit.getTotalXpEarned()).isEqualTo(0);
        assertThat(habit.getMaxStreak())
            .as("undo must never touch maxStreak - it's a permanent high-water mark by design")
            .isEqualTo(5);
    }

    @Test
    void addSchedule_dayAlreadyExists_throwsDuplicateScheduleException() {
        Habit habit = HabitTestDataFactory.aHabit();
        habit.addSchedule(HabitTestDataFactory.aSchedule(DayOfWeek.MONDAY, LocalTime.of(6, 0), LocalTime.of(7, 0)), LocalDate.now());

        HabitSchedule secondMonday = HabitTestDataFactory.aSchedule(DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(19, 0));

        assertThatThrownBy(() -> habit.addSchedule(secondMonday, LocalDate.now()))
            .isInstanceOf(DuplicateScheduleException.class);
    }

    @Test
    void addSchedule_setsEffectiveFromToToday() {
        Habit habit = HabitTestDataFactory.aHabit();
        HabitSchedule schedule = HabitTestDataFactory.aSchedule(DayOfWeek.MONDAY, LocalTime.of(6, 0), LocalTime.of(7, 0));

        habit.addSchedule(schedule, LocalDate.now());

        assertThat(schedule.getEffectiveFrom()).isEqualTo(LocalDate.now());
    }

    @Test
    void addSchedule_dayWasPreviouslyRemoved_canBeReAdded() {
        // Regression test: addSchedule's duplicate check used to look at the raw history
        // (including closed rows), permanently blocking a removed day from ever coming back.
        Habit habit = HabitTestDataFactory.aHabit();
        HabitSchedule originalMonday = HabitTestDataFactory.aSchedule(DayOfWeek.MONDAY, LocalTime.of(6, 0), LocalTime.of(7, 0));
        habit.addSchedule(originalMonday, LocalDate.now());
        habit.syncSchedule(List.of(), LocalDate.now()); // closes MONDAY

        HabitSchedule newMonday = HabitTestDataFactory.aSchedule(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0));

        assertThatCode(() -> habit.addSchedule(newMonday, LocalDate.now())).doesNotThrowAnyException();
        assertThat(habit.getActiveHabitSchedule()).containsExactly(newMonday);
        assertThat(habit.getHabitSchedule())
            .as("the original closed row must still be present as history")
            .contains(originalMonday, newMonday);
    }

    @Test
    void syncSchedule_dayWasPreviouslyRemoved_canBeReAddedWithoutCrashing() {
        // Regression test: once a day could be re-added (see above), building
        // existingScheduleMap from the raw history would find two rows sharing the same
        // dayOfWeek (one closed, one active) and Collectors.toMap would throw on the duplicate key.
        Habit habit = HabitTestDataFactory.aHabit();
        habit.addSchedule(HabitTestDataFactory.aSchedule(DayOfWeek.MONDAY, LocalTime.of(6, 0), LocalTime.of(7, 0)), LocalDate.now());
        habit.syncSchedule(List.of(), LocalDate.now()); // closes MONDAY

        HabitSchedule newMonday = HabitTestDataFactory.aSchedule(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0));
        HabitSchedule wednesday = HabitTestDataFactory.aSchedule(DayOfWeek.WEDNESDAY, LocalTime.of(7, 0), LocalTime.of(8, 0));

        assertThatCode(() -> habit.syncSchedule(List.of(newMonday, wednesday), LocalDate.now())).doesNotThrowAnyException();
        assertThat(habit.getActiveHabitSchedule())
            .extracting(HabitSchedule::getDayOfWeek)
            .containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY);
        assertThat(habit.getHabitSchedule())
            .as("closed MONDAY row + re-added MONDAY row + new WEDNESDAY row")
            .hasSize(3);
    }

    @Test
    void syncSchedule_doesNotReStampEffectiveUntilOnAlreadyClosedRows() {
        // Regression test for the critical data-corruption bug: syncSchedule's close-out loop
        // used to iterate the raw history and overwrite effectiveUntil on every row not present
        // in the incoming list - including rows that had already been closed in the past - every
        // time syncSchedule ran for any reason.
        Habit habit = HabitTestDataFactory.aHabit();
        HabitSchedule wednesday = HabitTestDataFactory.aSchedule(DayOfWeek.WEDNESDAY, LocalTime.of(7, 0), LocalTime.of(8, 0));
        habit.addSchedule(wednesday, LocalDate.now());
        habit.syncSchedule(List.of(), LocalDate.now()); // closes WEDNESDAY, effectiveUntil = today

        // Simulate time having passed since that closure actually happened.
        LocalDate trueClosureDate = LocalDate.now().minusWeeks(3);
        wednesday.setEffectiveUntil(trueClosureDate);

        // Some unrelated later update runs syncSchedule again.
        habit.syncSchedule(List.of(HabitTestDataFactory.aSchedule(DayOfWeek.FRIDAY, LocalTime.of(18, 0), LocalTime.of(19, 0))), LocalDate.now());

        assertThat(wednesday.getEffectiveUntil())
            .as("an already-closed row's true closure date must never be overwritten by a later sync")
            .isEqualTo(trueClosureDate);
    }
}
