package com.habitat.server.repository;

import com.habitat.server.model.HabitSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.DayOfWeek;
import java.util.List;

public interface HabitScheduleRepository extends JpaRepository<HabitSchedule, Long> {
    List<HabitSchedule> findByDayOfWeekAndEffectiveUntilIsNullOrderByStartTime(DayOfWeek dayOfWeek);
}
