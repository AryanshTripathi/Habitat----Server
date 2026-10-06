package com.habitat.server.dto;

import com.habitat.server.model.Habit;

import java.time.LocalDate;
import java.util.List;

public record HabitResponse(
    long id,
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
    List<HabitScheduleResponse> habitSchedule,
    LocalDate createdOn,
    int currentStreak,
    int maxStreak,
    int xpPerCompletion,
    int totalXpEarned,
    boolean completedToday,
    boolean scheduledToday,
    long totalDaysCompleted,
    List<Boolean> weekStatus
) {}
