package com.autotest.service;

import com.autotest.config.ScriptConfig;
import com.autotest.entity.Script;
import com.autotest.entity.ScriptVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ScriptConfigService 测试类
 */
class ScriptConfigServiceTest {

    @InjectMocks
    private ScriptConfigService scriptConfigService;

    @TempDir
    Path tempDir;

    @Mock
    private com.autotest.mapper.ScriptMapper scriptMapper;

    @Mock
    private com.autotest.mapper.ScriptVersionMapper scriptVersionMapper;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testParseConfig_WhenFileExists_ReturnsConfig() throws IOException {
        // 创建测试 YAML 文件
        String yamlContent = "name: test-script\n" +
            "description: Test Script\n" +
            "type: shell\n" +
            "category: cpu\n" +
            "timeout: 300\n" +
            "parameters:\n" +
            "  - name: PARAM1\n" +
            "    type: string\n" +
            "    default: value1\n" +
            "    description: Param 1\n" +
            "steps:\n" +
            "  step_1:\n" +
            "    displayName: Test\n" +
            "    script: main.sh\n" +
            "    dependsOn: []\n";

        Path scriptDir = tempDir.resolve("1");
        Files.createDirectories(scriptDir);
        Files.writeString(scriptDir.resolve("autotest.yaml"), yamlContent);

        // 设置 scriptsPath
        setScriptsPath(tempDir.toString());

        // 执行测试
        ScriptConfig config = scriptConfigService.parseConfig(1L);

        // 验证
        assertNotNull(config, "Config should not be null");
        assertEquals("test-script", config.getName());
        assertEquals("Test Script", config.getDescription());
        assertEquals("shell", config.getType());
        assertEquals("cpu", config.getCategory());
        assertEquals(300, config.getTimeout());
    }

    @Test
    void testParseConfig_WhenFileNotExists_ReturnsNull() {
        setScriptsPath(tempDir.toString());
        
        ScriptConfig config = scriptConfigService.parseConfig(999L);
        
        assertNull(config);
    }

    @Test
    void testSaveConfig_CreatesYamlFile() throws IOException {
        // 准备配置
        ScriptConfig config = new ScriptConfig();
        config.setName("new-script");
        config.setDescription("新脚本");
        config.setType("shell");
        config.setTimeout(600);

        List<ScriptConfig.ParameterConfig> params = new ArrayList<>();
        ScriptConfig.ParameterConfig param = new ScriptConfig.ParameterConfig();
        param.setName("TEST_PARAM");
        param.setType("string");
        param.setDefaultValue("default_value");
        param.setDescription("测试参数");
        params.add(param);
        config.setParameters(params);

        // 设置 scriptsPath
        setScriptsPath(tempDir.toString());

        // 执行保存
        scriptConfigService.saveConfig(1L, config);

        // 验证文件创建
        Path yamlFile = tempDir.resolve("1").resolve("autotest.yaml");
        assertTrue(Files.exists(yamlFile));
        
        // 验证内容
        String content = Files.readString(yamlFile);
        assertTrue(content.contains("name: new-script"));
        assertTrue(content.contains("TEST_PARAM"));
    }

    @Test
    void testApplyConfigToScript() {
        // 准备配置
        ScriptConfig config = new ScriptConfig();
        config.setName("config-script");
        config.setDescription("从配置创建");
        config.setType("python");
        config.setCategory("network");
        config.setTimeout(300);

        List<ScriptConfig.ParameterConfig> params = new ArrayList<>();
        ScriptConfig.ParameterConfig param = new ScriptConfig.ParameterConfig();
        param.setName("HOST");
        param.setType("string");
        param.setDefaultValue("localhost");
        params.add(param);
        config.setParameters(params);

        Map<String, Object> steps = new LinkedHashMap<>();
        steps.put("step1", Map.of("displayName", "测试步骤", "script", "test.py"));
        config.setSteps(steps);

        // 准备实体
        Script script = new Script();
        ScriptVersion version = new ScriptVersion();

        // 执行应用
        scriptConfigService.applyConfigToScript(config, script, version);

        // 验证
        assertEquals("config-script", script.getName());
        assertEquals("从配置创建", script.getDescription());
        assertEquals("python", script.getScriptType());
        assertEquals("network", script.getTestCategory());
        assertEquals(300, script.getDefaultTimeout());
        
        assertNotNull(version.getParameters());
        assertEquals(1, version.getParameters().size());
        
        assertNotNull(version.getSteps());
        assertTrue(version.getSteps().containsKey("step1"));
    }

    @Test
    void testApplyConfigToScript_WithNullConfig_DoesNotThrow() {
        Script script = new Script();
        ScriptVersion version = new ScriptVersion();
        
        assertDoesNotThrow(() -> scriptConfigService.applyConfigToScript(null, script, version));
    }

    @Test
    void testConfigExists() throws IOException {
        setScriptsPath(tempDir.toString());
        
        // 不存在时返回 false
        assertFalse(scriptConfigService.configExists(1L));
        
        // 创建文件后返回 true
        Path scriptDir = tempDir.resolve("1");
        Files.createDirectories(scriptDir);
        Files.writeString(scriptDir.resolve("autotest.yaml"), "name: test");
        
        assertTrue(scriptConfigService.configExists(1L));
    }

    @Test
    void testDeleteConfig() throws IOException {
        setScriptsPath(tempDir.toString());
        
        // 创建文件
        Path scriptDir = tempDir.resolve("1");
        Files.createDirectories(scriptDir);
        Path yamlFile = scriptDir.resolve("autotest.yaml");
        Files.writeString(yamlFile, "name: test");
        
        assertTrue(Files.exists(yamlFile));
        
        // 删除
        scriptConfigService.deleteConfig(1L);
        
        assertFalse(Files.exists(yamlFile));
    }

    /**
     * 设置 scriptsPath（使用反射）
     */
    private void setScriptsPath(String path) {
        try {
            java.lang.reflect.Field field = ScriptConfigService.class.getDeclaredField("scriptsPath");
            field.setAccessible(true);
            field.set(scriptConfigService, path);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
