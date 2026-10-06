package com.habitat.server.dto;

import com.habitat.server.model.Habit;

import java.util.List;

public record HabitRequest(
    String name,
    String description,
    String notes,
    String icon,
    Habit.Color color,
    String unitLabel,
    Habit.FrequencyType frequencyType,
    boolean reminderEnabled,
    boolean isActive,
    List<String> tags,
    List<HabitScheduleRequest> habitSchedule
) {}
