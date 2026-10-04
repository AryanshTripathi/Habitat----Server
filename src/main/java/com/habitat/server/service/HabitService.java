package com.habitat.server.service;

import com.habitat.server.exception.DuplicateCompletionException;
import com.habitat.server.exception.HabitNotFoundException;
import com.habitat.server.exception.HabitScheduleNotFoundException;
import com.habitat.server.model.Habit;
import com.habitat.server.model.HabitLog;
import com.habitat.server.model.HabitSchedule;
import com.habitat.server.repository.HabitLogRepository;
import com.habitat.server.repository.HabitRepository;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class HabitService {
    private final HabitRepository habitRepository;
    private final HabitLogRepository habitLogRepository;

    public HabitService(HabitRepository habitRepository, HabitLogRepository habitLogRepository) {
        this.habitRepository = habitRepository;
        this.habitLogRepository = habitLogRepository;
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

    public Habit completeHabit(long id) {
        Habit habit = habitRepository.findById(id).orElseThrow(() -> new HabitNotFoundException(id));
        LocalDate completionDate = LocalDate.now();

        boolean duplicateCompletion = habitLogRepository.existsByHabit_IdAndCompletionDate(id, completionDate);
        if(duplicateCompletion) {
            throw new DuplicateCompletionException(habit.getName(), completionDate);
        }

        boolean isHabitScheduledForToday = habit.getHabitSchedule().stream().anyMatch(s -> s.getDayOfWeek() == completionDate.getDayOfWeek());

        if(!isHabitScheduledForToday) {
            throw new HabitScheduleNotFoundException(id);
        }

        Set<DayOfWeek> scheduledDays = habit.getHabitSchedule().stream().map(HabitSchedule::getDayOfWeek).collect(Collectors.toSet());
        LocalDate previousScheduledDate = completionDate.minusDays(1);

        while(!scheduledDays.contains(previousScheduledDate.getDayOfWeek())) {
            previousScheduledDate = previousScheduledDate.minusDays(1);
        }

        boolean isStreakContinued = habitLogRepository.existsByHabit_IdAndCompletionDate(id, previousScheduledDate);

        HabitLog log = new HabitLog();
        log.setCompletionDate(completionDate);
        habit.addLog(log);
        habit.recordCompletion(isStreakContinued);

        return habitRepository.save(habit);
    }
}
