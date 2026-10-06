package com.habitat.server.controller;

import com.habitat.server.dto.ScheduleTodayRowResponse;
import com.habitat.server.service.HabitService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/schedule")
public class ScheduleController {
    private final HabitService habitService;

    public ScheduleController(HabitService habitService) {
        this.habitService = habitService;
    }

    @GetMapping("/today")
    public List<ScheduleTodayRowResponse> getTodaySchedule() {
        return habitService.getTodaySchedule();
    }
}
