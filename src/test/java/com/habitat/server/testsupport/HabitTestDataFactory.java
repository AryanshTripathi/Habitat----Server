package com.habitat.server.testsupport;

import com.habitat.server.dto.HabitRequest;
import com.habitat.server.dto.HabitResponse;
import com.habitat.server.dto.HabitScheduleRequest;
import com.habitat.server.model.Habit;
import com.habitat.server.model.HabitSchedule;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class HabitTestDataFactory {

    public static Habit aHabit() {
        Habit habit = new Habit();
        habit.setName("Exercise");
        habit.setColor(Habit.Color.BLUE);
        habit.setFrequencyType(Habit.FrequencyType.DAILY);
        habit.setXpPerCompletion(10);
        habit.setTotalXpEarned(0);
        habit.setCurrentStreak(0);
        habit.setMaxStreak(0);
        habit.setActive(true);
        habit.setTags(new ArrayList<>(List.of("health")));
        habit.setHabitSchedule(new ArrayList<>());
        habit.setHabitLog(new ArrayList<>());
        return habit;
    }

    public static HabitSchedule aSchedule(DayOfWeek day, LocalTime start, LocalTime end) {
        HabitSchedule schedule = new HabitSchedule();
        schedule.setDayOfWeek(day);
        schedule.setStartTime(start);
        schedule.setEndTime(end);
        return schedule;
    }

    /**
     * Builds a schedule row with an explicit effectiveFrom/effectiveUntil, bypassing
     * addSchedule/syncSchedule entirely. Used to hand-construct a historical ledger (including
     * already-closed rows) for tests that check how that ledger is *consumed* (e.g. the heatmap),
     * independent of whatever logic produced it.
     */
    public static HabitSchedule aScheduleWithHistory(DayOfWeek day, LocalTime start, LocalTime end,
                                                      LocalDate effectiveFrom, LocalDate effectiveUntil) {
        HabitSchedule schedule = aSchedule(day, start, end);
        schedule.setEffectiveFrom(effectiveFrom);
        schedule.setEffectiveUntil(effectiveUntil);
        return schedule;
    }

    public static HabitRequest aHabitRequest() {
        return new HabitRequest(
            "Exercise",
            "Daily workout",
            "",
            "dumbbell",
            Habit.Color.BLUE,
            "reps",
            Habit.FrequencyType.DAILY,
            true,
            true,
            new ArrayList<>(List.of("health")),
            new ArrayList<>()
        );
    }

    public static HabitScheduleRequest aScheduleRequest(DayOfWeek day, LocalTime start, LocalTime end) {
        return new HabitScheduleRequest(day, start, end);
    }

    /**
     * Habit.id deliberately has no public setter (preventing client id-spoofing via the real API),
     * but unit tests need a way to make a manually-constructed Habit report a specific id, to match
     * whatever a mocked repository.findById(id) stub is keyed on. Reflection is the only way around
     * that without weakening the production-code protection.
     */
    public static void setId(Habit habit, long id) {
        try {
            java.lang.reflect.Field field = Habit.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(habit, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Habit.createdOn is managed by JPA auditing (@CreatedDate) and has no public setter, so it's
     * null on any habit built outside of a real persistence round-trip. Tests that exercise the
     * heatmap need a concrete createdOn (resolveDayStatus calls date.isBefore(habit.getCreatedOn())
     * for every cell), so this reflects it in the same way setId does.
     */
    public static void setCreatedOn(Habit habit, LocalDate createdOn) {
        try {
            java.lang.reflect.Field field = Habit.class.getDeclaredField("createdOn");
            field.setAccessible(true);
            field.set(habit, createdOn);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    public static HabitResponse aHabitResponse() {
        return new HabitResponse(
            1L, "Exercise", "Daily workout", "", "dumbbell", Habit.Color.BLUE, "reps",
            Habit.FrequencyType.DAILY, true, true, new ArrayList<>(List.of("health")),
            new ArrayList<>(), LocalDate.now(), 0, 0, 10, 0, false, true, 0L,
            new ArrayList<>(List.of(false, false, false, false, false, false, false))
        );
    }
}
