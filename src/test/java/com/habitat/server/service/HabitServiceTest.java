package com.habitat.server.service;

import com.habitat.server.exception.DuplicateScheduleException;
import com.habitat.server.exception.HabitNotFoundException;
import com.habitat.server.model.Habit;
import com.habitat.server.model.HabitSchedule;
import com.habitat.server.repository.HabitRepository;
import com.habitat.server.testsupport.HabitTestDataFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HabitServiceTest {

    @Mock
    private HabitRepository habitRepository;

    @InjectMocks
    private HabitService habitService;

    @Test
    void createHabit_wiresBackReferenceOnEverySchedule() {
        Habit habit = HabitTestDataFactory.aHabit();
        HabitSchedule schedule = HabitTestDataFactory.aSchedule(DayOfWeek.MONDAY, LocalTime.of(6, 0), LocalTime.of(7, 0));
        // deliberately bypassing addSchedule() here, to mimic what Jackson actually
        // produces after deserializing a request body: the schedule is in the list,
        // but its own "habit" back-reference is still null
        habit.getHabitSchedule().add(schedule);

        when(habitRepository.save(any(Habit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        habitService.createHabit(habit);

        ArgumentCaptor<Habit> captor = ArgumentCaptor.forClass(Habit.class);
        verify(habitRepository).save(captor.capture());
        Habit saved = captor.getValue();

        assertThat(saved.getHabitSchedule())
            .allSatisfy(s -> assertThat(s.getHabit()).isSameAs(saved));
    }

    @Test
    void getHabitById_existingId_returnsHabit() {
        Habit habit = HabitTestDataFactory.aHabit();
        when(habitRepository.findById(1L)).thenReturn(Optional.of(habit));

        Habit result = habitService.getHabitById(1L);

        assertThat(result).isSameAs(habit);
    }

    @Test
    void getHabitById_nonExistentId_throwsHabitNotFoundException() {
        when(habitRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> habitService.getHabitById(99L))
            .isInstanceOf(HabitNotFoundException.class);
    }

    @Test
    void deleteHabit_existingId_callsRepositoryDeleteById() {
        when(habitRepository.existsById(1L)).thenReturn(true);

        habitService.deleteHabit(1L);

        verify(habitRepository).deleteById(1L);
    }

    @Test
    void deleteHabit_nonExistentId_throwsException_andNeverCallsDelete() {
        when(habitRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> habitService.deleteHabit(99L))
            .isInstanceOf(HabitNotFoundException.class);

        verify(habitRepository, never()).deleteById(anyLong());
    }

    @Test
    void updateHabit_updatesAllowedFields_leavesProtectedFieldsUntouched() {
        Habit existing = HabitTestDataFactory.aHabit();
        existing.setName("Original Name");
        existing.setCurrentStreak(5);
        existing.setMaxStreak(10);
        existing.setTotalXpEarned(100);
        existing.setActive(true);
        existing.setColor(Habit.Color.RED);

        Habit incoming = HabitTestDataFactory.aHabit();
        incoming.setName("Changed Name");      // protected - must NOT apply
        incoming.setCurrentStreak(999);        // protected - must NOT apply
        incoming.setMaxStreak(999);             // protected - must NOT apply
        incoming.setTotalXpEarned(999);          // protected - must NOT apply
        incoming.setActive(false);                // protected - must NOT apply
        incoming.setColor(Habit.Color.GREEN);        // allowed - SHOULD apply

        when(habitRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(habitRepository.save(any(Habit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Habit result = habitService.updateHabit(1L, incoming);

        assertThat(result.getName()).isEqualTo("Original Name");
        assertThat(result.getCurrentStreak()).isEqualTo(5);
        assertThat(result.getMaxStreak()).isEqualTo(10);
        assertThat(result.getTotalXpEarned()).isEqualTo(100);
        assertThat(result.isActive()).isTrue();
        assertThat(result.getColor()).isEqualTo(Habit.Color.GREEN);
    }

    @Test
    void updateHabit_nonExistentId_throwsHabitNotFoundException() {
        when(habitRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> habitService.updateHabit(99L, HabitTestDataFactory.aHabit()))
            .isInstanceOf(HabitNotFoundException.class);
    }

    @Test
    void updateHabit_whenIncomingScheduleIsNull_doesNotThrow() {
        Habit existing = HabitTestDataFactory.aHabit();
        Habit incoming = HabitTestDataFactory.aHabit();
        incoming.setHabitSchedule(null); // simulates a PUT body that simply omits "habitSchedule"

        when(habitRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(habitRepository.save(any(Habit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatCode(() -> habitService.updateHabit(1L, incoming))
            .doesNotThrowAnyException();
    }

    @Test
    void getAllHabits_returnsEverythingFromRepository() {
        Habit habit1 = HabitTestDataFactory.aHabit();
        Habit habit2 = HabitTestDataFactory.aHabit();
        when(habitRepository.findAll()).thenReturn(List.of(habit1, habit2));

        List<Habit> result = habitService.getAllHabits();

        assertThat(result).containsExactly(habit1, habit2);
    }

    @Test
    void createHabit_withNullScheduleList_doesNotThrow() {
        Habit habit = HabitTestDataFactory.aHabit();
        habit.setHabitSchedule(null);
        when(habitRepository.save(any(Habit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatCode(() -> habitService.createHabit(habit))
            .doesNotThrowAnyException();
    }

    @Test
    void createHabit_duplicateScheduleDay_throwsDuplicateScheduleException() {
        Habit habit = HabitTestDataFactory.aHabit();
        habit.getHabitSchedule().add(HabitTestDataFactory.aSchedule(DayOfWeek.MONDAY, LocalTime.of(6, 0), LocalTime.of(7, 0)));
        habit.getHabitSchedule().add(HabitTestDataFactory.aSchedule(DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(19, 0)));

        assertThatThrownBy(() -> habitService.createHabit(habit))
            .isInstanceOf(DuplicateScheduleException.class);

        verify(habitRepository, never()).save(any());
    }

    @Test
    void updateHabit_duplicateScheduleDay_throwsDuplicateScheduleException() {
        Habit existing = HabitTestDataFactory.aHabit();
        Habit incoming = HabitTestDataFactory.aHabit();
        incoming.getHabitSchedule().add(HabitTestDataFactory.aSchedule(DayOfWeek.MONDAY, LocalTime.of(6, 0), LocalTime.of(7, 0)));
        incoming.getHabitSchedule().add(HabitTestDataFactory.aSchedule(DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(19, 0)));

        when(habitRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> habitService.updateHabit(1L, incoming))
            .isInstanceOf(DuplicateScheduleException.class);

        verify(habitRepository, never()).save(any());
    }
}
