package com.habitat.server.exception;

import com.habitat.server.model.HabitSchedule;

import java.time.DayOfWeek;
import java.util.List;

public class DuplicateScheduleException extends DuplicateException {
    public DuplicateScheduleException(String message) {
        super(message);
    }

    public DuplicateScheduleException(DayOfWeek day, String habitName) {
        super("Multiple Time slots present in " + day + " for habit: " + habitName);
    }

    public DuplicateScheduleException(List<HabitSchedule> schedules, String message) {
        super(message + "\nSchedules: " + schedules);
    }
}
