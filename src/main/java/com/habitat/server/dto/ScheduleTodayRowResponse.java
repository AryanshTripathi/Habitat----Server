package com.habitat.server.dto;

import com.habitat.server.model.Habit;

import java.time.LocalTime;

public record ScheduleTodayRowResponse(
    long habitId,
    String name,
    String icon,
    Habit.Color color,
    String description,
    LocalTime startTime,
    LocalTime endTime,
    boolean reminderEnabled,
    boolean completedToday
) {}
