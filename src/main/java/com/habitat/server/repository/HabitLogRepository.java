package com.habitat.server.repository;

import com.habitat.server.model.Habit;
import com.habitat.server.model.HabitLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Optional;

public interface HabitLogRepository extends  JpaRepository<HabitLog, Long> {
    boolean existsByHabit_IdAndCompletionDate(Long habitId, LocalDate completionDate);
    Optional<HabitLog> findByHabit_IdAndCompletionDate(Long habitId, LocalDate completionDate);
    long countByHabit_Id(Long habitId);
}
