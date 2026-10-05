package com.habitat.server.model;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.habitat.server.exception.DuplicateCompletionException;
import com.habitat.server.exception.DuplicateScheduleException;
import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Entity
@EntityListeners(AuditingEntityListener.class)
public class Habit {
    @Id
    @GeneratedValue
    private long id;

    public enum FrequencyType {DAILY, WEEKLY, MONTHLY};
    public enum Color {RED, GREEN, BLUE, YELLOW, PINK, PURPLE, GREY};

    @CreatedDate
    private LocalDate createdOn;
    @LastModifiedDate
    private LocalDate updatedOn;

    private String name;
    private int currentStreak;
    private int maxStreak;
    private int xpPerCompletion;
    private int totalXpEarned;
    private boolean isActive;
    private String icon;

    @Enumerated(EnumType.STRING)
    private FrequencyType frequencyType;
    @Enumerated(EnumType.STRING)
    private Color color;

    @ElementCollection
    private List<String> tags;

    @OneToMany(mappedBy = "habit", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<HabitSchedule> habitSchedule;
    @OneToMany(mappedBy = "habit", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<HabitLog> habitLog;

    public List<HabitSchedule> getHabitSchedule() {
        return habitSchedule;
    }

    public void setHabitSchedule(List<HabitSchedule> habitSchedule) {
        this.habitSchedule = habitSchedule;
    }

    public List<HabitLog> getHabitLog() {
        return habitLog;
    }

    public void setHabitLog(List<HabitLog> habitLog) {
        this.habitLog = habitLog;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getCurrentStreak() {
        return currentStreak;
    }

    public void setCurrentStreak(int currentStreak) {
        this.currentStreak = currentStreak;
    }

    public int getMaxStreak() {
        return maxStreak;
    }

    public void setMaxStreak(int maxStreak) {
        this.maxStreak = maxStreak;
    }

    public int getXpPerCompletion() {
        return xpPerCompletion;
    }

    public void setXpPerCompletion(int xpPerCompletion) {
        this.xpPerCompletion = xpPerCompletion;
    }

    public int getTotalXpEarned() {
        return totalXpEarned;
    }

    public void setTotalXpEarned(int totalXpEarned) {
        this.totalXpEarned = totalXpEarned;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public FrequencyType getFrequencyType() {
        return frequencyType;
    }

    public void setFrequencyType(FrequencyType frequencyType) {
        this.frequencyType = frequencyType;
    }

    public Color getColor() {
        return color;
    }

    public void setColor(Color color) {
        this.color = color;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public LocalDate getCreatedOn() {
        return createdOn;
    }

    public LocalDate getUpdatedOn() {
        return updatedOn;
    }

    public void addSchedule(HabitSchedule schedule) {
        boolean scheduleAlreadyExists = this.habitSchedule.stream().anyMatch((s -> s.getDayOfWeek() == schedule.getDayOfWeek()));
        if(scheduleAlreadyExists) {
            throw new DuplicateScheduleException(schedule.getDayOfWeek(), this.name);
        }

        this.habitSchedule.add(schedule);
        schedule.setHabit(this);
    }

    public void syncSchedule(List<HabitSchedule> incomingSchedules) {
        Map<DayOfWeek, HabitSchedule> existingScheduleMap = this.habitSchedule.stream().collect(Collectors.toMap(HabitSchedule::getDayOfWeek, s -> s));
        Set<DayOfWeek> incomingDays = new HashSet<>();

        boolean isDuplicateScheduleOnSameDay = incomingSchedules.stream().anyMatch(s -> !incomingDays.add(s.getDayOfWeek()));
        if(isDuplicateScheduleOnSameDay) {
            throw new DuplicateScheduleException(incomingSchedules, "Multiple Schedules for the same day.");
        }

        for(HabitSchedule incoming : incomingSchedules) {
            HabitSchedule existingSchedule = existingScheduleMap.get(incoming.getDayOfWeek());
            if(existingSchedule != null) {
                existingSchedule.setStartTime(incoming.getStartTime());
                existingSchedule.setEndTime(incoming.getEndTime());
            } else {
                this.addSchedule(incoming);
            }
        }
        this.habitSchedule.removeIf(s -> !incomingDays.contains(s.getDayOfWeek()));
    }

    public void addLog(HabitLog log) {
        this.habitLog.add(log);
        log.setHabit(this);
    }

    public void recordCompletion(boolean continuesStreak) {
        this.totalXpEarned += this.xpPerCompletion;
        this.currentStreak = continuesStreak ? this.currentStreak + 1 : 1;
        this.maxStreak = Math.max(this.maxStreak, this.currentStreak);
    }

    public void undoCompletion(HabitLog log, int recomputedStreak) {
        this.habitLog.remove(log);
        this.currentStreak = recomputedStreak;
        this.totalXpEarned -= this.xpPerCompletion;
    }
}
