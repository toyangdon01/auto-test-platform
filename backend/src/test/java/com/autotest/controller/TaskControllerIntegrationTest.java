package com.autotest.controller;

import com.autotest.entity.Task;
import com.autotest.mapper.TaskMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * TaskController 集成测试
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TaskControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TaskMapper taskMapper;

    @BeforeEach
    void setUp() {
        when(taskMapper.insert(any(Task.class))).thenAnswer(invocation -> {
            Task task = invocation.getArgument(0);
            task.setId(1L);
            return 1;
        });
    }

    @Test
    void testListTasks_ReturnsPageResult() throws Exception {
        mockMvc.perform(get("/tasks")
                .param("page", "1")
                .param("size", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void testGetTask_WhenExists_ReturnsTask() throws Exception {
        // 准备数据
        Task task = new Task();
        task.setId(1L);
        task.setName("test-task");
        task.setScriptId(1L);
        task.setScriptVersion("v1.0.0");
        task.setStatus("pending");
        task.setCreatedAt(LocalDateTime.now());

        when(taskMapper.selectById(1L)).thenReturn(task);

        // 执行测试
        mockMvc.perform(get("/tasks/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.name").value("test-task"));
    }

    @Test
    void testCreateTask_WithValidData_ReturnsSuccess() throws Exception {
        // 准备数据
        Map<String, Object> request = new HashMap<>();
        request.put("name", "new-task");
        request.put("scriptId", 1);
        request.put("scriptVersion", "v1.0.0");
        request.put("serverIds", Arrays.asList(1, 2));
        request.put("executionMode", "immediate");

        // 执行测试
        mockMvc.perform(post("/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());
    }

    @Test
    void testCancelTask_WhenRunning_ReturnsSuccess() throws Exception {
        // 准备数据
        Task task = new Task();
        task.setId(1L);
        task.setName("running-task");
        task.setStatus("running");

        when(taskMapper.selectById(1L)).thenReturn(task);
        when(taskMapper.updateById(any(Task.class))).thenReturn(1);

        // 执行测试
        mockMvc.perform(post("/tasks/1/cancel"))
            .andExpect(status().isOk());
    }

    @Test
    void testDeleteTask_WhenExists_ReturnsSuccess() throws Exception {
        // 准备数据
        Task task = new Task();
        task.setId(1L);
        task.setName("to-delete");
        task.setStatus("completed");

        when(taskMapper.selectById(1L)).thenReturn(task);
        when(taskMapper.deleteById(1L)).thenReturn(1);

        // 执行测试
        mockMvc.perform(delete("/tasks/1"))
            .andExpect(status().isOk());
    }
}
