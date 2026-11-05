package com.autotest.service;

import com.autotest.config.ScriptConfig;
import com.autotest.dto.ConfigIssue;
import com.autotest.dto.ConfigParseResult;
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

    @Test
    void testSchemaIssues_CleanPackage_HasNoErrors() throws IOException {
        Path dir = tempDir.resolve("20");
        writePackage(dir, "name: demo\n"
                + "description: 合规包\n"
                + "type: shell\n"
                + "category: compute\n"
                + "timeout: 600\n"
                + "tags:\n"
                + "  - key: os\n"
                + "    value: kylin\n"
                + "parameters:\n"
                + "  - name: MODE\n"
                + "    type: select\n"
                + "    required: true\n"
                + "    options:\n"
                + "      - label: 快速\n"
                + "        value: fast\n"
                + "resources:\n"
                + "  - resourceMd5: abc123\n"
                + "    targetPath: tools/x.tar.gz\n"
                + "steps:\n"
                + "  _meta:\n"
                + "    note: 元数据不参与执行\n"
                + "  step_1:\n"
                + "    script: steps/run.sh\n"
                + "    dependsOn: []\n"
                + "  step_2:\n"
                + "    script: uptime\n"
                + "    dependsOn: [step_1]\n",
                "steps/run.sh", "#!/bin/sh\necho ok\n");

        assertTrue(parsePackage(dir).getValidationErrors().isEmpty());
    }

    @Test
    void testSchemaIssues_UnknownKeys_ReportedWithLegalList() throws IOException {
        Path dir = tempDir.resolve("21");
        writePackage(dir, "name: demo\n"
                + "version: 1.0.0\n"
                + "paramaters:\n  - name: A\n"
                + "type: shell\n"
                + "steps:\n"
                + "  step_1:\n"
                + "    script: main.sh\n"
                + "    timeout: 30\n",
                "main.sh", "#!/bin/sh\necho ok\n");

        ConfigParseResult result = parsePackage(dir);
        List<ConfigIssue> unknown = result.getValidationErrors().stream()
                .filter(item -> "UNKNOWN_KEY".equals(item.code()))
                .toList();
        assertEquals(3, unknown.size(), unknown.toString());
        assertEquals(List.of("version", "paramaters", "steps.step_1.timeout"),
                unknown.stream().map(ConfigIssue::path).toList());
        for (ConfigIssue item : unknown) {
            assertTrue(item.hint().contains("合法键"), item.hint());
        }
        // 拼错的键要给出候选，让智能体一眼看出是写错了不是写漏了
        assertTrue(unknown.get(1).hint().contains("'parameters'"), unknown.get(1).hint());
    }

    @Test
    void testSchemaIssues_ScriptNotFoundButInlineCommandAllowed() throws IOException {
        Path dir = tempDir.resolve("22");
        writePackage(dir, "name: demo\ntype: shell\nsteps:\n"
                + "  step_1:\n    script: main.sch\n"
                + "  step_2:\n    script: bash run.sh --fast\n"
                + "  step_3:\n    script: uptime\n", "run.sh", "#!/bin/sh\n");

        List<ConfigIssue> issues = parsePackage(dir).getValidationErrors();
        assertEquals(1, issues.size(), issues.toString());
        assertEquals("STEP_SCRIPT_NOT_FOUND", issues.get(0).code());
        assertTrue(issues.get(0).hint().contains("run.sh"), issues.get(0).hint());
    }

    @Test
    void testSchemaIssues_DependencyProblems() throws IOException {
        Path dir = tempDir.resolve("23");
        writePackage(dir, "name: demo\ntype: shell\nsteps:\n"
                + "  a:\n    script: main.sh\n    dependsOn: [missing]\n"
                + "  b:\n    script: main.sh\n    dependsOn: [c]\n"
                + "  c:\n    script: main.sh\n    dependsOn: [b]\n", "main.sh", "#!/bin/sh\n");

        List<String> codes = codes(parsePackage(dir));
        assertTrue(codes.contains("DEPENDS_ON_UNKNOWN"), codes.toString());
        assertTrue(codes.contains("DEPENDENCY_CYCLE"), codes.toString());
    }

    @Test
    void testSchemaIssues_ParameterShapes() throws IOException {
        Path dir = tempDir.resolve("24");
        writePackage(dir, "name: demo\ntype: shell\n"
                + "parameters:\n"
                + "  - name: A\n    required: \"true\"\n"
                + "  - name: A\n"
                + "  - type: string\n"
                + "  - name: B\n    options:\n"
                + "      - label: 只填展示名是合法的\n"
                + "      - label: 空提交值\n        value: \"\"\n",
                "main.sh", "#!/bin/sh\n");

        List<String> codes = codes(parsePackage(dir));
        assertTrue(codes.contains("PARAMETER_REQUIRED_NOT_BOOLEAN"), codes.toString());
        assertTrue(codes.contains("PARAMETER_NAME_DUPLICATED"), codes.toString());
        assertTrue(codes.contains("PARAMETER_NAME_MISSING"), codes.toString());
        assertEquals(1, codes.stream().filter("OPTION_VALUE_EMPTY"::equals).count(), codes.toString());
    }

    @Test
    void testSchemaIssues_StructureErrors() throws IOException {
        Path dir = tempDir.resolve("25");
        writePackage(dir, "name: demo\n"
                + "type: bash\n"
                + "timeout: 0\n"
                + "tags: kylin\n"
                + "resources:\n  - targetPath: a.sh\n",
                "main.sh", "#!/bin/sh\n");

        List<String> codes = codes(parsePackage(dir));
        assertTrue(codes.contains("TYPE_INVALID"), codes.toString());
        assertTrue(codes.contains("TIMEOUT_INVALID"), codes.toString());
        assertTrue(codes.contains("TAGS_NOT_ARRAY"), codes.toString());
        assertTrue(codes.contains("RESOURCE_LOCATOR_MISSING"), codes.toString());
        assertTrue(codes.contains("STEPS_MISSING"), codes.toString());
    }

    @Test
    void testSchemaIssues_StepsArrayWarnsButNotFails() throws IOException {
        Path dir = tempDir.resolve("26");
        writePackage(dir, "name: demo\ntype: shell\nsteps:\n"
                + "  - name: first\n    script: main.sh\n", "main.sh", "#!/bin/sh\n");

        ConfigParseResult result = parsePackage(dir);
        assertTrue(result.getValidationErrors().isEmpty(), result.getValidationErrors().toString());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("数组")), result.getWarnings().toString());
    }

    @Test
    void testSchemaIssues_CrlfScriptRejected() throws IOException {
        Path dir = tempDir.resolve("27");
        writePackage(dir, "name: demo\ntype: shell\nsteps:\n  step_1:\n    script: main.sh\n",
                "main.sh", "#!/bin/sh\r\necho ok\r\n");

        List<String> codes = codes(parsePackage(dir));
        assertTrue(codes.contains("CRLF_SCRIPT"), codes.toString());
    }

    @Test
    void testSchemaIssues_MultipleCollectorStepsRejected() throws IOException {
        Path dir = tempDir.resolve("28");
        writePackage(dir, "name: demo\ntype: shell\nsteps:\n"
                + "  a:\n    script: main.sh\n    resultParser: true\n"
                + "  b:\n    script: main.sh\n    resultCollector: true\n", "main.sh", "#!/bin/sh\n");

        List<ConfigIssue> issues = parsePackage(dir).getValidationErrors();
        List<ConfigIssue> hits = issues.stream()
                .filter(i -> "RESULT_COLLECTOR_MULTIPLE".equals(i.code()))
                .toList();
        assertEquals(1, hits.size(), issues.toString());
        assertTrue(hits.get(0).message().contains("a, b"), hits.get(0).message());
    }

    @Test
    void testSchemaIssues_SingleCollectorStepAccepted() throws IOException {
        Path dir = tempDir.resolve("29");
        writePackage(dir, "name: demo\ntype: shell\nsteps:\n"
                + "  a:\n    script: main.sh\n    resultParser: false\n    resultCollector: false\n"
                + "  b:\n    script: main.sh\n    resultParser: true\n", "main.sh", "#!/bin/sh\n");

        List<String> codes = codes(parsePackage(dir));
        assertFalse(codes.contains("RESULT_COLLECTOR_MULTIPLE"), codes.toString());
    }

    /**
     * 写一个包目录：yaml + 若干文件
     */
    private Path writePackage(Path dir, String yaml, String... fileNameAndContent) throws IOException {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("autotest.yaml"), yaml);
        for (int i = 0; i + 1 < fileNameAndContent.length; i += 2) {
            Path file = dir.resolve(fileNameAndContent[i]);
            Files.createDirectories(file.getParent());
            Files.writeString(file, fileNameAndContent[i + 1]);
        }
        return dir;
    }

    private ConfigParseResult parsePackage(Path dir) {
        return scriptConfigService.parseConfigFromTempWithDetails(dir.toString());
    }

    private List<String> codes(ConfigParseResult result) {
        List<String> codes = new ArrayList<>();
        for (ConfigIssue issue : result.getValidationErrors()) {
            codes.add(issue.code());
        }
        return codes;
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
