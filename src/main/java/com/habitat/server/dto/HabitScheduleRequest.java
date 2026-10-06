package com.habitat.server.dto;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record HabitScheduleRequest(
    DayOfWeek dayOfWeek,
    LocalTime startTime,
    LocalTime endTime
) {}
