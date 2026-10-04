package com.habitat.server.exception;

import java.time.LocalDate;

public class DuplicateCompletionException extends DuplicateException {
    public DuplicateCompletionException(String message) {
        super(message);
    }

    public DuplicateCompletionException(String habitName, LocalDate date) {
        super("Habit: " + habitName + " Already Marked Complete for Date: " + date);
    }
}
