package com.habitat.server.repository;

import com.habitat.server.model.HabitLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HabitLogRepository extends  JpaRepository<HabitLog, Long> {
}
