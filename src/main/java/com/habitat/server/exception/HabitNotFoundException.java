package com.habitat.server.exception;

public class HabitNotFoundException extends NotFoundException {
    public HabitNotFoundException(String message) {
        super(message);
    }

    public HabitNotFoundException (long id) {
        super("No Habit found with id: " + id);
    }
}
