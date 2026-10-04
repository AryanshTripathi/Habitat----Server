package com.habitat.server.controller;

import com.habitat.server.exception.DuplicateCompletionException;
import com.habitat.server.exception.DuplicateScheduleException;
import com.habitat.server.exception.HabitNotFoundException;
import com.habitat.server.exception.HabitScheduleNotFoundException;
import com.habitat.server.model.Habit;
import com.habitat.server.service.HabitService;
import com.habitat.server.testsupport.HabitTestDataFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.DayOfWeek;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(HabitController.class)
class HabitControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private HabitService habitService;

    @Test
    void getAllHabits_returnsJsonArray() throws Exception {
        Habit habit1 = HabitTestDataFactory.aHabit();
        Habit habit2 = HabitTestDataFactory.aHabit();
        when(habitService.getAllHabits()).thenReturn(List.of(habit1, habit2));

        mockMvc.perform(get("/habits"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getHabitById_existingId_returnsHabitJson() throws Exception {
        Habit habit = HabitTestDataFactory.aHabit();
        when(habitService.getHabitById(1L)).thenReturn(habit);

        mockMvc.perform(get("/habits/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Exercise"));
    }

    @Test
    void getHabitById_nonExistentId_returns404() throws Exception {
        when(habitService.getHabitById(99L)).thenThrow(new HabitNotFoundException(99L));

        mockMvc.perform(get("/habits/99"))
            .andExpect(status().isNotFound());
    }

    @Test
    void createHabit_validBody_returnsCreatedHabit() throws Exception {
        Habit habit = HabitTestDataFactory.aHabit();
        when(habitService.createHabit(any(Habit.class))).thenReturn(habit);

        mockMvc.perform(post("/habits")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(habit)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Exercise"));
    }

    @Test
    void createHabit_invalidColorEnum_returns400_andNeverReachesService() throws Exception {
        String invalidJson = "{\"name\":\"Exercise\",\"color\":\"ORANGE\"}";

        mockMvc.perform(post("/habits")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
            .andExpect(status().isBadRequest());

        verify(habitService, never()).createHabit(any());
    }

    @Test
    void updateHabit_validBody_returnsUpdatedHabit() throws Exception {
        Habit habit = HabitTestDataFactory.aHabit();
        when(habitService.updateHabit(eq(1L), any(Habit.class))).thenReturn(habit);

        mockMvc.perform(put("/habits/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(habit)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Exercise"));
    }

    @Test
    void updateHabit_duplicateScheduleDay_returns400() throws Exception {
        Habit habit = HabitTestDataFactory.aHabit();
        when(habitService.updateHabit(eq(1L), any(Habit.class)))
            .thenThrow(new DuplicateScheduleException(DayOfWeek.MONDAY, "Exercise"));

        mockMvc.perform(put("/habits/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(habit)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void deleteHabit_existingId_returns200_andDelegatesToService() throws Exception {
        mockMvc.perform(delete("/habits/1"))
            .andExpect(status().isOk());

        verify(habitService).deleteHabit(1L);
    }

    @Test
    void deleteHabit_nonExistentId_returns404() throws Exception {
        doThrow(new HabitNotFoundException(99L)).when(habitService).deleteHabit(99L);

        mockMvc.perform(delete("/habits/99"))
            .andExpect(status().isNotFound());
    }

    @Test
    void completeHabit_success_returnsUpdatedHabit() throws Exception {
        Habit habit = HabitTestDataFactory.aHabit();
        habit.setCurrentStreak(3);
        when(habitService.completeHabit(1L)).thenReturn(habit);

        mockMvc.perform(post("/habits/complete/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentStreak").value(3));
    }

    @Test
    void completeHabit_nonExistentId_returns404() throws Exception {
        when(habitService.completeHabit(99L)).thenThrow(new HabitNotFoundException(99L));

        mockMvc.perform(post("/habits/complete/99"))
            .andExpect(status().isNotFound());
    }

    @Test
    void completeHabit_notScheduledToday_returns404() throws Exception {
        when(habitService.completeHabit(1L)).thenThrow(new HabitScheduleNotFoundException(1L));

        mockMvc.perform(post("/habits/complete/1"))
            .andExpect(status().isNotFound());
    }

    @Test
    void completeHabit_alreadyCompletedToday_returns400() throws Exception {
        when(habitService.completeHabit(1L))
            .thenThrow(new DuplicateCompletionException("Exercise", java.time.LocalDate.now()));

        mockMvc.perform(post("/habits/complete/1"))
            .andExpect(status().isBadRequest());
    }
}
