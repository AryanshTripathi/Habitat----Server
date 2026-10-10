package com.habitat.server.service;

import com.habitat.server.dto.DayStatus;
import com.habitat.server.dto.HabitDetailResponse;
import com.habitat.server.dto.HabitRequest;
import com.habitat.server.dto.HabitResponse;
import com.habitat.server.dto.HabitScheduleRequest;
import com.habitat.server.dto.HabitScheduleResponse;
import com.habitat.server.dto.ScheduleTodayRowResponse;
import com.habitat.server.exception.DuplicateCompletionException;
import com.habitat.server.exception.HabitLogNotFoundException;
import com.habitat.server.exception.HabitNotFoundException;
import com.habitat.server.exception.HabitScheduleNotFoundException;
import com.habitat.server.model.Habit;
import com.habitat.server.model.HabitLog;
import com.habitat.server.model.HabitSchedule;
import com.habitat.server.repository.HabitLogRepository;
import com.habitat.server.repository.HabitRepository;
import com.habitat.server.repository.HabitScheduleRepository;
import com.habitat.server.time.UserClock;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class HabitService {
    private static final int DEFAULT_XP_PER_COMPLETION = 10;
    private static final int HEATMAP_WEEK_COUNT = 5;

    private final HabitRepository habitRepository;
    private final HabitLogRepository habitLogRepository;
    private final HabitScheduleRepository habitScheduleRepository;
    private final UserClock userClock;

    public HabitService(HabitRepository habitRepository, HabitLogRepository habitLogRepository, HabitScheduleRepository habitScheduleRepository, UserClock userClock) {
        this.habitRepository = habitRepository;
        this.habitLogRepository = habitLogRepository;
        this.habitScheduleRepository = habitScheduleRepository;
        this.userClock = userClock;
    }

    public List<HabitResponse> getAllHabits() {
        return habitRepository.findAll().stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    public HabitDetailResponse getHabitById(long habitId) {
        Habit habit = habitRepository.findById(habitId).orElseThrow(() -> new HabitNotFoundException(habitId));
        return toDetailResponse(habit);
    }

    public HabitResponse createHabit(HabitRequest request) {
        Habit habit = new Habit();
        habit.setName(request.name());
        habit.setXpPerCompletion(DEFAULT_XP_PER_COMPLETION);
        applyEditableFields(habit, request);

        habit.setHabitSchedule(new ArrayList<>());
        habit.setHabitLog(new ArrayList<>());
        List<HabitSchedule> schedules = toScheduleEntities(request.habitSchedule());
        if (schedules != null) {
            for (HabitSchedule schedule : schedules) {
                habit.addSchedule(schedule, userClock.today());
            }
        }

        Habit saved = habitRepository.save(habit);
        return toResponse(saved);
    }

    public HabitResponse updateHabit(long id, HabitRequest request) {
        Habit existingHabit = habitRepository.findById(id).orElseThrow(() -> new HabitNotFoundException(id));

        applyEditableFields(existingHabit, request);

        List<HabitSchedule> schedules = toScheduleEntities(request.habitSchedule());
        if (schedules != null) {
            existingHabit.syncSchedule(schedules, userClock.today());
        }

        Habit saved = habitRepository.save(existingHabit);
        return toResponse(saved);
    }

    public void deleteHabit(long habitId) {
        if (!habitRepository.existsById(habitId)) {
            throw new HabitNotFoundException("Habit Id to be deleted not found");
        }
        habitRepository.deleteById(habitId);
    }

    public HabitResponse completeHabit(long id) {
        Habit habit = habitRepository.findById(id).orElseThrow(() -> new HabitNotFoundException(id));
        LocalDate completionDate = userClock.today();

        boolean duplicateCompletion = habitLogRepository.existsByHabit_IdAndCompletionDate(id, completionDate);
        if (duplicateCompletion) {
            throw new DuplicateCompletionException(habit.getName(), completionDate);
        }

        Set<DayOfWeek> scheduledDays = scheduledDaysOf(habit);
        boolean isHabitScheduledForToday = scheduledDays.contains(completionDate.getDayOfWeek());

        if (!isHabitScheduledForToday) {
            throw new HabitScheduleNotFoundException(id);
        }

        LocalDate previousScheduledDate = walkBackToNearestScheduledDay(completionDate, scheduledDays);
        boolean isStreakContinued = habitLogRepository.existsByHabit_IdAndCompletionDate(id, previousScheduledDate);

        HabitLog log = new HabitLog();
        log.setCompletionDate(completionDate);
        habit.addLog(log);
        habit.recordCompletion(isStreakContinued);

        Habit saved = habitRepository.save(habit);
        return toResponse(saved);
    }

    public HabitResponse undoCompletion(long habitId) {
        Habit habit = habitRepository.findById(habitId).orElseThrow(() -> new HabitNotFoundException(habitId));
        LocalDate today = userClock.today();

        HabitLog todayLog = habitLogRepository.findByHabit_IdAndCompletionDate(habitId, today)
            .orElseThrow(() -> new HabitLogNotFoundException(habitId, today));

        Set<DayOfWeek> scheduledDays = scheduledDaysOf(habit);

        int recomputedStreak = 0;
        LocalDate cursor = today;
        while (!habit.getActiveHabitSchedule().isEmpty()) {
            cursor = walkBackToNearestScheduledDay(cursor, scheduledDays);
            if (habitLogRepository.existsByHabit_IdAndCompletionDate(habitId, cursor)) {
                recomputedStreak++;
            } else {
                break;
            }
        }

        habit.undoCompletion(todayLog, recomputedStreak);
        Habit saved = habitRepository.save(habit);
        return toResponse(saved);
    }

    public List<ScheduleTodayRowResponse> getTodaySchedule() {
        LocalDate today = userClock.today();
        List<HabitSchedule> todaySchedules = habitScheduleRepository.findByDayOfWeekAndEffectiveUntilIsNullOrderByStartTime(today.getDayOfWeek());

        return todaySchedules.stream()
            .map(schedule -> {
                Habit habit = schedule.getHabit();
                boolean completedToday = habitLogRepository.existsByHabit_IdAndCompletionDate(habit.getId(), today);
                return new ScheduleTodayRowResponse(
                    habit.getId(),
                    habit.getName(),
                    habit.getIcon(),
                    habit.getColor(),
                    habit.getDescription(),
                    schedule.getStartTime(),
                    schedule.getEndTime(),
                    habit.isReminderEnabled(),
                    completedToday
                );
            })
            .collect(Collectors.toList());
    }

    // --- field mapping helpers ---

    private void applyEditableFields(Habit habit, HabitRequest request) {
        habit.setDescription(request.description());
        habit.setNotes(request.notes());
        habit.setUnitLabel(request.unitLabel());
        habit.setReminderEnabled(request.reminderEnabled());
        habit.setActive(request.isActive());
        habit.setColor(request.color());
        habit.setFrequencyType(request.frequencyType());
        habit.setTags(request.tags());
        habit.setIcon(request.icon());
    }

    private List<HabitSchedule> toScheduleEntities(List<HabitScheduleRequest> requests) {
        if (requests == null) {
            return null;
        }
        return requests.stream()
            .map(r -> {
                HabitSchedule schedule = new HabitSchedule();
                schedule.setDayOfWeek(r.dayOfWeek());
                schedule.setStartTime(r.startTime());
                schedule.setEndTime(r.endTime());
                return schedule;
            })
            .collect(Collectors.toList());
    }

    // --- response-building helpers ---

    private HabitResponse toResponse(Habit habit) {
        long habitId = habit.getId();
        LocalDate today = userClock.today();
        Set<DayOfWeek> scheduledDays = scheduledDaysOf(habit);

        boolean completedToday = habitLogRepository.existsByHabit_IdAndCompletionDate(habitId, today);
        boolean scheduledToday = scheduledDays.contains(today.getDayOfWeek());
        long totalDaysCompleted = habitLogRepository.countByHabit_Id(habitId);
        int effectiveCurrentStreak = computeEffectiveCurrentStreak(habit, completedToday, scheduledDays, today);
        List<Boolean> weekStatus = computeWeekStatus(habitId, today);
        List<HabitScheduleResponse> scheduleResponses = toScheduleResponses(habit);

        return new HabitResponse(
            habit.getId(),
            habit.getName(),
            habit.getDescription(),
            habit.getNotes(),
            habit.getIcon(),
            habit.getColor(),
            habit.getUnitLabel(),
            habit.getFrequencyType(),
            habit.isReminderEnabled(),
            habit.isActive(),
            habit.getTags(),
            scheduleResponses,
            habit.getCreatedOn(),
            effectiveCurrentStreak,
            habit.getMaxStreak(),
            habit.getXpPerCompletion(),
            habit.getTotalXpEarned(),
            completedToday,
            scheduledToday,
            totalDaysCompleted,
            weekStatus
        );
    }

    private HabitDetailResponse toDetailResponse(Habit habit) {
        HabitResponse base = toResponse(habit);
        List<List<DayStatus>> heatmap = computeHeatmap(habit.getId(), habit.getHabitSchedule(), userClock.today());

        return new HabitDetailResponse(
            base.id(),
            base.name(),
            base.description(),
            base.notes(),
            base.icon(),
            base.color(),
            base.unitLabel(),
            base.frequencyType(),
            base.reminderEnabled(),
            base.isActive(),
            base.tags(),
            base.habitSchedule(),
            base.createdOn(),
            base.currentStreak(),
            base.maxStreak(),
            base.xpPerCompletion(),
            base.totalXpEarned(),
            base.completedToday(),
            base.scheduledToday(),
            base.totalDaysCompleted(),
            base.weekStatus(),
            heatmap
        );
    }

    private List<HabitScheduleResponse> toScheduleResponses(Habit habit) {
        return habit.getActiveHabitSchedule().stream()
            .map(s -> new HabitScheduleResponse(s.getId(), s.getDayOfWeek(), s.getStartTime(), s.getEndTime()))
            .collect(Collectors.toList());
    }

    private Set<DayOfWeek> scheduledDaysOf(Habit habit) {
        return habit.getActiveHabitSchedule().stream()
            .map(HabitSchedule::getDayOfWeek)
            .collect(Collectors.toSet());
    }

    /**
     * Walks backward from the day before {@code fromExclusive} until it finds a date whose
     * day-of-week is in {@code scheduledDays}. Bounded to at most 7 steps, since every day-of-week
     * recurs within a week.
     */
    private LocalDate walkBackToNearestScheduledDay(LocalDate fromExclusive, Set<DayOfWeek> scheduledDays) {
        LocalDate cursor = fromExclusive.minusDays(1);
        while (!scheduledDays.contains(cursor.getDayOfWeek())) {
            cursor = cursor.minusDays(1);
        }
        return cursor;
    }

    /**
     * The stored currentStreak is only ever updated inside completeHabit/undoCompletion, so it can
     * go stale between a missed scheduled day and the next time either of those runs. This checks
     * whether the single most recent scheduled day before today was actually completed; if it was,
     * the stored value is still correct (nothing has been missed since it was last computed). If not,
     * the streak is broken and the effective value is 0 - no need to recompute the full historical
     * count, since a broken streak is 0 regardless of how long an earlier streak used to be.
     */
    private int computeEffectiveCurrentStreak(Habit habit, boolean completedToday, Set<DayOfWeek> scheduledDays, LocalDate today) {
        if (completedToday || scheduledDays.isEmpty()) {
            return habit.getCurrentStreak();
        }
        LocalDate lastOpportunity = walkBackToNearestScheduledDay(today, scheduledDays);
        boolean lastOpportunityCompleted = habitLogRepository.existsByHabit_IdAndCompletionDate(habit.getId(), lastOpportunity);
        return lastOpportunityCompleted ? habit.getCurrentStreak() : 0;
    }

    private List<Boolean> computeWeekStatus(long habitId, LocalDate today) {
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        List<Boolean> weekStatus = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate day = monday.plusDays(i);
            weekStatus.add(habitLogRepository.existsByHabit_IdAndCompletionDate(habitId, day));
        }
        return weekStatus;
    }

    private List<List<DayStatus>> computeHeatmap(long habitId, List<HabitSchedule> scheduleHistory, LocalDate today) {
        LocalDate currentWeekMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate heatmapStartMonday = currentWeekMonday.minusWeeks(HEATMAP_WEEK_COUNT - 1L);
        Habit habit = habitRepository.findById(habitId).orElseThrow(() -> new HabitNotFoundException(habitId));

        List<List<DayStatus>> heatmap = new ArrayList<>();
        for (int week = 0; week < HEATMAP_WEEK_COUNT; week++) {
            List<DayStatus> row = new ArrayList<>();
            for (int day = 0; day < 7; day++) {
                LocalDate date = heatmapStartMonday.plusWeeks(week).plusDays(day);
                row.add(resolveDayStatus(habitId, habit, date, scheduleHistory, today));
            }
            heatmap.add(row);
        }
        return heatmap;
    }

    private DayStatus resolveDayStatus(long habitId, Habit habit, LocalDate date, List<HabitSchedule> scheduleHistory, LocalDate today) {
        if (habitLogRepository.existsByHabit_IdAndCompletionDate(habitId, date)) {
            return DayStatus.DONE;
        }

        boolean wasHabitScheduled = scheduleHistory.stream().anyMatch(s -> s.getDayOfWeek() == date.getDayOfWeek() && s.isActiveOnDate(date));
        if(date.isBefore(habit.getCreatedOn()) || !date.isBefore(today) || !wasHabitScheduled) {
            return DayStatus.EMPTY;
        }
        return DayStatus.MISSED;
    }
}
