package com.habitat.server.service;

import com.habitat.server.exception.DuplicateCompletionException;
import com.habitat.server.exception.DuplicateScheduleException;
import com.habitat.server.exception.HabitLogNotFoundException;
import com.habitat.server.exception.HabitNotFoundException;
import com.habitat.server.exception.HabitScheduleNotFoundException;
import com.habitat.server.model.Habit;
import com.habitat.server.model.HabitLog;
import com.habitat.server.model.HabitSchedule;
import com.habitat.server.repository.HabitLogRepository;
import com.habitat.server.repository.HabitRepository;
import com.habitat.server.testsupport.HabitTestDataFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
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

    @Mock
    private HabitLogRepository habitLogRepository;

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

    @Test
    void completeHabit_nonExistentId_throwsHabitNotFoundException() {
        when(habitRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> habitService.completeHabit(99L))
            .isInstanceOf(HabitNotFoundException.class);
    }

    @Test
    void completeHabit_alreadyCompletedToday_throwsDuplicateCompletionException() {
        Habit habit = HabitTestDataFactory.aHabit();
        LocalDate today = LocalDate.now();
        habit.addSchedule(HabitTestDataFactory.aSchedule(today.getDayOfWeek(), LocalTime.of(6, 0), LocalTime.of(7, 0)));

        when(habitRepository.findById(1L)).thenReturn(Optional.of(habit));
        when(habitLogRepository.existsByHabit_IdAndCompletionDate(1L, today)).thenReturn(true);

        assertThatThrownBy(() -> habitService.completeHabit(1L))
            .isInstanceOf(DuplicateCompletionException.class);

        verify(habitRepository, never()).save(any());
    }

    @Test
    void completeHabit_notScheduledToday_throwsHabitScheduleNotFoundException() {
        Habit habit = HabitTestDataFactory.aHabit();
        LocalDate today = LocalDate.now();
        DayOfWeek notToday = Arrays.stream(DayOfWeek.values())
            .filter(d -> d != today.getDayOfWeek())
            .findFirst().orElseThrow();
        habit.addSchedule(HabitTestDataFactory.aSchedule(notToday, LocalTime.of(6, 0), LocalTime.of(7, 0)));

        when(habitRepository.findById(1L)).thenReturn(Optional.of(habit));
        when(habitLogRepository.existsByHabit_IdAndCompletionDate(1L, today)).thenReturn(false);

        assertThatThrownBy(() -> habitService.completeHabit(1L))
            .isInstanceOf(HabitScheduleNotFoundException.class);

        verify(habitRepository, never()).save(any());
    }

    @Test
    void completeHabit_previousScheduledDayWasCompleted_continuesStreak() {
        Habit habit = HabitTestDataFactory.aHabit();
        habit.setCurrentStreak(2);
        habit.setMaxStreak(2);
        habit.setXpPerCompletion(10);
        habit.setTotalXpEarned(20);
        LocalDate today = LocalDate.now();
        habit.addSchedule(HabitTestDataFactory.aSchedule(today.getDayOfWeek(), LocalTime.of(6, 0), LocalTime.of(7, 0)));
        // single scheduled day a week => the "previous scheduled date" is exactly 7 days back
        LocalDate previousScheduledDate = today.minusDays(7);

        when(habitRepository.findById(1L)).thenReturn(Optional.of(habit));
        when(habitLogRepository.existsByHabit_IdAndCompletionDate(1L, today)).thenReturn(false);
        when(habitLogRepository.existsByHabit_IdAndCompletionDate(1L, previousScheduledDate)).thenReturn(true);
        when(habitRepository.save(any(Habit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Habit result = habitService.completeHabit(1L);

        assertThat(result.getCurrentStreak()).isEqualTo(3);
        assertThat(result.getMaxStreak()).isEqualTo(3);
        assertThat(result.getTotalXpEarned()).isEqualTo(30);
        assertThat(result.getHabitLog()).hasSize(1);
        assertThat(result.getHabitLog().get(0).getCompletionDate()).isEqualTo(today);
    }

    @Test
    void completeHabit_previousScheduledDayWasMissed_resetsStreak() {
        Habit habit = HabitTestDataFactory.aHabit();
        habit.setCurrentStreak(5);
        habit.setMaxStreak(5);
        LocalDate today = LocalDate.now();
        habit.addSchedule(HabitTestDataFactory.aSchedule(today.getDayOfWeek(), LocalTime.of(6, 0), LocalTime.of(7, 0)));
        LocalDate previousScheduledDate = today.minusDays(7);

        when(habitRepository.findById(1L)).thenReturn(Optional.of(habit));
        when(habitLogRepository.existsByHabit_IdAndCompletionDate(1L, today)).thenReturn(false);
        when(habitLogRepository.existsByHabit_IdAndCompletionDate(1L, previousScheduledDate)).thenReturn(false);
        when(habitRepository.save(any(Habit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Habit result = habitService.completeHabit(1L);

        assertThat(result.getCurrentStreak()).isEqualTo(1);
        assertThat(result.getMaxStreak())
            .as("historical max streak should not drop just because the current one reset")
            .isEqualTo(5);
    }

    @Test
    void completeHabit_multiDaySchedule_findsNearestPreviousScheduledDate_notJustOneWeekBack() {
        Habit habit = HabitTestDataFactory.aHabit();
        habit.setCurrentStreak(2);
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        habit.addSchedule(HabitTestDataFactory.aSchedule(today.getDayOfWeek(), LocalTime.of(6, 0), LocalTime.of(7, 0)));
        habit.addSchedule(HabitTestDataFactory.aSchedule(yesterday.getDayOfWeek(), LocalTime.of(6, 0), LocalTime.of(7, 0)));

        when(habitRepository.findById(1L)).thenReturn(Optional.of(habit));
        when(habitLogRepository.existsByHabit_IdAndCompletionDate(1L, today)).thenReturn(false);
        when(habitLogRepository.existsByHabit_IdAndCompletionDate(1L, yesterday)).thenReturn(true);
        when(habitRepository.save(any(Habit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Habit result = habitService.completeHabit(1L);

        assertThat(result.getCurrentStreak())
            .as("should find yesterday as the previous scheduled day, not walk back a full week")
            .isEqualTo(3);
    }

    @Test
    void undoCompletion_nonExistentHabit_throwsHabitNotFoundException() {
        when(habitRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> habitService.undoCompletion(99L))
            .isInstanceOf(HabitNotFoundException.class);
    }

    @Test
    void undoCompletion_noCompletionLoggedToday_throwsHabitLogNotFoundException() {
        Habit habit = HabitTestDataFactory.aHabit();
        LocalDate today = LocalDate.now();

        when(habitRepository.findById(1L)).thenReturn(Optional.of(habit));
        when(habitLogRepository.findByHabit_IdAndCompletionDate(1L, today)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> habitService.undoCompletion(1L))
            .isInstanceOf(HabitLogNotFoundException.class);

        verify(habitRepository, never()).save(any());
    }

    @Test
    void undoCompletion_noPriorHistory_recomputesStreakToZero() {
        Habit habit = HabitTestDataFactory.aHabit();
        habit.setCurrentStreak(1);
        habit.setXpPerCompletion(10);
        habit.setTotalXpEarned(10);
        LocalDate today = LocalDate.now();
        habit.addSchedule(HabitTestDataFactory.aSchedule(today.getDayOfWeek(), LocalTime.of(6, 0), LocalTime.of(7, 0)));

        HabitLog todayLog = new HabitLog();
        todayLog.setCompletionDate(today);
        habit.addLog(todayLog);

        LocalDate previousScheduledDate = today.minusDays(7);

        when(habitRepository.findById(1L)).thenReturn(Optional.of(habit));
        when(habitLogRepository.findByHabit_IdAndCompletionDate(1L, today)).thenReturn(Optional.of(todayLog));
        when(habitLogRepository.existsByHabit_IdAndCompletionDate(1L, previousScheduledDate)).thenReturn(false);
        when(habitRepository.save(any(Habit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Habit result = habitService.undoCompletion(1L);

        assertThat(result.getCurrentStreak()).isEqualTo(0);
        assertThat(result.getTotalXpEarned()).isEqualTo(0);
        assertThat(result.getHabitLog()).doesNotContain(todayLog);
    }

    @Test
    void undoCompletion_withConsecutivePriorHistory_recomputesStreakCorrectly() {
        Habit habit = HabitTestDataFactory.aHabit();
        habit.setCurrentStreak(3);
        LocalDate today = LocalDate.now();
        habit.addSchedule(HabitTestDataFactory.aSchedule(today.getDayOfWeek(), LocalTime.of(6, 0), LocalTime.of(7, 0)));

        HabitLog todayLog = new HabitLog();
        todayLog.setCompletionDate(today);
        habit.addLog(todayLog);

        LocalDate oneWeekBack = today.minusDays(7);
        LocalDate twoWeeksBack = today.minusDays(14);
        LocalDate threeWeeksBack = today.minusDays(21);

        when(habitRepository.findById(1L)).thenReturn(Optional.of(habit));
        when(habitLogRepository.findByHabit_IdAndCompletionDate(1L, today)).thenReturn(Optional.of(todayLog));
        when(habitLogRepository.existsByHabit_IdAndCompletionDate(1L, oneWeekBack)).thenReturn(true);
        when(habitLogRepository.existsByHabit_IdAndCompletionDate(1L, twoWeeksBack)).thenReturn(true);
        when(habitLogRepository.existsByHabit_IdAndCompletionDate(1L, threeWeeksBack)).thenReturn(false);
        when(habitRepository.save(any(Habit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Habit result = habitService.undoCompletion(1L);

        assertThat(result.getCurrentStreak())
            .as("should count the two consecutive prior scheduled completions, stopping at the first gap")
            .isEqualTo(2);
    }

    @Test
    void undoCompletion_doesNotModifyMaxStreak() {
        Habit habit = HabitTestDataFactory.aHabit();
        habit.setCurrentStreak(1);
        habit.setMaxStreak(10);
        LocalDate today = LocalDate.now();
        habit.addSchedule(HabitTestDataFactory.aSchedule(today.getDayOfWeek(), LocalTime.of(6, 0), LocalTime.of(7, 0)));

        HabitLog todayLog = new HabitLog();
        todayLog.setCompletionDate(today);
        habit.addLog(todayLog);

        when(habitRepository.findById(1L)).thenReturn(Optional.of(habit));
        when(habitLogRepository.findByHabit_IdAndCompletionDate(1L, today)).thenReturn(Optional.of(todayLog));
        when(habitLogRepository.existsByHabit_IdAndCompletionDate(1L, today.minusDays(7))).thenReturn(false);
        when(habitRepository.save(any(Habit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Habit result = habitService.undoCompletion(1L);

        assertThat(result.getMaxStreak())
            .as("undo must never touch maxStreak - permanent high-water mark by design")
            .isEqualTo(10);
    }
}
