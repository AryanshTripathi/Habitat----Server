package com.habitat.server.service;

import com.habitat.server.dto.HabitDetailResponse;
import com.habitat.server.dto.HabitRequest;
import com.habitat.server.dto.HabitResponse;
import com.habitat.server.dto.HabitScheduleRequest;
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
import com.habitat.server.repository.HabitScheduleRepository;
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
import java.util.ArrayList;
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

    @Mock
    private HabitScheduleRepository habitScheduleRepository;

    @InjectMocks
    private HabitService habitService;

    @Test
    void createHabit_wiresBackReferenceOnEverySchedule() {
        HabitRequest request = withSchedule(HabitTestDataFactory.aHabitRequest(),
            HabitTestDataFactory.aScheduleRequest(DayOfWeek.MONDAY, LocalTime.of(6, 0), LocalTime.of(7, 0)));

        when(habitRepository.save(any(Habit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        habitService.createHabit(request);

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

        HabitDetailResponse result = habitService.getHabitById(1L);

        assertThat(result.name()).isEqualTo("Exercise");
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
        existing.setIcon("original-icon");

        // HabitRequest has no name/currentStreak/maxStreak/totalXpEarned/icon fields at all -
        // protection is enforced by the type itself now, not by the service selectively ignoring them.
        HabitRequest incoming = new HabitRequest(
            "ignored-by-type", "new description", "new notes", "ignored-by-type",
            Habit.Color.AMBER, "reps", Habit.FrequencyType.WEEKLY, true, false,
            new ArrayList<>(List.of("new-tag")), new ArrayList<>()
        );

        when(habitRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(habitRepository.save(any(Habit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        HabitResponse result = habitService.updateHabit(1L, incoming);

        assertThat(result.name()).isEqualTo("Original Name");
        assertThat(result.icon()).isEqualTo("original-icon");
        assertThat(result.currentStreak()).isEqualTo(5);
        assertThat(result.maxStreak()).isEqualTo(10);
        assertThat(result.totalXpEarned()).isEqualTo(100);
        assertThat(result.color()).isEqualTo(Habit.Color.AMBER);
        assertThat(result.description()).isEqualTo("new description");
    }

    @Test
    void updateHabit_nonExistentId_throwsHabitNotFoundException() {
        when(habitRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> habitService.updateHabit(99L, HabitTestDataFactory.aHabitRequest()))
            .isInstanceOf(HabitNotFoundException.class);
    }

    @Test
    void updateHabit_whenIncomingScheduleIsNull_doesNotThrow() {
        Habit existing = HabitTestDataFactory.aHabit();
        HabitRequest incoming = withSchedule(HabitTestDataFactory.aHabitRequest(), null);

        when(habitRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(habitRepository.save(any(Habit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatCode(() -> habitService.updateHabit(1L, incoming))
            .doesNotThrowAnyException();
    }

    @Test
    void getAllHabits_returnsEverythingFromRepository() {
        Habit habit1 = HabitTestDataFactory.aHabit();
        Habit habit2 = HabitTestDataFactory.aHabit();
        habit2.setName("Read");
        when(habitRepository.findAll()).thenReturn(List.of(habit1, habit2));

        List<HabitResponse> result = habitService.getAllHabits();

        assertThat(result).extracting(HabitResponse::name).containsExactly("Exercise", "Read");
    }

    @Test
    void createHabit_withNullScheduleList_doesNotThrow() {
        HabitRequest request = withSchedule(HabitTestDataFactory.aHabitRequest(), null);
        when(habitRepository.save(any(Habit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatCode(() -> habitService.createHabit(request))
            .doesNotThrowAnyException();
    }

    @Test
    void createHabit_duplicateScheduleDay_throwsDuplicateScheduleException() {
        HabitRequest request = withSchedule(HabitTestDataFactory.aHabitRequest(),
            HabitTestDataFactory.aScheduleRequest(DayOfWeek.MONDAY, LocalTime.of(6, 0), LocalTime.of(7, 0)),
            HabitTestDataFactory.aScheduleRequest(DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(19, 0)));

        assertThatThrownBy(() -> habitService.createHabit(request))
            .isInstanceOf(DuplicateScheduleException.class);

        verify(habitRepository, never()).save(any());
    }

    @Test
    void updateHabit_duplicateScheduleDay_throwsDuplicateScheduleException() {
        Habit existing = HabitTestDataFactory.aHabit();
        HabitRequest incoming = withSchedule(HabitTestDataFactory.aHabitRequest(),
            HabitTestDataFactory.aScheduleRequest(DayOfWeek.MONDAY, LocalTime.of(6, 0), LocalTime.of(7, 0)),
            HabitTestDataFactory.aScheduleRequest(DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(19, 0)));

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
        HabitTestDataFactory.setId(habit, 1L);
        habit.setCurrentStreak(2);
        habit.setMaxStreak(2);
        habit.setXpPerCompletion(10);
        habit.setTotalXpEarned(20);
        LocalDate today = LocalDate.now();
        habit.addSchedule(HabitTestDataFactory.aSchedule(today.getDayOfWeek(), LocalTime.of(6, 0), LocalTime.of(7, 0)));
        // single scheduled day a week => the "previous scheduled date" is exactly 7 days back
        LocalDate previousScheduledDate = today.minusDays(7);

        when(habitRepository.findById(1L)).thenReturn(Optional.of(habit));
        // first call = the duplicate check (before completing); second call = completedToday,
        // computed while building the response AFTER the completion has been recorded
        when(habitLogRepository.existsByHabit_IdAndCompletionDate(1L, today)).thenReturn(false, true);
        when(habitLogRepository.existsByHabit_IdAndCompletionDate(1L, previousScheduledDate)).thenReturn(true);
        when(habitRepository.save(any(Habit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        HabitResponse result = habitService.completeHabit(1L);

        assertThat(result.currentStreak()).isEqualTo(3);
        assertThat(result.maxStreak()).isEqualTo(3);
        assertThat(result.totalXpEarned()).isEqualTo(30);
    }

    @Test
    void completeHabit_previousScheduledDayWasMissed_resetsStreak() {
        Habit habit = HabitTestDataFactory.aHabit();
        HabitTestDataFactory.setId(habit, 1L);
        habit.setCurrentStreak(5);
        habit.setMaxStreak(5);
        LocalDate today = LocalDate.now();
        habit.addSchedule(HabitTestDataFactory.aSchedule(today.getDayOfWeek(), LocalTime.of(6, 0), LocalTime.of(7, 0)));
        LocalDate previousScheduledDate = today.minusDays(7);

        when(habitRepository.findById(1L)).thenReturn(Optional.of(habit));
        when(habitLogRepository.existsByHabit_IdAndCompletionDate(1L, today)).thenReturn(false, true);
        when(habitLogRepository.existsByHabit_IdAndCompletionDate(1L, previousScheduledDate)).thenReturn(false);
        when(habitRepository.save(any(Habit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        HabitResponse result = habitService.completeHabit(1L);

        assertThat(result.currentStreak()).isEqualTo(1);
        assertThat(result.maxStreak())
            .as("historical max streak should not drop just because the current one reset")
            .isEqualTo(5);
    }

    @Test
    void completeHabit_multiDaySchedule_findsNearestPreviousScheduledDate_notJustOneWeekBack() {
        Habit habit = HabitTestDataFactory.aHabit();
        HabitTestDataFactory.setId(habit, 1L);
        habit.setCurrentStreak(2);
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        habit.addSchedule(HabitTestDataFactory.aSchedule(today.getDayOfWeek(), LocalTime.of(6, 0), LocalTime.of(7, 0)));
        habit.addSchedule(HabitTestDataFactory.aSchedule(yesterday.getDayOfWeek(), LocalTime.of(6, 0), LocalTime.of(7, 0)));

        when(habitRepository.findById(1L)).thenReturn(Optional.of(habit));
        when(habitLogRepository.existsByHabit_IdAndCompletionDate(1L, today)).thenReturn(false, true);
        when(habitLogRepository.existsByHabit_IdAndCompletionDate(1L, yesterday)).thenReturn(true);
        when(habitRepository.save(any(Habit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        HabitResponse result = habitService.completeHabit(1L);

        assertThat(result.currentStreak())
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
        HabitTestDataFactory.setId(habit, 1L);
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

        HabitResponse result = habitService.undoCompletion(1L);

        assertThat(result.currentStreak()).isEqualTo(0);
        assertThat(result.totalXpEarned()).isEqualTo(0);
    }

    @Test
    void undoCompletion_withConsecutivePriorHistory_recomputesStreakCorrectly() {
        Habit habit = HabitTestDataFactory.aHabit();
        HabitTestDataFactory.setId(habit, 1L);
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

        HabitResponse result = habitService.undoCompletion(1L);

        assertThat(result.currentStreak())
            .as("should count the two consecutive prior scheduled completions, stopping at the first gap")
            .isEqualTo(2);
    }

    @Test
    void undoCompletion_doesNotModifyMaxStreak() {
        Habit habit = HabitTestDataFactory.aHabit();
        HabitTestDataFactory.setId(habit, 1L);
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

        HabitResponse result = habitService.undoCompletion(1L);

        assertThat(result.maxStreak())
            .as("undo must never touch maxStreak - permanent high-water mark by design")
            .isEqualTo(10);
    }

    private static HabitRequest withSchedule(HabitRequest base, HabitScheduleRequest... schedules) {
        List<HabitScheduleRequest> scheduleList = schedules == null ? null : Arrays.asList(schedules);
        return new HabitRequest(
            base.name(), base.description(), base.notes(), base.icon(), base.color(), base.unitLabel(),
            base.frequencyType(), base.reminderEnabled(), base.isActive(), base.tags(), scheduleList
        );
    }
}
