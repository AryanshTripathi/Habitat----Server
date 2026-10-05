package com.habitat.server.controller;

import com.habitat.server.model.Habit;
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
    public List<Habit> getAllHabits() { return habitService.getAllHabits(); }

    @GetMapping("/{id}")
    public Habit getHabitById(@PathVariable long id) {
        return habitService.getHabitById(id);
    }

    @PostMapping
    public Habit createHabit(@RequestBody Habit habit) {
        return habitService.createHabit(habit);
    }

    @PutMapping("/{id}")
    public Habit updateHabit(@PathVariable long id, @RequestBody Habit updatedHabit) {
        return habitService.updateHabit(id, updatedHabit);
    }

    @DeleteMapping("/{id}")
    public void deleteHabit(@PathVariable long id) {
        habitService.deleteHabit(id);
    }

    @PostMapping("/complete/{id}")
    public Habit completeHabit(@PathVariable long id) { return habitService.completeHabit(id); }

    @PostMapping("/undo/{id}")
    public Habit undoCompletion(@PathVariable long id) {
        return habitService.undoCompletion(id);
    }
}
