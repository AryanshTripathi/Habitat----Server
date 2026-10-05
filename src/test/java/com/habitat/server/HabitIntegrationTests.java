package com.habitat.server;

import com.habitat.server.model.Habit;
import com.habitat.server.model.HabitLog;
import com.habitat.server.model.HabitSchedule;
import com.habitat.server.repository.HabitLogRepository;
import com.habitat.server.repository.HabitRepository;
import com.habitat.server.testsupport.HabitTestDataFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HabitIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private HabitRepository habitRepository;

    @Autowired
    private HabitLogRepository habitLogRepository;

    private Habit createAndParse(Habit habit) throws Exception {
        String response = mockMvc.perform(post("/habits")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(habit)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(response, Habit.class);
    }

    private Habit getAndParse(long id) throws Exception {
        String response = mockMvc.perform(get("/habits/" + id))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(response, Habit.class);
    }

    @Test
    void fullLifecycle_createGetUpdateDelete_worksEndToEnd() throws Exception {
        Habit created = createAndParse(HabitTestDataFactory.aHabit());
        long id = created.getId();

        mockMvc.perform(get("/habits/" + id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Exercise"));

        Habit updatePayload = HabitTestDataFactory.aHabit();
        updatePayload.setColor(Habit.Color.GREEN);
        mockMvc.perform(put("/habits/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updatePayload)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.color").value("GREEN"));

        mockMvc.perform(delete("/habits/" + id))
            .andExpect(status().isOk());

        mockMvc.perform(get("/habits/" + id))
            .andExpect(status().isNotFound());
    }

    @Test
    void createHabit_withSchedule_persistsLinkedScheduleRows() throws Exception {
        Habit habit = HabitTestDataFactory.aHabit();
        habit.getHabitSchedule().add(HabitTestDataFactory.aSchedule(DayOfWeek.MONDAY, LocalTime.of(6, 0), LocalTime.of(7, 0)));
        habit.getHabitSchedule().add(HabitTestDataFactory.aSchedule(DayOfWeek.WEDNESDAY, LocalTime.of(7, 0), LocalTime.of(8, 0)));

        Habit created = createAndParse(habit);
        Habit fetched = getAndParse(created.getId());

        assertThat(fetched.getHabitSchedule()).hasSize(2);
        assertThat(fetched.getHabitSchedule())
            .extracting(HabitSchedule::getDayOfWeek)
            .containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY);
        assertThat(fetched.getHabitSchedule())
            .allSatisfy(s -> assertThat(s.getId()).isNotZero());
    }

    @Test
    void updateHabit_scheduleSync_onlyChangedDayIsAffected() throws Exception {
        Habit habit = HabitTestDataFactory.aHabit();
        habit.getHabitSchedule().add(HabitTestDataFactory.aSchedule(DayOfWeek.MONDAY, LocalTime.of(6, 0), LocalTime.of(7, 0)));
        habit.getHabitSchedule().add(HabitTestDataFactory.aSchedule(DayOfWeek.WEDNESDAY, LocalTime.of(7, 0), LocalTime.of(8, 0)));
        Habit created = createAndParse(habit);

        long wednesdayIdBeforeUpdate = created.getHabitSchedule().stream()
            .filter(s -> s.getDayOfWeek() == DayOfWeek.WEDNESDAY)
            .findFirst().orElseThrow()
            .getId();

        Habit updatePayload = HabitTestDataFactory.aHabit();
        updatePayload.getHabitSchedule().add(HabitTestDataFactory.aSchedule(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0))); // changed
        updatePayload.getHabitSchedule().add(HabitTestDataFactory.aSchedule(DayOfWeek.WEDNESDAY, LocalTime.of(7, 0), LocalTime.of(8, 0))); // unchanged
        updatePayload.getHabitSchedule().add(HabitTestDataFactory.aSchedule(DayOfWeek.FRIDAY, LocalTime.of(18, 0), LocalTime.of(19, 0))); // new

        mockMvc.perform(put("/habits/" + created.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updatePayload)))
            .andExpect(status().isOk());

        Habit fetched = getAndParse(created.getId());

        assertThat(fetched.getHabitSchedule())
            .extracting(HabitSchedule::getDayOfWeek)
            .containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY);

        HabitSchedule wednesdayAfterUpdate = fetched.getHabitSchedule().stream()
            .filter(s -> s.getDayOfWeek() == DayOfWeek.WEDNESDAY)
            .findFirst().orElseThrow();
        assertThat(wednesdayAfterUpdate.getId())
            .as("unchanged day should keep the same row, not be deleted and recreated")
            .isEqualTo(wednesdayIdBeforeUpdate);

        HabitSchedule mondayAfterUpdate = fetched.getHabitSchedule().stream()
            .filter(s -> s.getDayOfWeek() == DayOfWeek.MONDAY)
            .findFirst().orElseThrow();
        assertThat(mondayAfterUpdate.getStartTime()).isEqualTo(LocalTime.of(9, 0));
    }

    @Test
    void updateHabit_cannotChangeNameOrStreakViaClient() throws Exception {
        Habit habit = HabitTestDataFactory.aHabit();
        habit.setName("Original Name");
        habit.setCurrentStreak(5);
        Habit created = createAndParse(habit);

        Habit updatePayload = HabitTestDataFactory.aHabit();
        updatePayload.setName("Changed Name");
        updatePayload.setCurrentStreak(999);
        updatePayload.setColor(Habit.Color.PURPLE);

        mockMvc.perform(put("/habits/" + created.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updatePayload)))
            .andExpect(status().isOk());

        Habit fetched = getAndParse(created.getId());

        assertThat(fetched.getName()).isEqualTo("Original Name");
        assertThat(fetched.getCurrentStreak()).isEqualTo(5);
        assertThat(fetched.getColor()).isEqualTo(Habit.Color.PURPLE);
    }

    @Test
    void completeHabit_firstTimeScheduledToday_startsStreakAtOne() throws Exception {
        LocalDate today = LocalDate.now();
        Habit habit = HabitTestDataFactory.aHabit();
        habit.setXpPerCompletion(10);
        habit.getHabitSchedule().add(HabitTestDataFactory.aSchedule(today.getDayOfWeek(), LocalTime.of(6, 0), LocalTime.of(7, 0)));
        Habit created = createAndParse(habit);

        mockMvc.perform(post("/habits/complete/" + created.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentStreak").value(1))
            .andExpect(jsonPath("$.maxStreak").value(1))
            .andExpect(jsonPath("$.totalXpEarned").value(10));

        Habit fetched = getAndParse(created.getId());
        assertThat(fetched.getHabitLog()).hasSize(1);
        assertThat(fetched.getHabitLog().get(0).getCompletionDate()).isEqualTo(today);
    }

    @Test
    void completeHabit_sameDayTwice_returns400() throws Exception {
        LocalDate today = LocalDate.now();
        Habit habit = HabitTestDataFactory.aHabit();
        habit.getHabitSchedule().add(HabitTestDataFactory.aSchedule(today.getDayOfWeek(), LocalTime.of(6, 0), LocalTime.of(7, 0)));
        Habit created = createAndParse(habit);

        mockMvc.perform(post("/habits/complete/" + created.getId()))
            .andExpect(status().isOk());

        mockMvc.perform(post("/habits/complete/" + created.getId()))
            .andExpect(status().isBadRequest());
    }

    @Test
    void completeHabit_notScheduledToday_returns404() throws Exception {
        LocalDate today = LocalDate.now();
        DayOfWeek notToday = java.util.Arrays.stream(DayOfWeek.values())
            .filter(d -> d != today.getDayOfWeek())
            .findFirst().orElseThrow();

        Habit habit = HabitTestDataFactory.aHabit();
        habit.getHabitSchedule().add(HabitTestDataFactory.aSchedule(notToday, LocalTime.of(6, 0), LocalTime.of(7, 0)));
        Habit created = createAndParse(habit);

        mockMvc.perform(post("/habits/complete/" + created.getId()))
            .andExpect(status().isNotFound());
    }

    @Test
    void completeHabit_previousScheduledDayAlreadyLogged_continuesStreak() throws Exception {
        LocalDate today = LocalDate.now();
        LocalDate previousScheduledDate = today.minusDays(7); // single-day-a-week schedule => exactly one week back

        Habit habit = HabitTestDataFactory.aHabit();
        habit.getHabitSchedule().add(HabitTestDataFactory.aSchedule(today.getDayOfWeek(), LocalTime.of(6, 0), LocalTime.of(7, 0)));
        Habit created = createAndParse(habit);

        // Seed a historical completion directly via the repository - the API itself
        // only ever logs "today", so backdated data has to be set up this way.
        // currentStreak is set to 1 to match what recordCompletion(false) would have
        // produced had this historical completion actually gone through the real API.
        Habit managed = habitRepository.findById(created.getId()).orElseThrow();
        managed.setCurrentStreak(1);
        managed.setMaxStreak(1);
        HabitLog pastLog = new HabitLog();
        pastLog.setCompletionDate(previousScheduledDate);
        managed.addLog(pastLog);
        habitRepository.save(managed);

        mockMvc.perform(post("/habits/complete/" + created.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentStreak").value(2));
    }

    @Test
    void undoCompletion_afterCompleting_removesLogAndRestoresStreakAndXp() throws Exception {
        LocalDate today = LocalDate.now();
        Habit habit = HabitTestDataFactory.aHabit();
        habit.setXpPerCompletion(10);
        habit.getHabitSchedule().add(HabitTestDataFactory.aSchedule(today.getDayOfWeek(), LocalTime.of(6, 0), LocalTime.of(7, 0)));
        Habit created = createAndParse(habit);

        mockMvc.perform(post("/habits/complete/" + created.getId()))
            .andExpect(status().isOk());

        mockMvc.perform(post("/habits/undo/" + created.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentStreak").value(0))
            .andExpect(jsonPath("$.totalXpEarned").value(0));

        Habit fetched = getAndParse(created.getId());
        assertThat(fetched.getHabitLog()).isEmpty();
    }

    @Test
    void undoCompletion_noCompletionToday_returns404() throws Exception {
        LocalDate today = LocalDate.now();
        Habit habit = HabitTestDataFactory.aHabit();
        habit.getHabitSchedule().add(HabitTestDataFactory.aSchedule(today.getDayOfWeek(), LocalTime.of(6, 0), LocalTime.of(7, 0)));
        Habit created = createAndParse(habit);

        mockMvc.perform(post("/habits/undo/" + created.getId()))
            .andExpect(status().isNotFound());
    }

    @Test
    void undoCompletion_doesNotLowerMaxStreak() throws Exception {
        LocalDate today = LocalDate.now();
        Habit habit = HabitTestDataFactory.aHabit();
        habit.getHabitSchedule().add(HabitTestDataFactory.aSchedule(today.getDayOfWeek(), LocalTime.of(6, 0), LocalTime.of(7, 0)));
        Habit created = createAndParse(habit);

        // Give this habit a historical maxStreak higher than anything today's
        // completion/undo cycle would produce, to prove undo never touches it.
        Habit managed = habitRepository.findById(created.getId()).orElseThrow();
        managed.setMaxStreak(10);
        habitRepository.save(managed);

        mockMvc.perform(post("/habits/complete/" + created.getId()))
            .andExpect(status().isOk());

        mockMvc.perform(post("/habits/undo/" + created.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.maxStreak").value(10));
    }

    @Test
    void undoCompletion_withPriorHistory_recomputesStreakToMatchRemainingHistory() throws Exception {
        LocalDate today = LocalDate.now();
        LocalDate oneWeekBack = today.minusDays(7);
        Habit habit = HabitTestDataFactory.aHabit();
        habit.getHabitSchedule().add(HabitTestDataFactory.aSchedule(today.getDayOfWeek(), LocalTime.of(6, 0), LocalTime.of(7, 0)));
        Habit created = createAndParse(habit);

        // Seed one week of real history, matching what recordCompletion would have done
        Habit managed = habitRepository.findById(created.getId()).orElseThrow();
        managed.setCurrentStreak(1);
        managed.setMaxStreak(1);
        HabitLog pastLog = new HabitLog();
        pastLog.setCompletionDate(oneWeekBack);
        managed.addLog(pastLog);
        habitRepository.save(managed);

        mockMvc.perform(post("/habits/complete/" + created.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentStreak").value(2));

        mockMvc.perform(post("/habits/undo/" + created.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentStreak")
                .value(1)); // reverts to match the one remaining historical completion, not 0
    }
}
