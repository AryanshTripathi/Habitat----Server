package com.habitat.server.dto;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record HabitScheduleResponse(
    long id,
    DayOfWeek dayOfWeek,
    LocalTime startTime,
    LocalTime endTime
) {}
