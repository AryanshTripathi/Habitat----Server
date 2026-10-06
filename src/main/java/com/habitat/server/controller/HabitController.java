package com.habitat.server.controller;

import com.habitat.server.dto.HabitDetailResponse;
import com.habitat.server.dto.HabitRequest;
import com.habitat.server.dto.HabitResponse;
import com.habitat.server.service.HabitService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/habits")
public class HabitController {
    private final HabitService habitService;
    public HabitController(HabitService habitService) {
        this.habitService = habitService;
    }

    @GetMapping
    public List<HabitResponse> getAllHabits() { return habitService.getAllHabits(); }

    @GetMapping("/{id}")
    public HabitDetailResponse getHabitById(@PathVariable long id) {
        return habitService.getHabitById(id);
    }

    @PostMapping
    public HabitResponse createHabit(@RequestBody HabitRequest habit) {
        return habitService.createHabit(habit);
    }

    @PutMapping("/{id}")
    public HabitResponse updateHabit(@PathVariable long id, @RequestBody HabitRequest updatedHabit) {
        return habitService.updateHabit(id, updatedHabit);
    }

    @DeleteMapping("/{id}")
    public void deleteHabit(@PathVariable long id) {
        habitService.deleteHabit(id);
    }

    @PostMapping("/complete/{id}")
    public HabitResponse completeHabit(@PathVariable long id) { return habitService.completeHabit(id); }

    @PostMapping("/undo/{id}")
    public HabitResponse undoCompletion(@PathVariable long id) {
        return habitService.undoCompletion(id);
    }
}
