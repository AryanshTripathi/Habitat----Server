package com.habitat.server.testsupport;

import com.habitat.server.model.Habit;
import com.habitat.server.model.HabitSchedule;

import java.time.DayOfWeek;
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
}
