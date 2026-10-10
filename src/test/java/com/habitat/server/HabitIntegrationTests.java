package com.habitat.server;

import com.habitat.server.dto.HabitDetailResponse;
import com.habitat.server.dto.HabitRequest;
import com.habitat.server.dto.HabitResponse;
import com.habitat.server.dto.HabitScheduleResponse;
import com.habitat.server.model.Habit;
import com.habitat.server.model.HabitLog;
import com.habitat.server.model.HabitSchedule;
import com.habitat.server.repository.HabitLogRepository;
import com.habitat.server.repository.HabitRepository;
import com.habitat.server.repository.HabitScheduleRepository;
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
import java.util.ArrayList;
import java.util.Arrays;
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

    @Autowired
    private HabitScheduleRepository habitScheduleRepository;

    private HabitResponse createAndParse(HabitRequest request) throws Exception {
        String response = mockMvc.perform(post("/habits")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(response, HabitResponse.class);
    }

    private HabitDetailResponse getAndParse(long id) throws Exception {
        String response = mockMvc.perform(get("/habits/" + id))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(response, HabitDetailResponse.class);
    }

    @Test
    void timezoneHeader_decidesWhichCalendarDayACompletionBelongsTo() throws Exception {
        // UTC+14 and UTC-12 are always on different calendar dates, whatever the real time is.
        String aheadZone = "Pacific/Kiritimati";
        String behindZone = "Etc/GMT+12";

        HabitRequest request = HabitTestDataFactory.aHabitRequest();
        for (DayOfWeek day : DayOfWeek.values()) {
            request.habitSchedule().add(HabitTestDataFactory.aScheduleRequest(day, LocalTime.of(6, 0), LocalTime.of(7, 0)));
        }
        long id = createAndParse(request).id();

        mockMvc.perform(post("/habits/complete/" + id).header("X-Timezone", aheadZone))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.completedToday").value(true));

        mockMvc.perform(get("/habits/" + id).header("X-Timezone", aheadZone))
            .andExpect(jsonPath("$.completedToday").value(true));
        mockMvc.perform(get("/habits/" + id).header("X-Timezone", behindZone))
            .andExpect(jsonPath("$.completedToday").value(false));
    }

    @Test
    void timezoneHeader_invalidValue_isIgnoredRatherThanRejected() throws Exception {
        mockMvc.perform(get("/habits").header("X-Timezone", "Not/AZone"))
            .andExpect(status().isOk());
    }

    @Test
    void fullLifecycle_createGetUpdateDelete_worksEndToEnd() throws Exception {
        HabitResponse created = createAndParse(HabitTestDataFactory.aHabitRequest());
        long id = created.id();

        mockMvc.perform(get("/habits/" + id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Exercise"));

        HabitRequest updatePayload = withColor(HabitTestDataFactory.aHabitRequest(), Habit.Color.AMBER);
        mockMvc.perform(put("/habits/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updatePayload)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.color").value("AMBER"));

        mockMvc.perform(delete("/habits/" + id))
            .andExpect(status().isOk());

        mockMvc.perform(get("/habits/" + id))
            .andExpect(status().isNotFound());
    }

    @Test
    void createHabit_withSchedule_persistsLinkedScheduleRows() throws Exception {
        HabitRequest request = HabitTestDataFactory.aHabitRequest();
        request.habitSchedule().add(HabitTestDataFactory.aScheduleRequest(DayOfWeek.MONDAY, LocalTime.of(6, 0), LocalTime.of(7, 0)));
        request.habitSchedule().add(HabitTestDataFactory.aScheduleRequest(DayOfWeek.WEDNESDAY, LocalTime.of(7, 0), LocalTime.of(8, 0)));

        HabitResponse created = createAndParse(request);
        HabitDetailResponse fetched = getAndParse(created.id());

        assertThat(fetched.habitSchedule()).hasSize(2);
        assertThat(fetched.habitSchedule())
            .extracting(HabitScheduleResponse::dayOfWeek)
            .containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY);
        assertThat(fetched.habitSchedule())
            .allSatisfy(s -> assertThat(s.id()).isNotZero());
    }

    @Test
    void updateHabit_scheduleSync_onlyChangedDayIsAffected() throws Exception {
        HabitRequest request = HabitTestDataFactory.aHabitRequest();
        request.habitSchedule().add(HabitTestDataFactory.aScheduleRequest(DayOfWeek.MONDAY, LocalTime.of(6, 0), LocalTime.of(7, 0)));
        request.habitSchedule().add(HabitTestDataFactory.aScheduleRequest(DayOfWeek.WEDNESDAY, LocalTime.of(7, 0), LocalTime.of(8, 0)));
        HabitResponse created = createAndParse(request);

        long wednesdayIdBeforeUpdate = created.habitSchedule().stream()
            .filter(s -> s.dayOfWeek() == DayOfWeek.WEDNESDAY)
            .findFirst().orElseThrow()
            .id();

        HabitRequest updatePayload = HabitTestDataFactory.aHabitRequest();
        updatePayload.habitSchedule().add(HabitTestDataFactory.aScheduleRequest(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0))); // changed
        updatePayload.habitSchedule().add(HabitTestDataFactory.aScheduleRequest(DayOfWeek.WEDNESDAY, LocalTime.of(7, 0), LocalTime.of(8, 0))); // unchanged
        updatePayload.habitSchedule().add(HabitTestDataFactory.aScheduleRequest(DayOfWeek.FRIDAY, LocalTime.of(18, 0), LocalTime.of(19, 0))); // new

        mockMvc.perform(put("/habits/" + created.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updatePayload)))
            .andExpect(status().isOk());

        HabitDetailResponse fetched = getAndParse(created.id());

        assertThat(fetched.habitSchedule())
            .extracting(HabitScheduleResponse::dayOfWeek)
            .containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY);

        HabitScheduleResponse wednesdayAfterUpdate = fetched.habitSchedule().stream()
            .filter(s -> s.dayOfWeek() == DayOfWeek.WEDNESDAY)
            .findFirst().orElseThrow();
        assertThat(wednesdayAfterUpdate.id())
            .as("unchanged day should keep the same row, not be deleted and recreated")
            .isEqualTo(wednesdayIdBeforeUpdate);

        HabitScheduleResponse mondayAfterUpdate = fetched.habitSchedule().stream()
            .filter(s -> s.dayOfWeek() == DayOfWeek.MONDAY)
            .findFirst().orElseThrow();
        assertThat(mondayAfterUpdate.startTime()).isEqualTo(LocalTime.of(9, 0));
    }

    @Test
    void updateHabit_cannotChangeNameViaClient_becauseTheRequestTypeHasNoSuchField() throws Exception {
        HabitResponse created = createAndParse(HabitTestDataFactory.aHabitRequest());

        // Bypass the API to give this habit a non-zero streak, since HabitRequest has no
        // currentStreak field at all - there is no way to set it through the public API.
        Habit managed = habitRepository.findById(created.id()).orElseThrow();
        managed.setCurrentStreak(5);
        habitRepository.save(managed);

        HabitRequest updatePayload = withColor(HabitTestDataFactory.aHabitRequest(), Habit.Color.PURPLE);

        mockMvc.perform(put("/habits/" + created.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updatePayload)))
            .andExpect(status().isOk());

        HabitDetailResponse fetched = getAndParse(created.id());

        assertThat(fetched.name()).isEqualTo("Exercise"); // unchanged - name isn't part of HabitRequest
        assertThat(fetched.currentStreak()).isEqualTo(5); // unchanged - also not part of HabitRequest
        assertThat(fetched.color()).isEqualTo(Habit.Color.PURPLE); // this one is actually editable
    }

    @Test
    void completeHabit_firstTimeScheduledToday_startsStreakAtOne() throws Exception {
        LocalDate today = LocalDate.now();
        HabitRequest request = HabitTestDataFactory.aHabitRequest();
        request.habitSchedule().add(HabitTestDataFactory.aScheduleRequest(today.getDayOfWeek(), LocalTime.of(6, 0), LocalTime.of(7, 0)));
        HabitResponse created = createAndParse(request);

        mockMvc.perform(post("/habits/complete/" + created.id()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentStreak").value(1))
            .andExpect(jsonPath("$.maxStreak").value(1))
            .andExpect(jsonPath("$.totalXpEarned").value(10))
            .andExpect(jsonPath("$.completedToday").value(true))
            .andExpect(jsonPath("$.totalDaysCompleted").value(1));
    }

    @Test
    void completeHabit_sameDayTwice_returns400() throws Exception {
        LocalDate today = LocalDate.now();
        HabitRequest request = HabitTestDataFactory.aHabitRequest();
        request.habitSchedule().add(HabitTestDataFactory.aScheduleRequest(today.getDayOfWeek(), LocalTime.of(6, 0), LocalTime.of(7, 0)));
        HabitResponse created = createAndParse(request);

        mockMvc.perform(post("/habits/complete/" + created.id()))
            .andExpect(status().isOk());

        mockMvc.perform(post("/habits/complete/" + created.id()))
            .andExpect(status().isBadRequest());
    }

    @Test
    void completeHabit_notScheduledToday_returns404() throws Exception {
        LocalDate today = LocalDate.now();
        DayOfWeek notToday = Arrays.stream(DayOfWeek.values())
            .filter(d -> d != today.getDayOfWeek())
            .findFirst().orElseThrow();

        HabitRequest request = HabitTestDataFactory.aHabitRequest();
        request.habitSchedule().add(HabitTestDataFactory.aScheduleRequest(notToday, LocalTime.of(6, 0), LocalTime.of(7, 0)));
        HabitResponse created = createAndParse(request);

        mockMvc.perform(post("/habits/complete/" + created.id()))
            .andExpect(status().isNotFound());
    }

    @Test
    void completeHabit_previousScheduledDayAlreadyLogged_continuesStreak() throws Exception {
        LocalDate today = LocalDate.now();
        LocalDate previousScheduledDate = today.minusDays(7); // single-day-a-week schedule => exactly one week back

        HabitRequest request = HabitTestDataFactory.aHabitRequest();
        request.habitSchedule().add(HabitTestDataFactory.aScheduleRequest(today.getDayOfWeek(), LocalTime.of(6, 0), LocalTime.of(7, 0)));
        HabitResponse created = createAndParse(request);

        // Seed a historical completion directly via the repository - the API itself
        // only ever logs "today", so backdated data has to be set up this way.
        // currentStreak is set to 1 to match what recordCompletion(false) would have
        // produced had this historical completion actually gone through the real API.
        Habit managed = habitRepository.findById(created.id()).orElseThrow();
        managed.setCurrentStreak(1);
        managed.setMaxStreak(1);
        HabitLog pastLog = new HabitLog();
        pastLog.setCompletionDate(previousScheduledDate);
        managed.addLog(pastLog);
        habitRepository.save(managed);

        mockMvc.perform(post("/habits/complete/" + created.id()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentStreak").value(2));
    }

    @Test
    void undoCompletion_afterCompleting_removesLogAndRestoresStreakAndXp() throws Exception {
        LocalDate today = LocalDate.now();
        HabitRequest request = HabitTestDataFactory.aHabitRequest();
        request.habitSchedule().add(HabitTestDataFactory.aScheduleRequest(today.getDayOfWeek(), LocalTime.of(6, 0), LocalTime.of(7, 0)));
        HabitResponse created = createAndParse(request);

        mockMvc.perform(post("/habits/complete/" + created.id()))
            .andExpect(status().isOk());

        mockMvc.perform(post("/habits/undo/" + created.id()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentStreak").value(0))
            .andExpect(jsonPath("$.totalXpEarned").value(0))
            .andExpect(jsonPath("$.completedToday").value(false))
            .andExpect(jsonPath("$.totalDaysCompleted").value(0));
    }

    @Test
    void undoCompletion_noCompletionToday_returns404() throws Exception {
        LocalDate today = LocalDate.now();
        HabitRequest request = HabitTestDataFactory.aHabitRequest();
        request.habitSchedule().add(HabitTestDataFactory.aScheduleRequest(today.getDayOfWeek(), LocalTime.of(6, 0), LocalTime.of(7, 0)));
        HabitResponse created = createAndParse(request);

        mockMvc.perform(post("/habits/undo/" + created.id()))
            .andExpect(status().isNotFound());
    }

    @Test
    void undoCompletion_doesNotLowerMaxStreak() throws Exception {
        LocalDate today = LocalDate.now();
        HabitRequest request = HabitTestDataFactory.aHabitRequest();
        request.habitSchedule().add(HabitTestDataFactory.aScheduleRequest(today.getDayOfWeek(), LocalTime.of(6, 0), LocalTime.of(7, 0)));
        HabitResponse created = createAndParse(request);

        // Give this habit a historical maxStreak higher than anything today's
        // completion/undo cycle would produce, to prove undo never touches it.
        Habit managed = habitRepository.findById(created.id()).orElseThrow();
        managed.setMaxStreak(10);
        habitRepository.save(managed);

        mockMvc.perform(post("/habits/complete/" + created.id()))
            .andExpect(status().isOk());

        mockMvc.perform(post("/habits/undo/" + created.id()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.maxStreak").value(10));
    }

    @Test
    void undoCompletion_withPriorHistory_recomputesStreakToMatchRemainingHistory() throws Exception {
        LocalDate today = LocalDate.now();
        LocalDate oneWeekBack = today.minusDays(7);
        HabitRequest request = HabitTestDataFactory.aHabitRequest();
        request.habitSchedule().add(HabitTestDataFactory.aScheduleRequest(today.getDayOfWeek(), LocalTime.of(6, 0), LocalTime.of(7, 0)));
        HabitResponse created = createAndParse(request);

        // Seed one week of real history, matching what recordCompletion would have done
        Habit managed = habitRepository.findById(created.id()).orElseThrow();
        managed.setCurrentStreak(1);
        managed.setMaxStreak(1);
        HabitLog pastLog = new HabitLog();
        pastLog.setCompletionDate(oneWeekBack);
        managed.addLog(pastLog);
        habitRepository.save(managed);

        mockMvc.perform(post("/habits/complete/" + created.id()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentStreak").value(2));

        mockMvc.perform(post("/habits/undo/" + created.id()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentStreak")
                .value(1)); // reverts to match the one remaining historical completion, not 0
    }

    @Test
    void getTodaySchedule_returnsOnlyHabitsScheduledForToday() throws Exception {
        LocalDate today = LocalDate.now();
        DayOfWeek notToday = Arrays.stream(DayOfWeek.values())
            .filter(d -> d != today.getDayOfWeek())
            .findFirst().orElseThrow();

        HabitRequest scheduledTodayRequest = HabitTestDataFactory.aHabitRequest();
        scheduledTodayRequest.habitSchedule().add(HabitTestDataFactory.aScheduleRequest(today.getDayOfWeek(), LocalTime.of(6, 0), LocalTime.of(7, 0)));
        HabitResponse scheduledToday = createAndParse(scheduledTodayRequest);

        HabitRequest scheduledOtherDayRequest = HabitTestDataFactory.aHabitRequest();
        scheduledOtherDayRequest.habitSchedule().add(HabitTestDataFactory.aScheduleRequest(notToday, LocalTime.of(6, 0), LocalTime.of(7, 0)));
        createAndParse(scheduledOtherDayRequest);

        mockMvc.perform(get("/schedule/today"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].habitId").value(scheduledToday.id()))
            .andExpect(jsonPath("$[0].completedToday").value(false));
    }

    @Test
    void updateHabit_removingScheduledDay_excludesHabitFromTodaySchedule() throws Exception {
        LocalDate today = LocalDate.now();
        HabitRequest request = HabitTestDataFactory.aHabitRequest();
        request.habitSchedule().add(HabitTestDataFactory.aScheduleRequest(today.getDayOfWeek(), LocalTime.of(6, 0), LocalTime.of(7, 0)));
        HabitResponse created = createAndParse(request);

        mockMvc.perform(get("/schedule/today"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1));

        HabitRequest updatePayload = HabitTestDataFactory.aHabitRequest(); // empty schedule list

        mockMvc.perform(put("/habits/" + created.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updatePayload)))
            .andExpect(status().isOk());

        mockMvc.perform(get("/schedule/today"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void updateHabit_removingScheduledDay_doesNotDeleteTheRow_justClosesIt() throws Exception {
        LocalDate today = LocalDate.now();
        HabitRequest request = HabitTestDataFactory.aHabitRequest();
        request.habitSchedule().add(HabitTestDataFactory.aScheduleRequest(today.getDayOfWeek(), LocalTime.of(6, 0), LocalTime.of(7, 0)));
        HabitResponse created = createAndParse(request);

        HabitRequest updatePayload = HabitTestDataFactory.aHabitRequest(); // empty schedule list
        mockMvc.perform(put("/habits/" + created.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updatePayload)))
            .andExpect(status().isOk());

        HabitDetailResponse fetched = getAndParse(created.id());
        assertThat(fetched.habitSchedule())
            .as("client-facing schedule list must no longer show the removed day")
            .isEmpty();

        List<HabitSchedule> history = habitScheduleRepository.findAll().stream()
            .filter(s -> s.getHabit().getId() == created.id())
            .toList();
        assertThat(history)
            .as("no data loss - the row itself must still exist, just closed out")
            .hasSize(1);
        assertThat(history.get(0).getEffectiveUntil()).isEqualTo(today);
    }

    @Test
    void updateHabit_removingThenReAddingSameDay_doesNotThrow_andLeavesOnlyOneActiveRow() throws Exception {
        LocalDate today = LocalDate.now();
        HabitRequest request = HabitTestDataFactory.aHabitRequest();
        request.habitSchedule().add(HabitTestDataFactory.aScheduleRequest(today.getDayOfWeek(), LocalTime.of(6, 0), LocalTime.of(7, 0)));
        HabitResponse created = createAndParse(request);

        HabitRequest removePayload = HabitTestDataFactory.aHabitRequest(); // empty schedule list
        mockMvc.perform(put("/habits/" + created.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(removePayload)))
            .andExpect(status().isOk());

        HabitRequest reAddPayload = HabitTestDataFactory.aHabitRequest();
        reAddPayload.habitSchedule().add(HabitTestDataFactory.aScheduleRequest(today.getDayOfWeek(), LocalTime.of(9, 0), LocalTime.of(10, 0)));

        mockMvc.perform(put("/habits/" + created.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reAddPayload)))
            .andExpect(status().isOk());

        HabitDetailResponse fetched = getAndParse(created.id());
        assertThat(fetched.habitSchedule())
            .as("exactly one active row for this day after the re-add")
            .hasSize(1);
        assertThat(fetched.habitSchedule().get(0).startTime()).isEqualTo(LocalTime.of(9, 0));

        List<HabitSchedule> history = habitScheduleRepository.findAll().stream()
            .filter(s -> s.getHabit().getId() == created.id())
            .toList();
        assertThat(history)
            .as("closed original row + re-added active row, both preserved")
            .hasSize(2);
    }

    private static HabitRequest withColor(HabitRequest base, Habit.Color color) {
        return new HabitRequest(
            base.name(), base.description(), base.notes(), base.icon(), color, base.unitLabel(),
            base.frequencyType(), base.reminderEnabled(), base.isActive(), base.tags(), new ArrayList<>(base.habitSchedule())
        );
    }
}
