package com.autotest.agent;

import com.autotest.config.PackageSchema;
import com.autotest.config.ScriptConfig;
import com.autotest.dto.ConfigIssue;
import com.autotest.dto.ConfigParseResult;
import com.autotest.dto.ImportResult;
import com.autotest.dto.ScriptImportStatus;
import com.autotest.entity.PipelineTask;
import com.autotest.entity.Script;
import com.autotest.entity.ScriptVersion;
import com.autotest.entity.Task;
import com.autotest.mapper.PipelineTaskMapper;
import com.autotest.mapper.ScriptMapper;
import com.autotest.mapper.ScriptVersionMapper;
import com.autotest.mapper.TaskMapper;
import com.autotest.service.ScriptConfigService;
import com.autotest.service.ScriptFileService;
import com.autotest.service.ScriptPackageService;
import com.autotest.service.TaskService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import static com.autotest.agent.CatalogTools.error;
import static com.autotest.agent.CatalogTools.result;

/**
 * 面向智能体的脚本包导入与增删改工具。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScriptTools {

    private static final List<String> ACTIVE_TASK_STATUS = List.of("pending", "running");

    private final ScriptMapper scriptMapper;
    private final ScriptVersionMapper scriptVersionMapper;
    private final TaskMapper taskMapper;
    private final PipelineTaskMapper pipelineTaskMapper;
    private final TaskService taskService;
    private final ScriptConfigService scriptConfigService;
    private final ScriptPackageService scriptPackageService;
    private final ScriptFileService scriptFileService;

    @Value("${autotest.storage.scripts-path}")
    private String scriptsPath;

    @Value("${autotest.storage.temp-path}")
    private String tempPath;

    @McpTool(name = "import_package", description = "从平台主机上的脚本包目录导入或更新脚本包，等价于 UI 的 ZIP 导入。"
            + "sourceDir 既可以是包集合根目录（其下有 scripts/<包名>/），也可以直接是某个包目录（其内有 autotest.yaml）。"
            + "脚本名取包目录名；冲突默认 SKIP，要覆盖内容必须显式传 strategy=OVERWRITE。"
            + "注意 OVERWRITE 重写当前版本记录，不会新增版本。"
            + "默认按 autotest.yaml 规范严格校验，不合格直接返回 errors；先调 describe_package_spec 看规范，"
            + "确认无碍可传 strict=false 放行。",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false,
                    idempotentHint = true, title = "导入脚本包目录"))
    public Map<String, Object> importPackage(
            @McpToolParam(description = "平台主机上的目录绝对路径，如 D:/scripts-package 或 D:/scripts-package/scripts/netperf") String sourceDir,
            @McpToolParam(required = false, description = "sourceDir 是包集合根目录时，指定导入其中的哪个包") String packageName,
            @McpToolParam(required = false, description = "冲突策略：SKIP（默认，已存在则不动）、OVERWRITE（覆盖内容）、RENAME（另存为新脚本）") String strategy,
            @McpToolParam(required = false, description = "是否严格校验 autotest.yaml，默认 true") Boolean strict) {

        Path root = toPath(sourceDir);
        if (root == null) {
            return error("PARAM_INVALID", "路径不合法: " + sourceDir);
        }
        if (!Files.isDirectory(root)) {
            return error("DIR_NOT_FOUND", "目录不存在: " + sourceDir);
        }

        Path scriptDir;
        Path packageRoot;
        if (Files.isRegularFile(root.resolve("autotest.yaml"))) {
            scriptDir = root;
            packageRoot = root.getParent();
        } else {
            Path scriptsSub = root.resolve("scripts");
            if (!Files.isDirectory(scriptsSub)) {
                return error("NO_PACKAGE", "目录里既没有 autotest.yaml 也没有 scripts/ 子目录: " + sourceDir);
            }
            if (packageName == null || packageName.isBlank()) {
                return result("ok", false, "reason_code", "PARAM_INVALID",
                        "error", "sourceDir 是包集合根目录，还需要传 packageName",
                        "available", discoverPackageNames(scriptsSub));
            }
            scriptDir = scriptsSub.resolve(packageName.trim());
            packageRoot = root;
        }
        if (!Files.isDirectory(scriptDir)) {
            return error("DIR_NOT_FOUND", "脚本包目录不存在: " + scriptDir);
        }
        // 覆盖导入会先整份删除目标目录再从源目录拷贝，源等于目标时就是自我删除
        if (isUnderScriptsStorage(scriptDir)) {
            return error("SOURCE_IS_TARGET", "源目录位于平台脚本存储目录内，覆盖导入会先删掉它自己: " + scriptDir
                    + "；请把包源码放在存储目录之外，例如 D:/scripts-package");
        }
        return doImport(scriptDir, packageRoot, strategy, strict != Boolean.FALSE);
    }

    @McpTool(name = "create_script", description = "用内联文件内容新建脚本包，供没有平台磁盘访问权限的智能体使用。"
            + "files 的键是包内相对路径、值是文本内容，必须包含 autotest.yaml（步骤与参数契约由它决定）。"
            + "autotest.yaml 的字段契约、最小示例和硬规则都在 describe_package_spec 里，写包前先读它；"
            + "本工具默认严格校验，不合格返回 PACKAGE_INVALID 与逐条 errors。"
            + "二进制资源（源码 tar 包等）无法内联，需先落到平台主机目录再用 import_package。"
            + "同名脚本已存在时拒绝，此时改用 import_package 更新内容。",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false,
                    idempotentHint = false, title = "新建脚本包"))
    public Map<String, Object> createScript(
            @McpToolParam(description = "脚本名称，同时作为包目录名，需与 autotest.yaml 的 name 一致") String name,
            @McpToolParam(description = "包内文件，键为相对路径（如 autotest.yaml、main.sh），值为文本内容") Map<String, String> files,
            @McpToolParam(required = false, description = "是否严格校验 autotest.yaml，默认 true") Boolean strict) {

        if (name == null || name.isBlank()) {
            return error("PARAM_INVALID", "name 不能为空");
        }
        if (files == null || files.isEmpty()) {
            return error("PARAM_INVALID", "files 不能为空，至少要包含 autotest.yaml");
        }
        String scriptName = name.trim();
        if (findByName(scriptName) != null) {
            return error("SCRIPT_EXISTS", "脚本已存在: " + scriptName + "；改内容请用 import_package(strategy=OVERWRITE)");
        }

        Path staging;
        try {
            staging = Files.createTempDirectory(ensureTempRoot(), "mcp-create-");
        } catch (IOException e) {
            return error("INTERNAL", "创建临时目录失败: " + e.getMessage());
        }
        Path packageDir = staging.resolve("scripts").resolve(scriptName);
        try {
            Files.createDirectories(packageDir);
            for (Map.Entry<String, String> entry : files.entrySet()) {
                Path target = resolveInside(packageDir, entry.getKey());
                if (target == null) {
                    return error("PARAM_INVALID", "非法文件路径: " + entry.getKey());
                }
                Files.createDirectories(target.getParent());
                Files.writeString(target, entry.getValue() == null ? "" : entry.getValue());
            }
        } catch (IOException e) {
            return error("INTERNAL", "写入临时包文件失败: " + e.getMessage());
        }
        try {
            return doImport(packageDir, staging, null, strict != Boolean.FALSE);
        } finally {
            deleteRecursively(staging);
        }
    }

    @McpTool(name = "describe_package_spec", description = "返回脚本包 autotest.yaml 的字段契约、硬规则、最小可用示例、"
            + "常见错法和严格校验会拒绝的问题。写包或改包之前先读它："
            + "create_script/import_package 默认就按这份契约校验，不合格返回 PACKAGE_INVALID 和逐条 errors。"
            + "只需要一部分时传 section=schema|rules|example|mistakes|codes。",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, title = "脚本包规范"))
    public Map<String, Object> describePackageSpec(
            @McpToolParam(required = false, description = "只返回某一部分：schema / rules / example / mistakes / codes，缺省返回全部") String section) {

        String wanted = section == null || section.isBlank() ? "all" : section.trim().toLowerCase(Locale.ROOT);
        List<String> allowedSections = List.of("all", "schema", "rules", "example", "mistakes", "codes");
        if (!allowedSections.contains(wanted)) {
            return error("PARAM_INVALID", "section 只能是 " + allowedSections + "，收到: " + section);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("manifestFile", "autotest.yaml");
        if (matches(wanted, "schema")) {
            out.put("topLevelKeys", PackageSchema.topLevelDocs());
            out.put("parameterKeys", PackageSchema.parameterDocs());
            out.put("stepKeys", PackageSchema.stepDocs());
            out.put("resourceKeys", new ArrayList<>(PackageSchema.RESOURCE));
            out.put("tagKeys", new ArrayList<>(PackageSchema.TAG));
            out.put("optionKeys", new ArrayList<>(PackageSchema.OPTION));
        }
        if (matches(wanted, "rules")) {
            out.put("rules", PackageSchema.RULES);
        }
        if (matches(wanted, "example")) {
            out.put("exampleYaml", PackageSchema.exampleYaml());
        }
        if (matches(wanted, "mistakes")) {
            out.put("commonMistakes", PackageSchema.commonMistakes());
        }
        if (matches(wanted, "codes")) {
            out.put("strictValidation", PackageSchema.validationCodes());
        }
        out.put("howToSubmit", "纯文本包用 create_script(name, files)；带二进制的包先把目录放到平台主机，"
                + "再 import_package(sourceDir)。UI 上传则要求 autotest.yaml 在 zip 根目录。");
        out.put("howToVerify", "导入后用 get_script_contract 回读步骤和参数，确认没被裁剪");
        return out;
    }

    @McpTool(name = "update_script", description = "修改脚本包的元信息（名称、描述、分类、状态、默认超时），"
            + "未传的字段保持原值。description/testCategory/defaultTimeout 由 autotest.yaml 决定，"
            + "下次 import_package 覆盖导入会被 yaml 重新写回，长期生效要改 yaml。步骤与参数契约不在这里改。",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false,
                    idempotentHint = true, title = "修改脚本元信息"))
    public Map<String, Object> updateScript(
            @McpToolParam(description = "脚本包 id") Long scriptId,
            @McpToolParam(required = false, description = "新名称；改名后 import_package 会把它当成新脚本导入") String name,
            @McpToolParam(required = false, description = "新描述") String description,
            @McpToolParam(required = false, description = "新测试分类，如 network") String testCategory,
            @McpToolParam(required = false, description = "新状态：enabled 或 disabled") String status,
            @McpToolParam(required = false, description = "新默认超时，单位是秒（不是毫秒）") Integer defaultTimeout) {

        Script script = scriptMapper.selectById(scriptId);
        if (script == null) {
            return error("SCRIPT_NOT_FOUND", "脚本不存在: " + scriptId);
        }
        if (status != null && !status.isBlank()) {
            String normalized = status.trim().toLowerCase(Locale.ROOT);
            if (!"enabled".equals(normalized) && !"disabled".equals(normalized)) {
                return error("PARAM_INVALID", "status 只能是 enabled 或 disabled，收到: " + status);
            }
            script.setStatus(normalized);
        }
        if (name != null && !name.isBlank()) {
            Script taken = findByName(name.trim());
            if (taken != null && !taken.getId().equals(scriptId)) {
                return error("SCRIPT_NAME_TAKEN", "脚本名已存在: " + name.trim());
            }
            script.setName(name.trim());
        }
        if (description != null && !description.isBlank()) {
            script.setDescription(description);
        }
        if (testCategory != null && !testCategory.isBlank()) {
            script.setTestCategory(testCategory.trim());
        }
        if (defaultTimeout != null && defaultTimeout > 0) {
            script.setDefaultTimeout(defaultTimeout);
        }
        scriptMapper.updateById(script);

        Map<String, Object> out = describe(scriptMapper.selectById(scriptId));
        out.put("ok", true);
        out.put("note", "defaultTimeout 单位秒；steps/parameters 需通过 import_package 更新");
        return out;
    }

    @McpTool(name = "delete_script", description = "删除脚本包：磁盘文件、版本记录，以及引用它的任务与编排步骤都会一起消失。"
            + "被 pending/running 任务或编排（pipeline）引用时拒绝删除。必须显式传 confirm=true。",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = true,
                    idempotentHint = false, title = "删除脚本包"))
    public Map<String, Object> deleteScript(
            @McpToolParam(description = "脚本包 id") Long scriptId,
            @McpToolParam(description = "确认删除，必须为 true") Boolean confirm) {

        if (!Boolean.TRUE.equals(confirm)) {
            return error("CONFIRM_REQUIRED", "删除脚本包需要显式 confirm=true");
        }
        Script script = scriptMapper.selectById(scriptId);
        if (script == null) {
            return error("SCRIPT_NOT_FOUND", "脚本不存在: " + scriptId);
        }

        List<Long> active = taskMapper.selectList(new LambdaQueryWrapper<Task>()
                .eq(Task::getScriptId, scriptId)
                .in(Task::getStatus, ACTIVE_TASK_STATUS))
                .stream().map(Task::getId).toList();
        if (!active.isEmpty()) {
            return error("SCRIPT_IN_USE", "脚本 " + scriptId + " 还有活动任务，先取消再删: " + active);
        }
        List<Long> pipelines = pipelineTaskMapper.selectList(new LambdaQueryWrapper<PipelineTask>()
                .eq(PipelineTask::getScriptId, scriptId))
                .stream().map(PipelineTask::getPipelineId).distinct().toList();
        if (!pipelines.isEmpty()) {
            return error("SCRIPT_IN_USE", "脚本 " + scriptId + " 被这些编排引用，删除会连带清空编排步骤: " + pipelines);
        }

        List<Task> history = taskMapper.selectList(new LambdaQueryWrapper<Task>()
                .eq(Task::getScriptId, scriptId));
        for (Task task : history) {
            taskService.deleteTask(task.getId());
        }
        try {
            scriptFileService.deleteScriptFiles(scriptId);
        } catch (IOException e) {
            log.warn("删除脚本文件目录失败，继续删除记录: scriptId={}, {}", scriptId, e.getMessage());
        }
        scriptMapper.deleteById(scriptId);

        return result("ok", true, "deletedScriptId", scriptId, "name", script.getName(),
                "deletedTaskHistory", history.size(),
                "note", "版本记录与资源关联由数据库外键级联清理");
    }

    // ==================== 内部方法 ====================

    private static boolean matches(String wanted, String section) {
        return "all".equals(wanted) || wanted.equals(section);
    }

    /**
     * 校验不过时的返回：逐条给出出错位置、后果和改法，让智能体不用读文档就能自己修完再来一次
     */
    private Map<String, Object> invalidPackage(ConfigParseResult parsed, String action) {
        List<Map<String, Object>> issues = new ArrayList<>();
        for (ConfigIssue issue : parsed.getValidationErrors()) {
            issues.add(result("code", issue.code(), "path", issue.path(),
                    "error", issue.message(), "hint", issue.hint()));
        }
        Map<String, Object> out = error("PACKAGE_INVALID",
                "autotest.yaml 有 " + issues.size() + " 处问题，未" + action + "；按 errors 逐条改完再试，"
                        + "确认都不影响你要做的事可以传 strict=false");
        out.put("errors", issues);
        out.put("next", "describe_package_spec");
        return out;
    }

    private Map<String, Object> doImport(Path scriptDir, Path packageRoot, String strategy, boolean strict) {
        String scriptName = scriptDir.getFileName().toString();

        ConfigParseResult parsed = scriptConfigService.parseConfigFromTempWithDetails(scriptDir.toString());
        if (parsed.getStatus() == ConfigParseResult.Status.FILE_NOT_FOUND) {
            return error("NO_MANIFEST", "包目录里没有 autotest.yaml: " + scriptDir);
        }
        if (!parsed.isSuccess() || !parsed.hasConfig()) {
            return error("YAML_INVALID", "autotest.yaml 解析失败: " + scriptDir + " - " + parsed.getError()
                    + (parsed.getErrorLocation() == null ? "" : " @ " + parsed.getErrorLocation()));
        }
        ScriptConfig config = parsed.getConfig();

        ScriptPackageService.ConflictStrategy useStrategy;
        if (strategy == null || strategy.isBlank()) {
            useStrategy = ScriptPackageService.ConflictStrategy.SKIP;
        } else {
            try {
                useStrategy = ScriptPackageService.ConflictStrategy.valueOf(strategy.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return error("PARAM_INVALID", "strategy 只能是 SKIP/OVERWRITE/RENAME，收到: " + strategy);
            }
        }

        // SKIP 且脚本已存在时这次什么都不写，就不拿校验错误挡路
        if (strict && parsed.hasValidationErrors()
                && (useStrategy != ScriptPackageService.ConflictStrategy.SKIP || findByName(scriptName) == null)) {
            return invalidPackage(parsed, "导入");
        }

        ImportResult importResult = new ImportResult();
        importResult.setTotal(1);
        try {
            scriptPackageService.importScriptFromDirectory(scriptDir, config, scriptName, packageRoot,
                    useStrategy, importResult);
        } catch (IOException | RuntimeException e) {
            return error("IMPORT_FAILED", "导入失败: " + scriptName + " - " + e.getMessage());
        }

        ScriptImportStatus status = importResult.getScripts().isEmpty() ? null : importResult.getScripts().get(0);
        if (status == null) {
            return error("IMPORT_FAILED", "导入没有产生任何结果: " + importResult.getErrors());
        }
        if ("failed".equals(status.getStatus())) {
            return error("IMPORT_FAILED", "导入失败: " + scriptName + " - " + status.getError());
        }
        if ("skipped".equals(status.getStatus())) {
            Script existing = findByName(scriptName);
            return result("ok", false, "reason_code", "SCRIPT_EXISTS",
                    "error", "脚本 " + scriptName + " 已存在，内容未改动",
                    "scriptId", existing == null ? null : existing.getId(),
                    "next", "import_package(strategy=OVERWRITE) 覆盖内容，或 RENAME 另存一份");
        }

        Script saved = scriptMapper.selectById(status.getId());
        if (saved == null) {
            return error("INTERNAL", "导入返回成功但查不到脚本: " + status.getId());
        }
        Map<String, Object> out = describe(saved);
        out.put("ok", true);
        out.put("result", status.getStatus());
        out.put("message", status.getMessage());
        List<String> notes = new ArrayList<>();
        notes.addAll(importResult.getWarnings());
        notes.addAll(parsed.getWarnings());
        // strict=false 时校验错误不挡路，但必须让调用方看见
        notes.addAll(parsed.validationErrorTexts());
        if (config.getName() != null && !config.getName().equals(scriptName)) {
            notes.add("autotest.yaml 的 name=" + config.getName() + "，但脚本名按目录名取 " + scriptName);
        }
        if (ScriptPackageService.ConflictStrategy.OVERWRITE == useStrategy) {
            notes.add("覆盖导入只重写当前版本记录，未新增版本");
        }
        out.put("notes", notes);
        out.put("resources", config.getResources() == null ? List.of() : config.getResources().size());
        out.put("next", "get_script_contract");
        return out;
    }

    private Map<String, Object> describe(Script script) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("scriptId", script.getId());
        out.put("name", script.getName());
        out.put("currentVersion", script.getCurrentVersion());
        out.put("scriptType", script.getScriptType());
        out.put("testCategory", script.getTestCategory());
        out.put("status", script.getStatus());
        out.put("defaultTimeoutSeconds", script.getDefaultTimeout());
        ScriptVersion version = findVersion(script.getId(), script.getCurrentVersion());
        out.put("fileCount", version == null || version.getFileList() == null ? 0 : version.getFileList().size());
        out.put("steps", version == null || version.getSteps() == null
                ? List.of() : new ArrayList<>(version.getSteps().keySet()));
        out.put("paramCount", version == null || version.getParameters() == null ? 0 : version.getParameters().size());
        return out;
    }

    private Script findByName(String name) {
        return scriptMapper.selectOne(new LambdaQueryWrapper<Script>().eq(Script::getName, name));
    }

    private ScriptVersion findVersion(Long scriptId, String version) {
        if (version == null || version.isBlank()) {
            return null;
        }
        return scriptVersionMapper.selectOne(new LambdaQueryWrapper<ScriptVersion>()
                .eq(ScriptVersion::getScriptId, scriptId)
                .eq(ScriptVersion::getVersion, version.trim()));
    }

    private List<String> discoverPackageNames(Path scriptsSub) {
        List<String> names = new ArrayList<>();
        try (Stream<Path> dirs = Files.list(scriptsSub)) {
            dirs.filter(Files::isDirectory)
                    .map(dir -> dir.getFileName().toString())
                    .sorted()
                    .forEach(names::add);
        } catch (IOException e) {
            log.warn("列举包目录失败: {}", scriptsSub);
        }
        return names;
    }

    private Path ensureTempRoot() throws IOException {
        Path root = Paths.get(tempPath);
        Files.createDirectories(root);
        return root;
    }

    private Path toPath(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Paths.get(raw.trim());
        } catch (InvalidPathException e) {
            return null;
        }
    }

    private boolean isUnderScriptsStorage(Path path) {
        Path base = Paths.get(scriptsPath).toAbsolutePath().normalize();
        return path.toAbsolutePath().normalize().startsWith(base);
    }

    private Path resolveInside(Path baseDir, String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return null;
        }
        String cleaned = relativePath.trim().replace('\\', '/');
        if (cleaned.startsWith("/") || cleaned.contains("..") || cleaned.contains(":")) {
            return null;
        }
        Path resolved = baseDir.resolve(cleaned).normalize();
        return resolved.startsWith(baseDir) ? resolved : null;
    }

    private void deleteRecursively(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    log.warn("清理临时文件失败: {}", path);
                }
            });
        } catch (IOException e) {
            log.warn("清理临时目录失败: {}", dir);
        }
    }
}
