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

    @Test
    void testAlignConfigName_ReplacesOnlyNameLine() throws IOException {
        setScriptsPath(tempDir.toString());

        Path scriptDir = tempDir.resolve("1");
        Files.createDirectories(scriptDir);
        Path yamlFile = scriptDir.resolve("autotest.yaml");
        Files.writeString(yamlFile, "# 包说明\nname: package-name\ntimeout: 300\ndescription: 保持不动\n");

        scriptConfigService.alignConfigName(1L, "改名后的脚本");

        assertEquals("# 包说明\nname: 改名后的脚本\ntimeout: 300\ndescription: 保持不动\n",
            Files.readString(yamlFile), "只应改写 name 行，注释、timeout、缩进都要保留");
    }

    @Test
    void testAlignConfigName_QuotedNameStaysValidYaml() throws IOException {
        setScriptsPath(tempDir.toString());

        Path scriptDir = tempDir.resolve("2");
        Files.createDirectories(scriptDir);
        Path yamlFile = scriptDir.resolve("autotest.yaml");
        Files.writeString(yamlFile, "name: old\ntype: shell\n");

        // 名字里带冒号会被 YAML 当成映射分隔符，必须由 yamlMapper 加引号
        scriptConfigService.alignConfigName(2L, "a: b");

        String content = Files.readString(yamlFile);
        assertTrue(content.contains("name: \"a: b\""), "实际内容: " + content);
        assertEquals("a: b", scriptConfigService.parseConfig(2L).getName());
    }

    @Test
    void testAlignConfigName_InsertsAfterCommentHeader_WhenMissing() throws IOException {
        setScriptsPath(tempDir.toString());

        Path scriptDir = tempDir.resolve("3");
        Files.createDirectories(scriptDir);
        Path yamlFile = scriptDir.resolve("autotest.yaml");
        Files.writeString(yamlFile, "# 没有声明 name\n\ntype: shell\n");

        scriptConfigService.alignConfigName(3L, "new-name");

        assertEquals("# 没有声明 name\n\nname: new-name\ntype: shell\n", Files.readString(yamlFile));
    }

    @Test
    void testAlignConfigName_KeepsCrlfAndSkipsMissingFile() throws IOException {
        setScriptsPath(tempDir.toString());

        Path scriptDir = tempDir.resolve("4");
        Files.createDirectories(scriptDir);
        Path yamlFile = scriptDir.resolve("autotest.yaml");
        Files.writeString(yamlFile, "name: old\r\ntimeout: 60\r\n");

        scriptConfigService.alignConfigName(4L, "new-name");
        assertEquals("name: new-name\r\ntimeout: 60\r\n", Files.readString(yamlFile));

        // 999 没有 autotest.yaml，静默跳过（在线编辑创建的脚本本来就没有配置文件）
        scriptConfigService.alignConfigName(999L, "new-name");
        assertFalse(Files.exists(tempDir.resolve("999")));

        // 名字为空不改写
        scriptConfigService.alignConfigName(4L, "  ");
        assertEquals("name: new-name\r\ntimeout: 60\r\n", Files.readString(yamlFile));
    }

    @Test
    void testApplyDeclaredTimeout_TakesPackageValue_WhenScriptHasNone() throws IOException {
        setScriptsPath(tempDir.toString());

        Path scriptDir = tempDir.resolve("10");
        Files.createDirectories(scriptDir);
        Files.writeString(scriptDir.resolve("autotest.yaml"), "name: pkg\ntimeout: 300\n");

        Script script = new Script();
        script.setId(10L);
        script.setName("pkg");

        assertEquals(Integer.valueOf(300), scriptConfigService.applyDeclaredTimeout(script));
        assertEquals(Integer.valueOf(300), script.getDefaultTimeout());
        verify(scriptMapper).updateById(script);
    }

    @Test
    void testApplyDeclaredTimeout_KeepsExplicitValueAndIgnoresBadInput() throws IOException {
        setScriptsPath(tempDir.toString());

        Path scriptDir = tempDir.resolve("11");
        Files.createDirectories(scriptDir);
        Files.writeString(scriptDir.resolve("autotest.yaml"), "name: pkg\ntimeout: 300\n");

        // 调用方显式给了值就不越权
        Script explicit = new Script();
        explicit.setId(11L);
        explicit.setDefaultTimeout(600);
        assertNull(scriptConfigService.applyDeclaredTimeout(explicit));
        assertEquals(Integer.valueOf(600), explicit.getDefaultTimeout());
        verify(scriptMapper, never()).updateById(any(Script.class));

        // 包里没声明 timeout
        Script undeclared = new Script();
        undeclared.setId(12L);
        assertNull(scriptConfigService.applyDeclaredTimeout(undeclared));

        // timeout 不是正数
        Path zeroDir = tempDir.resolve("13");
        Files.createDirectories(zeroDir);
        Files.writeString(zeroDir.resolve("autotest.yaml"), "name: pkg\ntimeout: 0\n");
        Script zero = new Script();
        zero.setId(13L);
        assertNull(scriptConfigService.applyDeclaredTimeout(zero));
        assertNull(zero.getDefaultTimeout());
        verify(scriptMapper, never()).updateById(any(Script.class));
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
