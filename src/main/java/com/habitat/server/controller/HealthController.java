package com.habitat.server.controller;

import com.habitat.server.repository.HabitRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private final HabitRepository habitRepository;

    public HealthController(HabitRepository habitRepository) {
        this.habitRepository = habitRepository;
    }

    @GetMapping("/health")
    public String health() {
        habitRepository.count(); // cheap SELECT count(*) - forces a real round-trip, keeps Neon's compute awake
        return "OK";
    }
}

