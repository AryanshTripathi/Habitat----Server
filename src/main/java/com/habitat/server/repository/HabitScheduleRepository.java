package com.habitat.server.repository;

import com.habitat.server.model.HabitLog;
import com.habitat.server.model.HabitSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HabitScheduleRepository extends  JpaRepository<HabitSchedule, Long> {
    
}

