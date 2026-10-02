package com.habitat.server;

import com.habitat.server.model.Habit;
import com.habitat.server.model.HabitSchedule;
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
}
