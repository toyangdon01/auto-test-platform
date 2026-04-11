package com.autotest.controller;

import com.autotest.entity.Script;
import com.autotest.entity.ScriptVersion;
import com.autotest.mapper.ScriptMapper;
import com.autotest.mapper.ScriptVersionMapper;
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
 * ScriptController 集成测试
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ScriptControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ScriptMapper scriptMapper;

    @MockBean
    private ScriptVersionMapper scriptVersionMapper;

    @BeforeEach
    void setUp() {
        // 默认返回
        when(scriptMapper.insert(any(Script.class))).thenAnswer(invocation -> {
            Script script = invocation.getArgument(0);
            script.setId(1L);
            return 1;
        });

        when(scriptVersionMapper.insert(any(ScriptVersion.class))).thenReturn(1);
    }

    @Test
    void testListScripts_ReturnsPageResult() throws Exception {
        // 准备数据
        List<Script> scripts = new ArrayList<>();
        Script script = new Script();
        script.setId(1L);
        script.setName("test-script");
        script.setScriptType("shell");
        script.setTestCategory("cpu");
        script.setStatus("enabled");
        script.setCreatedAt(LocalDateTime.now());
        scripts.add(script);

        when(scriptMapper.selectPage(any(), any())).thenReturn(
            new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 20, scripts.size())
        );

        // 执行测试
        mockMvc.perform(get("/scripts")
                .param("page", "1")
                .param("size", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void testGetScript_WhenExists_ReturnsScript() throws Exception {
        // 准备数据
        Script script = new Script();
        script.setId(1L);
        script.setName("test-script");
        script.setDescription("测试脚本");
        script.setScriptType("shell");
        script.setTestCategory("cpu");
        script.setStatus("enabled");
        script.setCurrentVersion("v1.0.0");

        when(scriptMapper.selectById(1L)).thenReturn(script);

        // 执行测试
        mockMvc.perform(get("/scripts/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.name").value("test-script"));
    }

    @Test
    void testGetScript_WhenNotExists_ReturnsNull() throws Exception {
        when(scriptMapper.selectById(999L)).thenReturn(null);

        mockMvc.perform(get("/scripts/999"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void testCreateScript_WithValidData_ReturnsSuccess() throws Exception {
        // 准备数据
        Script script = new Script();
        script.setName("new-script");
        script.setScriptType("shell");
        script.setTestCategory("cpu");

        // 执行测试
        mockMvc.perform(post("/scripts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(script)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        // 验证
        verify(scriptMapper).insert(any(Script.class));
    }

    @Test
    void testUpdateScript_WhenExists_ReturnsSuccess() throws Exception {
        // 准备数据
        Script existingScript = new Script();
        existingScript.setId(1L);
        existingScript.setName("old-name");
        existingScript.setScriptType("shell");
        existingScript.setTestCategory("cpu");
        existingScript.setCurrentVersion("v1.0.0");

        Script updateScript = new Script();
        updateScript.setName("new-name");
        updateScript.setDescription("更新描述");

        when(scriptMapper.selectById(1L)).thenReturn(existingScript);
        when(scriptMapper.updateById(any(Script.class))).thenReturn(1);

        // 执行测试
        mockMvc.perform(put("/scripts/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateScript)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        // 验证
        verify(scriptMapper).updateById(any(Script.class));
    }

    @Test
    void testDeleteScript_WhenNoTasks_ReturnsSuccess() throws Exception {
        // 准备数据
        Script script = new Script();
        script.setId(1L);
        script.setName("to-delete");

        when(scriptMapper.selectById(1L)).thenReturn(script);
        when(scriptMapper.deleteById(1L)).thenReturn(1);

        // 执行测试
        mockMvc.perform(delete("/scripts/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        // 验证
        verify(scriptMapper).deleteById(1L);
    }
}
