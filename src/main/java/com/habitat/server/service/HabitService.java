package com.habitat.server.service;

import com.habitat.server.exception.HabitNotFoundException;
import com.habitat.server.model.Habit;
import com.habitat.server.model.HabitSchedule;
import com.habitat.server.repository.HabitRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class HabitService {
    private final HabitRepository habitRepository;

    public HabitService(HabitRepository habitRepository) {
        this.habitRepository = habitRepository;
    }

    public List<Habit> getAllHabits() {
        return habitRepository.findAll();
    }

    public Habit getHabitById(long habitId) {
        return habitRepository.findById(habitId).orElseThrow(() -> new HabitNotFoundException(habitId));
    }

    public Habit createHabit(Habit habit) {
        if(habit.getHabitSchedule() != null) {
            List<HabitSchedule> incomingSchedules = new ArrayList<>(habit.getHabitSchedule());
            habit.getHabitSchedule().clear();
            for(HabitSchedule schedule : incomingSchedules) {
                habit.addSchedule(schedule);
            }
        }
        return habitRepository.save(habit);
    }

    public void deleteHabit(long habitId) {
        if (!habitRepository.existsById(habitId)) {
            throw new HabitNotFoundException("Habit Id to be deleted not found");
        }
        habitRepository.deleteById(habitId);
    }

    public Habit updateHabit(long id, Habit updatedHabit) {
        Habit existingHabit = habitRepository.findById(id).orElseThrow(() -> new HabitNotFoundException(id));

        existingHabit.setColor(updatedHabit.getColor());
        existingHabit.setXpPerCompletion(updatedHabit.getXpPerCompletion());
        existingHabit.setTags(updatedHabit.getTags());
        existingHabit.setFrequencyType(updatedHabit.getFrequencyType());

        if(updatedHabit.getHabitSchedule() != null) {
            existingHabit.syncSchedule(updatedHabit.getHabitSchedule());
        }
        return habitRepository.save(existingHabit);
    }
}
