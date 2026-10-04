package com.habitat.server.exception;

public class HabitScheduleNotFoundException extends NotFoundException {
    public HabitScheduleNotFoundException(String message) {
        super(message);
    }

    public HabitScheduleNotFoundException(long habitId) {
        super("No Schedule found for today for habit Id: " + habitId);
    }
}
