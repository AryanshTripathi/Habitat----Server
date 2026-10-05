package com.habitat.server.exception;

import java.time.LocalDate;

public class HabitLogNotFoundException extends NotFoundException {
    public HabitLogNotFoundException(String message) {
        super(message);
    }

    public HabitLogNotFoundException(long habitId, LocalDate date) {
        super("No completion logged for habit id " + habitId + " on " + date + " to undo");
    }
}
