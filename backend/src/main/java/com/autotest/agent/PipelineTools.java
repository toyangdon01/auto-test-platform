package com.autotest.agent;

import com.autotest.config.PackageSchema;
import com.autotest.dto.pipeline.PipelineYamlConfig;
import com.autotest.dto.pipeline.ServerYamlConfig;
import com.autotest.dto.pipeline.TaskYamlConfig;
import com.autotest.entity.Pipeline;
import com.autotest.entity.PipelineRun;
import com.autotest.entity.PipelineRunTask;
import com.autotest.entity.PipelineTask;
import com.autotest.entity.Script;
import com.autotest.entity.Server;
import com.autotest.entity.Task;
import com.autotest.entity.TaskServer;
import com.autotest.mapper.PipelineMapper;
import com.autotest.mapper.PipelineRunMapper;
import com.autotest.mapper.PipelineRunTaskMapper;
import com.autotest.mapper.PipelineTaskMapper;
import com.autotest.mapper.ScriptMapper;
import com.autotest.mapper.ServerMapper;
import com.autotest.mapper.TaskMapper;
import com.autotest.mapper.TaskServerMapper;
import com.autotest.service.PipelineImportService;
import com.autotest.service.PipelineService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.autotest.agent.CatalogTools.error;
import static com.autotest.agent.CatalogTools.result;

/**
 * 面向智能体的任务编排（流水线）工具：查看、按 YAML 建改、触发执行、等待、取消。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PipelineTools {

    private static final Set<String> ACTIVE_RUN = Set.of("pending", "running");
    private static final Set<String> TERMINAL_RUN = Set.of("completed", "failed", "cancelled");
    private static final Set<String> TERMINAL_TASK = Set.of(
            "completed", "completed_with_errors", "failed", "cancelled");
    /** 平台本机执行的虚拟服务器 id，不在 servers 表里 */
    private static final long LOCAL_SERVER_ID = -1L;

    /**
     * 编排 YAML 的字段契约：键表就是校验用的合法键集（下面的 YAML_* 直接从它取），
     * describe_pipeline_spec 返回的也是它 —— 一处维护，不会和校验器漂移。
     */
    private static final Map<String, String> TOP_DOCS = docs(
            "name", "编排名称，必填。库里不查重名，同名会再建一条独立编排；建之前用 list_pipelines 看清",
            "description", "可选，给人看的说明",
            "maxParallel", "可选，默认 5。整条编排同时运行的任务数上限，不是某个任务自己的并发",
            "servers", "可选，主机定义数组。只在「随编排一起新建主机」时写：按 name 匹配平台已有主机，"
                    + "存在则连凭据一起覆盖（默认拒绝，需 allowExistingServerUpdates=true）。"
                    + "引用已有主机不用写这段，直接在任务的 stepServerMapping 里写名称或 id",
            "tasks", "必填，非空数组。YAML 里的先后只决定列表顺序（orderNum），执行顺序完全由每个任务的 dependsOn 决定");

    private static final Map<String, String> TASK_DOCS = docs(
            "name", "必填，编排内唯一（重名会让调度直接抛异常）；dependsOn 按这个名字引用",
            "script", "与 scriptId 二选一。脚本包名，区分大小写、要与包完全一致；用 list_scripts 查",
            "scriptId", "与 script 二选一。脚本 id（数字）",
            "dependsOn", "可选，同编排内的任务名数组。不写就和其他无依赖任务并行开跑（受 maxParallel 限制）；"
                    + "名字写错返回 DEPENDS_ON_UNKNOWN，成环返回 DEPENDS_ON_CYCLE",
            "timeout", "可选，单位**秒**（入库 ×1000，get_pipeline 返回的 timeoutMs 才是毫秒）。"
                    + "不填则执行时回落到脚本包声明的默认超时",
            "stepServerMapping", "事实必填：{步骤名: [服务器名或 id]}。步骤名必须与脚本包 autotest.yaml 里 steps 的键"
                    + "完全一致（用 get_script_contract 查）—— 写错不报错，只是那个步骤执行时拿不到主机。"
                    + "一个步骤列多台主机会并行执行同一批脚本",
            "sharedParams", "可选，{参数名: 值}，整个任务共享。参数名、必填与可选值都来自 get_script_contract",
            "stepParams", "可选，{步骤名: {参数名: 值}}，覆盖同名的 sharedParams");

    private static final Map<String, String> SERVER_DOCS = docs(
            "name", "必填，按它匹配平台已有主机（存在即覆盖，默认被 SERVER_EXISTS_WOULD_OVERWRITE 拒掉）",
            "host", "必填，主机地址",
            "port", "可选，默认 22",
            "username", "必填",
            "authType", "可选，只能是 password 或 key；留空按 password",
            "authSecret", "必填，密码或私钥文本",
            "groupId", "可选，分组 id",
            "tags", "可选，标签列表",
            "remark", "可选，备注");

    private static final Set<String> YAML_TOP = TOP_DOCS.keySet();
    private static final Set<String> YAML_TASK = TASK_DOCS.keySet();
    private static final Set<String> YAML_SERVER = SERVER_DOCS.keySet();

    /** 写编排前该知道的硬规则，与校验器、服务层行为一致 */
    private static final List<String> AUTHOR_RULES = List.of(
            "键名全驼峰。max_parallel、step_params 这类下划线写法会被 UNKNOWN_KEY 拒掉，报错里给候选拼写。",
            "主机只在任务的 stepServerMapping 里给，任务级没有 servers 键；一个任务的所有步骤都拿不到主机时，"
                    + "run_pipeline 直接返回 NO_SERVERS —— 服务层原本是静默跳过这个任务，依赖它的任务一起卡住。",
            "stepServerMapping 的键必须是脚本包里的步骤名，取自 get_script_contract；这是校验唯一挡不住的一层，"
                    + "写错的形状合法，只是那个步骤执行时没有主机。",
            "没有 dependsOn 的任务彼此并行，受 maxParallel 限制；要串行就显式写 dependsOn，"
                    + "YAML 里的先后顺序不产生执行顺序。",
            "timeout 在 YAML 里是秒，入库是毫秒（get_pipeline 返回 timeoutMs）；任务没填时执行时回落到脚本包声明的默认超时。",
            "参数键是脚本包声明的参数名。必填（required）缺值、或值不在 options 白名单里，run_pipeline 会被拦下；"
                    + "stepParams 覆盖同名 sharedParams。",
            "update_pipeline 是全量覆盖：YAML 里没写的任务会被删除，pipelineTaskId 全部重建（先删后插），"
                    + "历史执行记录不受影响。改之前先 export_pipeline 拿完整文本。",
            "同一时刻只允许一条未结束的执行：已有 pending/running 的 run 时 run_pipeline 返回 ALREADY_RUNNING，"
                    + "这不是 YAML 写错了，先 wait_for_pipeline_run 或 cancel_pipeline_run。",
            "目标主机上已经有 pending/running 任务时 run_pipeline 返回 BUSY：SSH 连接池只有 2 条，"
                    + "并发跑只会让两边都变慢。",
            "编排 enabled=false 时 run_pipeline 拒绝执行；但任务级 enabled=false 服务层不看，照样会跑，"
                    + "返回值里的 warnings 会说明这一点。",
            "servers 段会按 name 覆盖同名主机并连凭据一起换，所以默认被拒；引用已有主机根本不需要写这段。",
            "authType 只认 password 和 key。写 ssh_key 之类的值，SSH 层两个分支都不匹配（既不设密码也不加载私钥），"
                    + "要等连接失败才看得见原因，所以提交时就拒。");

    /** 一份合法但不可直接提交的编排：占位符强制让智能体先查真实值，猜出来的名字会撞上精确的错误码 */
    private static final String EXAMPLE_YAML = """
            name: <编排名称>              # 必填；重名不报错，会再建一条
            description: <可选说明>
            maxParallel: 2               # 可选，默认 5；同时运行的任务上限
            tasks:
              - name: baseline           # 必填，编排内唯一；dependsOn 按这个名字引用
                script: <脚本包名>        # 必填（或改用 scriptId）；list_scripts 查，区分大小写
                timeout: 3600            # 可选，单位秒；不填用脚本包声明的默认超时
                stepServerMapping:       # 事实必填；步骤名来自 get_script_contract
                  step_1: [<服务器名或 id>]   # 主机来自 list_servers；一个步骤可以列多台并行
                sharedParams:            # 可选；键是脚本包声明的参数名
                  <参数名>: <值>
              - name: fio-4k
                script: <另一个脚本包名>
                dependsOn: [baseline]    # 不写就和别的无依赖任务一起并行开跑
                stepServerMapping:
                  <步骤名>: [<服务器名或 id>]
                stepParams:              # 可选；覆盖同名 sharedParams
                  <步骤名>:
                    <参数名>: <值>
            # 想连主机一起新建才加 servers 段（name/host/username/authSecret 必填，authType 只认 password|key）；
            # 引用平台上已有的主机不要写这段。
            """;

    /** YAML 解析用独立的 mapper：Spring 那个配了 fail-on-unknown-properties=false，会洗掉写错的键 */
    private static final ObjectMapper YAML_READER = new ObjectMapper(new YAMLFactory());

    private final PipelineMapper pipelineMapper;
    private final PipelineTaskMapper pipelineTaskMapper;
    private final PipelineRunMapper pipelineRunMapper;
    private final PipelineRunTaskMapper pipelineRunTaskMapper;
    private final TaskMapper taskMapper;
    private final TaskServerMapper taskServerMapper;
    private final ScriptMapper scriptMapper;
    private final ServerMapper serverMapper;
    private final PipelineService pipelineService;
    private final PipelineImportService pipelineImportService;
    private final ObjectMapper objectMapper;

    @McpTool(name = "list_pipelines", description = "列出任务编排（流水线）：id、名称、任务数、最大并行数、是否启用、"
            + "最近一次执行的状态。先用它找 pipelineId，再用 get_pipeline 看任务与依赖。",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, title = "列出任务编排"))
    public Map<String, Object> listPipelines(
            @McpToolParam(required = false, description = "按名称或描述模糊过滤，不区分大小写") String keyword,
            @McpToolParam(required = false, description = "最多返回多少条，默认 50") Integer limit) {

        int max = limit == null || limit <= 0 ? 50 : Math.min(limit, 200);
        String kw = keyword == null || keyword.isBlank() ? null : keyword.trim().toLowerCase();

        List<Map<String, Object>> items = new ArrayList<>();
        for (Pipeline p : pipelineMapper.selectList(new LambdaQueryWrapper<Pipeline>().orderByDesc(Pipeline::getId))) {
            if (kw != null && !contains(p.getName(), kw) && !contains(p.getDescription(), kw)) {
                continue;
            }
            Long count = pipelineTaskMapper.selectCount(new LambdaQueryWrapper<PipelineTask>()
                    .eq(PipelineTask::getPipelineId, p.getId()));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", p.getId());
            item.put("name", p.getName());
            item.put("description", p.getDescription());
            item.put("maxParallel", p.getMaxParallel());
            item.put("enabled", p.getEnabled());
            item.put("taskCount", count);
            item.put("lastRun", lastRunBrief(p.getId()));
            items.add(item);
            if (items.size() >= max) {
                break;
            }
        }
        return result("pipelines", items, "count", items.size(), "next", "get_pipeline");
    }

    @McpTool(name = "get_pipeline", description = "看一条编排的完整定义：每个编排任务的 pipelineTaskId、名称、脚本、"
            + "主机、步骤服务器映射、参数、依赖（按任务名写）、超时（毫秒），外加 problems 体检结果和最近几次执行。"
            + "run_pipeline 的 serverMapping 键可以用这里的 pipelineTaskId 或任务名。",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, title = "查看编排详情"))
    public Map<String, Object> getPipeline(
            @McpToolParam(description = "编排 id，来自 list_pipelines") Long pipelineId) {

        Pipeline p = pipelineMapper.selectById(pipelineId);
        if (p == null) {
            return error("PIPELINE_NOT_FOUND", "编排不存在: " + pipelineId);
        }
        List<PipelineTask> rows = pipelineService.getPipelineTasks(pipelineId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", p.getId());
        out.put("name", p.getName());
        out.put("description", p.getDescription());
        out.put("maxParallel", p.getMaxParallel());
        out.put("enabled", p.getEnabled());
        out.put("createdAt", p.getCreatedAt());
        out.put("updatedAt", p.getUpdatedAt());
        out.put("taskCount", rows.size());
        out.put("tasks", taskViews(rows));
        List<Map<String, Object>> problems = validateSpecs(specsFromDb(rows));
        out.put("problems", problems);
        out.put("recentRuns", recentRuns(pipelineId, 5));
        out.put("next", problems.isEmpty() ? "run_pipeline" : "先按 problems 改：update_pipeline 提交完整 YAML，或在界面编辑");
        return out;
    }

    @McpTool(name = "create_pipeline", description = "用 YAML 文本新建编排，与界面「导入 YAML」同一条路径。"
            + "顶层键只认 name、description、maxParallel、servers、tasks；任务键只认 name、script、scriptId、"
            + "dependsOn、timeout（单位秒）、stepServerMapping、sharedParams、stepParams —— 全是驼峰，"
            + "max_parallel 这类下划线写法直接被拒。主机只在 stepServerMapping 里按 {步骤名: [服务器名或id]} 指定，"
            + "任务级没有 servers 键；步骤名必须与脚本包 autotest.yaml 里的步骤名一致，否则该步骤执行时没有主机。"
            + "dependsOn 填同编排内的任务名。提交时先校验：任务重名、依赖指向不存在的任务或成环、脚本查不到、"
            + "任务没有可用主机都会返回 PIPELINE_INVALID 与逐条 errors，不会留下跑不起来的编排。"
            + "servers 段按名称创建或覆盖服务器（含凭据），默认拒绝，确需覆盖才传 allowExistingServerUpdates=true。",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false,
                    idempotentHint = false, title = "新建任务编排"))
    public Map<String, Object> createPipeline(
            @McpToolParam(description = "编排 YAML 文本") String yaml,
            @McpToolParam(required = false, description = "允许覆盖平台上同名服务器（默认 false）")
            Boolean allowExistingServerUpdates) {

        Map<String, Object> bad = checkYamlShape(yaml);
        if (bad != null) {
            return bad;
        }
        PipelineYamlConfig cfg = readConfig(yaml);
        if (cfg == null) {
            return error("YAML_INVALID", "YAML 读不出内容，检查缩进与顶层是否为对象");
        }
        Map<String, Object> refused = guardServers(cfg, allowExistingServerUpdates);
        if (refused != null) {
            return refused;
        }
        List<Map<String, Object>> problems = validateSpecs(specsFromYaml(cfg));
        if (!problems.isEmpty()) {
            return invalid(problems);
        }

        Pipeline created;
        try {
            created = pipelineImportService.importFromYaml(yaml);
        } catch (Exception e) {
            return error("IMPORT_FAILED", "导入失败: " + e.getMessage());
        }
        Map<String, Object> out = getPipeline(created.getId());
        out.put("ok", true);
        out.put("created", true);
        return out;
    }

    @McpTool(name = "update_pipeline", description = "用 YAML 文本整体替换一条编排（改名、改并行数、改任务列表都走这里）。"
            + "语义是全量覆盖：YAML 里没写的任务会被删除，所以先 export_pipeline 拿到当前 YAML，改完再提交。"
            + "任务的 pipelineTaskId 会被重建（先删后插），历史执行记录不受影响。校验与 create_pipeline 一致。",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false,
                    idempotentHint = true, title = "更新任务编排"))
    public Map<String, Object> updatePipeline(
            @McpToolParam(description = "编排 id") Long pipelineId,
            @McpToolParam(description = "完整的编排 YAML 文本") String yaml,
            @McpToolParam(required = false, description = "允许覆盖平台上同名服务器（默认 false）")
            Boolean allowExistingServerUpdates) {

        Pipeline existing = pipelineMapper.selectById(pipelineId);
        if (existing == null) {
            return error("PIPELINE_NOT_FOUND", "编排不存在: " + pipelineId);
        }
        Map<String, Object> bad = checkYamlShape(yaml);
        if (bad != null) {
            return bad;
        }
        PipelineYamlConfig cfg = readConfig(yaml);
        if (cfg == null) {
            return error("YAML_INVALID", "YAML 读不出内容，检查缩进与顶层是否为对象");
        }
        Map<String, Object> refused = guardServers(cfg, allowExistingServerUpdates);
        if (refused != null) {
            return refused;
        }
        List<Map<String, Object>> problems = validateSpecs(specsFromYaml(cfg));
        if (!problems.isEmpty()) {
            return invalid(problems);
        }

        Pipeline updated;
        try {
            updated = pipelineImportService.updateFromYaml(pipelineId, yaml);
        } catch (Exception e) {
            return error("UPDATE_FAILED", "更新失败: " + e.getMessage());
        }
        Map<String, Object> out = getPipeline(updated.getId());
        out.put("ok", true);
        List<String> notes = new ArrayList<>();
        notes.add("pipelineTaskId 已重建，run_pipeline 的 serverMapping 键按本次返回的 id 或任务名重新取");
        if (!String.valueOf(out.get("name")).equals(String.valueOf(existing.getName()))) {
            notes.add("名称已从 " + existing.getName() + " 改为 " + out.get("name"));
        }
        out.put("notes", notes);
        return out;
    }

    @McpTool(name = "delete_pipeline", description = "删除编排，连带删掉它的任务定义和全部执行记录（run 与 run_task）。"
            + "有 pending/running 的执行时拒绝，先 cancel_pipeline_run。必须显式传 confirm=true。",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = true,
                    idempotentHint = false, title = "删除任务编排"))
    public Map<String, Object> deletePipeline(
            @McpToolParam(description = "编排 id") Long pipelineId,
            @McpToolParam(description = "确认删除，必须为 true") Boolean confirm) {

        if (!Boolean.TRUE.equals(confirm)) {
            return error("CONFIRM_REQUIRED", "删除编排需要显式 confirm=true");
        }
        Pipeline existing = pipelineMapper.selectById(pipelineId);
        if (existing == null) {
            return error("PIPELINE_NOT_FOUND", "编排不存在: " + pipelineId);
        }
        List<PipelineRun> running = activeRunsOf(pipelineId);
        if (!running.isEmpty()) {
            List<Long> ids = new ArrayList<>();
            for (PipelineRun r : running) {
                ids.add(r.getId());
            }
            return error("PIPELINE_RUNNING", "编排还有未结束的执行: " + ids + "；先 cancel_pipeline_run 再删");
        }

        List<PipelineRun> all = pipelineRunMapper.selectList(new LambdaQueryWrapper<PipelineRun>()
                .eq(PipelineRun::getPipelineId, pipelineId));
        Long tasks = pipelineTaskMapper.selectCount(new LambdaQueryWrapper<PipelineTask>()
                .eq(PipelineTask::getPipelineId, pipelineId));
        pipelineService.deletePipeline(pipelineId);
        return result("ok", true, "deletedPipelineId", pipelineId, "name", existing.getName(),
                "deletedTaskCount", tasks, "deletedRunCount", all.size());
    }

    @McpTool(name = "run_pipeline", description = "触发编排执行：为每个编排任务建一个真实任务，再按 dependsOn 组成的 DAG "
            + "异步调度（受 maxParallel 限制）。返回 runId 和每个子任务的 taskId，用 wait_for_pipeline_run 等待、"
            + "get_task_result / get_step_log 看细节。执行前会先做一遍体检：任务没有主机、脚本查不到、依赖成环或指向"
            + "不存在的任务、同一台主机上已有 pending/running 任务，都会被挡住并返回具体原因 —— 服务层对没主机的任务是"
            + "静默跳过、对成环是死循环，这里提前拦。编排 enabled=false 时不执行。",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false,
                    idempotentHint = false, title = "执行任务编排"))
    public Map<String, Object> runPipeline(
            @McpToolParam(description = "编排 id") Long pipelineId,
            @McpToolParam(required = false, description = "临时改每台编排任务的目标主机，键用 pipelineTaskId 或任务名，"
                    + "值用服务器 id 列表（也接受服务器名）；不传就用编排里配好的主机") Map<String, Object> serverMapping) {

        Pipeline p = pipelineMapper.selectById(pipelineId);
        if (p == null) {
            return error("PIPELINE_NOT_FOUND", "编排不存在: " + pipelineId);
        }
        if (Boolean.FALSE.equals(p.getEnabled())) {
            return error("PIPELINE_DISABLED", "编排已禁用，不会执行: " + p.getName()
                    + "；需要执行先在界面启用，或用 update_pipeline 重新导入（导入会置为启用）");
        }
        List<PipelineTask> rows = pipelineService.getPipelineTasks(pipelineId);
        if (rows.isEmpty()) {
            return error("NO_TASKS", "编排里没有任何任务，先 update_pipeline 补上");
        }

        List<PipelineRun> running = activeRunsOf(pipelineId);
        if (!running.isEmpty()) {
            List<Long> ids = new ArrayList<>();
            for (PipelineRun r : running) {
                ids.add(r.getId());
            }
            return error("ALREADY_RUNNING", "这条编排已经有未结束的执行: " + ids
                    + "；先 wait_for_pipeline_run 等完，或 cancel_pipeline_run 取消");
        }

        List<Spec> specs = specsFromDb(rows);
        List<Map<String, Object>> problems = new ArrayList<>();
        Map<Long, List<Long>> overrides = resolveOverrides(specs, serverMapping, problems);
        problems.addAll(validateSpecs(specs));
        if (!problems.isEmpty()) {
            return invalid(problems);
        }

        List<Long> servers = new ArrayList<>();
        for (Spec s : specs) {
            for (Long id : s.serverIds) {
                if (!servers.contains(id)) {
                    servers.add(id);
                }
            }
        }
        List<Long> busy = findTasksUsingServers(servers);
        if (!busy.isEmpty()) {
            return error("BUSY", "这些服务器上已有 pending/running 任务，SQLite 连接池与 SSH 池不足以并行: " + busy);
        }

        List<String> warnings = new ArrayList<>();
        for (Spec s : specs) {
            if (Boolean.FALSE.equals(s.enabled)) {
                warnings.add("任务 " + s.name + " 在库里 enabled=false，但服务层不看这个标记，照样会执行");
            }
        }

        PipelineRun run;
        try {
            run = pipelineService.executePipeline(pipelineId, overrides);
        } catch (Exception e) {
            return error("INTERNAL", "触发执行失败: " + e.getMessage());
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("runId", run.getId());
        out.put("pipelineId", pipelineId);
        out.put("pipelineName", run.getPipelineName());
        out.put("status", run.getStatus());
        out.put("maxParallel", p.getMaxParallel());
        out.put("tasks", childViews(run.getId()));
        out.put("createdTaskCount", pipelineRunTaskMapper.selectCount(new LambdaQueryWrapper<PipelineRunTask>()
                .eq(PipelineRunTask::getPipelineRunId, run.getId())));
        if (overrides != null) {
            out.put("serverOverrides", overrides);
        }
        if (!warnings.isEmpty()) {
            out.put("warnings", warnings);
        }
        out.put("next", "wait_for_pipeline_run");
        return out;
    }

    @McpTool(name = "get_pipeline_run", description = "看一次编排执行：run 状态、起止时间，以及每个子任务的 taskId 与"
            + "实时状态（pipeline_run_tasks.status 是建行时的快照，不会跟着变，这里读的是任务表）。"
            + "run 的终态只有 completed / failed / cancelled；failed 不代表所有子任务都失败，按 tasks 逐个看。",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, title = "查看编排执行"))
    public Map<String, Object> getPipelineRun(
            @McpToolParam(description = "执行记录 id") Long runId) {

        PipelineRun run = pipelineRunMapper.selectById(runId);
        if (run == null) {
            return error("RUN_NOT_FOUND", "编排执行记录不存在: " + runId);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("runId", run.getId());
        out.put("pipelineId", run.getPipelineId());
        out.put("pipelineName", run.getPipelineName());
        out.put("status", run.getStatus());
        out.put("startedAt", run.getStartedAt());
        out.put("finishedAt", run.getFinishedAt());
        out.put("triggeredBy", run.getTriggeredBy());
        List<Map<String, Object>> tasks = childViews(runId);
        out.put("tasks", tasks);
        out.put("taskSummary", countBy(tasks, "status"));
        boolean allDone = !tasks.isEmpty() && tasks.stream()
                .allMatch(t -> TERMINAL_TASK.contains(String.valueOf(t.get("status"))));
        out.put("allTasksTerminal", allDone);
        if (allDone && ACTIVE_RUN.contains(nz(run.getStatus()))) {
            out.put("note", "所有子任务都已结束但 run 仍是 " + run.getStatus()
                    + "，说明调度线程异常退出过；以子任务状态为准");
        }
        out.put("next", TERMINAL_RUN.contains(nz(run.getStatus())) ? "get_task_result" : "wait_for_pipeline_run");
        return out;
    }

    @McpTool(name = "list_pipeline_runs", description = "列出编排执行记录，可按编排过滤；返回 runId、编排名、状态、起止时间。",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, title = "列出编排执行记录"))
    public Map<String, Object> listPipelineRuns(
            @McpToolParam(required = false, description = "只看这条编排的执行记录；不传则看全部") Long pipelineId,
            @McpToolParam(required = false, description = "最多返回多少条，默认 20") Integer limit) {

        int max = limit == null || limit <= 0 ? 20 : Math.min(limit, 200);
        LambdaQueryWrapper<PipelineRun> wrapper = new LambdaQueryWrapper<PipelineRun>()
                .orderByDesc(PipelineRun::getId);
        if (pipelineId != null) {
            wrapper.eq(PipelineRun::getPipelineId, pipelineId);
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (PipelineRun r : pipelineRunMapper.selectList(wrapper)) {
            items.add(runBrief(r));
            if (items.size() >= max) {
                break;
            }
        }
        return result("runs", items, "count", items.size(), "next", "get_pipeline_run");
    }

    @McpTool(name = "wait_for_pipeline_run", description = "轮询等待编排执行进入终态（completed / failed / cancelled），"
            + "最长阻塞 maxWaitSeconds（默认 30，上限 110）。没到终态就返回当前各子任务状态，继续调用即可。"
            + "编排通常比单个任务久得多，别把预算估小。",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, title = "等待编排执行结束"))
    public Map<String, Object> waitForPipelineRun(
            @McpToolParam(description = "执行记录 id") Long runId,
            @McpToolParam(required = false, description = "最多等待秒数，默认 30，上限 110") Integer maxWaitSeconds) {

        PipelineRun run = pipelineRunMapper.selectById(runId);
        if (run == null) {
            return error("RUN_NOT_FOUND", "编排执行记录不存在: " + runId);
        }
        int budget = maxWaitSeconds == null || maxWaitSeconds <= 0 ? 30 : Math.min(maxWaitSeconds, 110);
        long deadline = System.currentTimeMillis() + budget * 1000L;
        while (!TERMINAL_RUN.contains(nz(run.getStatus())) && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            run = pipelineRunMapper.selectById(runId);
            if (run == null) {
                return error("RUN_NOT_FOUND", "执行记录在等待期间消失: " + runId + "（编排被删了？）");
            }
        }
        Map<String, Object> out = getPipelineRun(runId);
        out.put("finished", TERMINAL_RUN.contains(nz(run.getStatus())));
        out.put("waitedSeconds", budget - (int) Math.max(0, (deadline - System.currentTimeMillis()) / 1000));
        out.put("next", Boolean.TRUE.equals(out.get("finished")) ? "get_task_result" : "wait_for_pipeline_run");
        return out;
    }

    @McpTool(name = "cancel_pipeline_run", description = "取消一次编排执行：把 run 置为 cancelled，并取消当前已建出来的子任务。"
            + "调度线程下一轮会读到 cancelled 并停止发放剩余任务（已下发到远端的进程需要各自终止，可能要等几十秒）。"
            + "必须显式传 confirm=true。",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = true,
                    idempotentHint = false, title = "取消编排执行"))
    public Map<String, Object> cancelPipelineRun(
            @McpToolParam(description = "执行记录 id") Long runId,
            @McpToolParam(description = "确认取消，必须为 true") Boolean confirm) {

        if (!Boolean.TRUE.equals(confirm)) {
            return error("CONFIRM_REQUIRED", "取消编排执行需要显式 confirm=true");
        }
        PipelineRun run = pipelineRunMapper.selectById(runId);
        if (run == null) {
            return error("RUN_NOT_FOUND", "编排执行记录不存在: " + runId);
        }
        if (TERMINAL_RUN.contains(nz(run.getStatus()))) {
            return error("ALREADY_TERMINAL", "执行已经结束，无需取消: " + run.getStatus());
        }
        try {
            pipelineService.cancelPipelineRun(runId);
        } catch (Exception e) {
            return error("INTERNAL", "取消失败: " + e.getMessage());
        }
        Map<String, Object> out = getPipelineRun(runId);
        out.put("ok", true);
        return out;
    }

    @McpTool(name = "export_pipeline", description = "把一条编排导出成 YAML 文本，就是 create_pipeline / update_pipeline "
            + "接受的格式。想新建或改动编排，先导出一条现成的改起来最稳：键名、层级、步骤名都对得上。"
            + "注意导出会省略 maxParallel=5 的默认值和只有 default 的步骤映射。",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, title = "导出编排 YAML"))
    public Map<String, Object> exportPipeline(
            @McpToolParam(description = "编排 id") Long pipelineId) {

        Pipeline p = pipelineMapper.selectById(pipelineId);
        if (p == null) {
            return error("PIPELINE_NOT_FOUND", "编排不存在: " + pipelineId);
        }
        String yaml;
        try {
            yaml = pipelineImportService.exportToYaml(pipelineId);
        } catch (Exception e) {
            return error("EXPORT_FAILED", "导出失败: " + e.getMessage());
        }
        return result("ok", true, "pipelineId", pipelineId, "name", p.getName(), "yaml", yaml,
                "topLevelKeys", YAML_TOP, "taskKeys", YAML_TASK,
                "next", "改完用 update_pipeline 提交；新建用 create_pipeline");
    }

    // ==================== 编排规范自述 ====================

    @McpTool(name = "describe_pipeline_spec", description = "返回编排 YAML 的字段契约、硬性规则、一份带注释的最小示例和常见错法。"
            + "第一次建编排、或手上没有可抄的现成编排时先读它：create_pipeline / update_pipeline 就按这份契约校验，"
            + "不合格返回 PIPELINE_INVALID 与逐条 errors。库里已有相近的编排时，export_pipeline 抄一份改更省事。"
            + "只需要一部分时传 section=schema|rules|example|mistakes。",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, title = "编排规范"))
    public Map<String, Object> describePipelineSpec(
            @McpToolParam(required = false, description = "只返回某一部分：schema / rules / example / mistakes，缺省返回全部")
            String section) {

        String wanted = section == null || section.isBlank() ? "all" : section.trim().toLowerCase(Locale.ROOT);
        List<String> allowedSections = List.of("all", "schema", "rules", "example", "mistakes");
        if (!allowedSections.contains(wanted)) {
            return error("PARAM_INVALID", "section 只能是 " + allowedSections + "，收到: " + section);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("format", "编排 YAML：create_pipeline / update_pipeline 的 yaml 参数接受的文本，"
                + "与界面「导入 YAML」是同一份格式");
        if (matches(wanted, "schema")) {
            out.put("topLevelKeys", TOP_DOCS);
            out.put("taskKeys", TASK_DOCS);
            out.put("serverKeys", SERVER_DOCS);
        }
        if (matches(wanted, "rules")) {
            out.put("rules", AUTHOR_RULES);
        }
        if (matches(wanted, "example")) {
            out.put("exampleYaml", EXAMPLE_YAML);
        }
        if (matches(wanted, "mistakes")) {
            out.put("commonMistakes", List.of(
                    mistake("max_parallel / step_params 这类下划线键名",
                            "UNKNOWN_KEY，未提交",
                            "全驼峰；报错里会给候选拼写，照着改就行"),
                    mistake("给任务写 servers 键（如 servers: [1,2,3]）",
                            "UNKNOWN_KEY；这个键只存在于顶层的 servers 段",
                            "主机写进 stepServerMapping：{步骤名: [服务器名或 id]}"),
                    mistake("步骤名凭感觉写（如 run、test）",
                            "校验放行、编排建成功，但那个步骤执行时拿不到主机",
                            "先 get_script_contract 拿 steps 的键名，原样抄进 stepServerMapping"),
                    mistake("timeout 按毫秒写（如 300000）",
                            "入库会再乘 1000，变成 30 万秒 ≈ 3.5 天，等于没有超时保护",
                            "YAML 里写秒；要核对就读 get_pipeline 返回的 timeoutMs"),
                    mistake("指望 YAML 里的任务顺序控制先后",
                            "两条任务同时开跑",
                            "顺序只由 dependsOn 决定；要串行就 dependsOn: [前一条任务名]"),
                    mistake("servers 段列出自管主机（含凭据）",
                            "SERVER_EXISTS_WOULD_OVERWRITE，未提交",
                            "引用已有主机不需要 servers 段；只在要把新主机落库时写它，覆盖同名主机才传 allowExistingServerUpdates=true"),
                    mistake("authType: ssh_key",
                            "AUTH_TYPE_INVALID；SSH 层的两个分支只匹配 password 和 key，写别的值既不设密码也不加载私钥",
                            "用 password 或 key"),
                    mistake("用 update_pipeline 只提交想改的那一个任务",
                            "其余任务被删除",
                            "全量覆盖：先 export_pipeline 拿完整 YAML，改完整份提交"),
                    mistake("dependsOn 写了一个拼错的任务名",
                            "DEPENDS_ON_UNKNOWN，未提交（服务层原本会直接丢掉这条依赖，顺序静默失效）",
                            "名字必须与同编排内某条任务的 name 完全一致"),
                    mistake("上一轮还在跑就再次触发",
                            "ALREADY_RUNNING（不是 PIPELINE_INVALID，别去改 YAML）",
                            "先 wait_for_pipeline_run 等完，或 cancel_pipeline_run 取消")));
        }
        out.put("howToAuthor", List.of(
                "1. list_scripts 选脚本包（库里没有合适的包，先 describe_package_spec 再 create_script）",
                "2. get_script_contract(scriptId) 拿步骤名、参数名、required 与 options —— stepServerMapping 的键和"
                        + " sharedParams/stepParams 的键都从这里来，别猜",
                "3. list_servers 拿可用主机名或 id（没有就先 create_server）",
                "4. 照 exampleYaml 写，把 <...> 换成第 2、3 步拿到的真实值",
                "5. create_pipeline(yaml)：成功会直接返回整条编排的视图，problems 为空就可以 run_pipeline"));
        out.put("howToVerify", "create_pipeline / update_pipeline 的返回体就是 get_pipeline 的视图，看 problems 是否为空；"
                + "提交后 run_pipeline -> wait_for_pipeline_run -> get_pipeline_run 拿子任务 taskId，"
                + "再用 get_task_result / get_step_log 看单任务细节");
        out.put("codesNote", "错误码的改法直接写在每条 error 的 message 里，不需要另查对照表");
        return out;
    }

    // ==================== YAML 预检 ====================

    /** 只查键名：写错键不会报错也不会生效，所以在这里就给出正确拼写 */
    private Map<String, Object> checkYamlShape(String yaml) {
        if (yaml == null || yaml.isBlank()) {
            return error("YAML_INVALID", "yaml 是空的");
        }
        JsonNode root;
        try {
            root = YAML_READER.readTree(yaml);
        } catch (Exception e) {
            return error("YAML_INVALID", "YAML 语法错误: " + firstLine(e.getMessage()));
        }
        if (root == null || !root.isObject()) {
            return error("YAML_INVALID", "YAML 顶层必须是对象（name / tasks 那一层）");
        }
        List<Map<String, Object>> problems = new ArrayList<>();
        checkKeys(problems, "", root, YAML_TOP);
        JsonNode tasks = root.get("tasks");
        if (tasks != null) {
            if (!tasks.isArray() || tasks.isEmpty()) {
                problems.add(problem("TASKS_INVALID", "tasks", "tasks 必须是非空数组"));
            } else {
                int i = 0;
                for (JsonNode t : tasks) {
                    if (!t.isObject()) {
                        problems.add(problem("TASK_INVALID", "tasks[" + i + "]", "每个任务必须是对象"));
                    } else {
                        checkKeys(problems, "tasks[" + i + "].", t, YAML_TASK);
                        JsonNode mapping = t.get("stepServerMapping");
                        if (mapping != null && !mapping.isNull() && !mapping.isObject()) {
                            problems.add(problem("STEP_SERVER_MAPPING_INVALID", "tasks[" + i + "].stepServerMapping",
                                    "格式是 {步骤名: [服务器名或id]}"));
                        }
                    }
                    i++;
                }
            }
        }
        JsonNode servers = root.get("servers");
        if (servers != null) {
            if (!servers.isArray()) {
                problems.add(problem("SERVERS_INVALID", "servers", "servers 必须是数组"));
            } else {
                int i = 0;
                for (JsonNode s : servers) {
                    if (s.isObject()) {
                        checkKeys(problems, "servers[" + i + "].", s, YAML_SERVER);
                    } else {
                        problems.add(problem("SERVER_INVALID", "servers[" + i + "]", "每台服务器必须是对象"));
                    }
                    i++;
                }
            }
        }
        // 键名对了还可能是值类型不对（timeout 写成字符串、dependsOn 写成标量等），这里也一并挡住
        PipelineYamlConfig cfg = null;
        try {
            cfg = YAML_READER.treeToValue(root, PipelineYamlConfig.class);
        } catch (Exception e) {
            problems.add(problem("YAML_TYPE_INVALID", null, "字段类型不对: " + firstLine(e.getMessage())));
        }
        if (cfg != null) {
            if (cfg.getName() == null || cfg.getName().isBlank()) {
                problems.add(problem("NAME_MISSING", "name", "编排名称不能为空"));
            }
            if (cfg.getMaxParallel() != null && cfg.getMaxParallel() <= 0) {
                problems.add(problem("MAX_PARALLEL_INVALID", "maxParallel", "maxParallel 要大于 0"));
            }
            checkServerSection(cfg, problems);
        }
        return problems.isEmpty() ? null : invalid(problems);
    }

    /** servers 段的必填项与认证类型；服务层只查空值，不查 authType，写 ssh_key 会一路带到 SSH 层才炸 */
    private void checkServerSection(PipelineYamlConfig cfg, List<Map<String, Object>> problems) {
        if (cfg.getServers() == null) {
            return;
        }
        int i = 0;
        for (ServerYamlConfig s : cfg.getServers()) {
            String at = "servers[" + i + "]";
            if (s.getName() == null || s.getName().isBlank()) {
                problems.add(problem("SERVER_NAME_MISSING", at, "服务器名称不能为空"));
            }
            if (s.getHost() == null || s.getHost().isBlank()) {
                problems.add(problem("SERVER_HOST_MISSING", at + "." + s.getName(), "host 不能为空"));
            }
            if (s.getUsername() == null || s.getUsername().isBlank()) {
                problems.add(problem("SERVER_USERNAME_MISSING", at + "." + s.getName(), "username 不能为空"));
            }
            if (s.getAuthSecret() == null || s.getAuthSecret().isBlank()) {
                problems.add(problem("SERVER_SECRET_MISSING", at + "." + s.getName(),
                        "authSecret 不能为空（密码或私钥文本）；只想引用已有主机就别写 servers 段"));
            }
            String auth = s.getAuthType() == null ? "password" : s.getAuthType().trim().toLowerCase();
            if (!"password".equals(auth) && !"key".equals(auth)) {
                problems.add(problem("AUTH_TYPE_INVALID", at + "." + s.getName(),
                        "authType 只能是 password 或 key，收到: " + s.getAuthType()
                                + "；ssh_key 这类写法两个分支都不匹配（既不设密码也不加载私钥），"
                                + "会一路带到连接失败才暴露，所以这里直接拒"));
            }
            i++;
        }
    }

    private void checkKeys(List<Map<String, Object>> problems, String path, JsonNode obj, Set<String> allowed) {
        Iterator<String> it = obj.fieldNames();
        while (it.hasNext()) {
            String key = it.next();
            if (!allowed.contains(key)) {
                String near = PackageSchema.nearest(key, allowed);
                problems.add(problem("UNKNOWN_KEY", path + key, "不认识这个键"
                        + (near == null ? "；这里只用这些键: " + allowed : "；是不是想写 " + near + "？")));
            }
        }
    }

    private PipelineYamlConfig readConfig(String yaml) {
        try {
            return YAML_READER.readValue(yaml, PipelineYamlConfig.class);
        } catch (Exception e) {
            log.warn("编排 YAML 反序列化失败: {}", e.getMessage());
            return null;
        }
    }

    /** servers 段会按名字创建或覆盖服务器（含凭据），默认不允许动已有主机 */
    private Map<String, Object> guardServers(PipelineYamlConfig cfg, Boolean allowExistingServerUpdates) {
        if (Boolean.TRUE.equals(allowExistingServerUpdates) || cfg.getServers() == null) {
            return null;
        }
        List<String> clash = new ArrayList<>();
        for (ServerYamlConfig s : cfg.getServers()) {
            if (s.getName() == null || s.getName().isBlank()) {
                continue;
            }
            Long hits = serverMapper.selectCount(new LambdaQueryWrapper<Server>().eq(Server::getName, s.getName()));
            if (hits != null && hits > 0) {
                clash.add(s.getName());
            }
        }
        if (clash.isEmpty()) {
            return null;
        }
        return error("SERVER_EXISTS_WOULD_OVERWRITE", "servers 段里的 " + clash
                + " 在平台上已经有同名主机，导入会连凭据一起覆盖。引用已有主机不需要写 servers 段，"
                + "直接在 stepServerMapping 里写名称或 id 即可；确实要覆盖才传 allowExistingServerUpdates=true");
    }

    // ==================== 校验 ====================

    /** 编排任务的可校验视图：YAML 和库两个来源都归一到它，校验只写一遍 */
    private static final class Spec {
        Long pipelineTaskId;
        String name;
        Long scriptId;
        String scriptRef;
        Boolean enabled;
        final List<Long> serverIds = new ArrayList<>();
        final List<String> missingServers = new ArrayList<>();
        final List<String> dependsOn = new ArrayList<>();
        /** YAML 的 servers 段里声明了、但库里还没有的主机名 —— 导入时会被创建，不算错误 */
        boolean pendingDeclaredServer;
        Long timeoutMs;
    }

    private List<Spec> specsFromYaml(PipelineYamlConfig cfg) {
        List<Spec> specs = new ArrayList<>();
        if (cfg.getTasks() == null) {
            return specs;
        }
        Set<String> declared = new LinkedHashSet<>();
        if (cfg.getServers() != null) {
            for (ServerYamlConfig s : cfg.getServers()) {
                if (s.getName() != null && !s.getName().isBlank()) {
                    declared.add(s.getName());
                }
            }
        }
        for (TaskYamlConfig t : cfg.getTasks()) {
            Spec s = new Spec();
            s.name = t.getName();
            s.scriptRef = t.getScript();
            s.scriptId = t.getScriptId();
            if (s.scriptId == null && t.getScript() != null && !t.getScript().isBlank()) {
                Script sc = scriptMapper.selectOne(new LambdaQueryWrapper<Script>()
                        .eq(Script::getName, t.getScript()));
                s.scriptId = sc == null ? null : sc.getId();
            }
            if (t.getTimeout() != null) {
                s.timeoutMs = t.getTimeout().longValue() * 1000L;
            }
            if (t.getDependsOn() != null) {
                for (String d : t.getDependsOn()) {
                    s.dependsOn.add(d);
                }
            }
            if (t.getStepServerMapping() != null) {
                Set<Long> seen = new LinkedHashSet<>();
                Set<String> seenNames = new LinkedHashSet<>();
                for (List<Object> refs : t.getStepServerMapping().values()) {
                    if (refs == null) {
                        continue;
                    }
                    for (Object ref : refs) {
                        Long id = toServerId(ref);
                        if (id == null) {
                            String text = String.valueOf(ref);
                            if (declared.contains(text.trim())) {
                                s.pendingDeclaredServer = true;
                            } else if (!seenNames.contains(text)) {
                                seenNames.add(text);
                                s.missingServers.add(text);
                            }
                        } else if (seen.add(id)) {
                            s.serverIds.add(id);
                        }
                    }
                }
            }
            specs.add(s);
        }
        return specs;
    }

    private List<Spec> specsFromDb(List<PipelineTask> rows) {
        List<Spec> specs = new ArrayList<>();
        for (PipelineTask pt : rows) {
            Spec s = new Spec();
            s.pipelineTaskId = pt.getId();
            s.name = pt.getName();
            s.scriptId = pt.getScriptId();
            s.enabled = pt.getEnabled();
            s.timeoutMs = pt.getTimeout();
            s.serverIds.addAll(uniqueLongs(parseLongList(pt.getServerIds())));
            if (s.serverIds.isEmpty()) {
                for (List<Object> refs : parseStepMapping(pt.getStepServerMapping()).values()) {
                    for (Object ref : refs) {
                        Long id = toServerId(ref);
                        if (id != null && !s.serverIds.contains(id)) {
                            s.serverIds.add(id);
                        }
                    }
                }
            }
            s.dependsOn.addAll(parseStringList(pt.getDependsOn()));
            specs.add(s);
        }
        return specs;
    }

    private List<Map<String, Object>> validateSpecs(List<Spec> specs) {
        List<Map<String, Object>> problems = new ArrayList<>();
        if (specs.isEmpty()) {
            problems.add(problem("TASKS_INVALID", null, "tasks 不能为空"));
            return problems;
        }
        Set<String> names = new LinkedHashSet<>();
        for (Spec s : specs) {
            String who = s.name == null || s.name.isBlank() ? null : s.name;
            if (who == null) {
                problems.add(problem("TASK_NAME_MISSING", null, "任务名称不能为空"));
            } else if (!names.add(who)) {
                problems.add(problem("TASK_NAME_DUPLICATE", who, "任务名称重复：" + who
                        + "；依赖按名称解析，重名会让调度直接抛异常"));
            }

            if (s.scriptId == null) {
                problems.add(problem(s.scriptRef == null ? "SCRIPT_MISSING" : "SCRIPT_NOT_FOUND", who,
                        s.scriptRef == null
                                ? "没写脚本：用 script 填平台上已有的脚本包名，或 scriptId 填数字 id"
                                : "脚本不存在: script: " + s.scriptRef + "；名称要与脚本包完全一致（区分大小写），用 list_scripts 查"));
            } else if (scriptMapper.selectById(s.scriptId) == null) {
                problems.add(problem("SCRIPT_NOT_FOUND", who, "脚本不存在: scriptId=" + s.scriptId));
            }

            if (s.timeoutMs != null && s.timeoutMs <= 0) {
                problems.add(problem("TIMEOUT_INVALID", who, "timeout 要大于 0（YAML 里单位是秒）"));
            }

            if (s.serverIds.isEmpty() && !s.pendingDeclaredServer) {
                problems.add(problem("NO_SERVERS", who, "没有可用主机：stepServerMapping 至少要给一个步骤指定存在的服务器名或 id；"
                        + "缺主机的任务执行时会被静默跳过，依赖它的任务会一起卡住"));
            }
            for (String miss : s.missingServers) {
                problems.add(problem("SERVER_NOT_FOUND", who, "服务器不存在: " + miss
                        + "；用 list_servers 查名称，或直接写服务器 id"));
            }
            for (Long id : s.serverIds) {
                if (id == LOCAL_SERVER_ID) {
                    continue;
                }
                if (serverMapper.selectById(id) == null) {
                    problems.add(problem("SERVER_NOT_FOUND", who, "服务器不存在: id=" + id));
                }
            }

            for (String dep : s.dependsOn) {
                if (dep == null || dep.isBlank()) {
                    problems.add(problem("DEPENDS_ON_INVALID", who, "dependsOn 里有空任务名"));
                } else if (dep.equals(s.name)) {
                    problems.add(problem("DEPENDS_ON_SELF", who, "依赖自己，调度会永远等下去"));
                }
            }
        }
        for (Spec s : specs) {
            for (String dep : s.dependsOn) {
                if (dep != null && !dep.isBlank() && !names.contains(dep)) {
                    problems.add(problem("DEPENDS_ON_UNKNOWN", s.name, "dependsOn 指向不存在的任务: " + dep
                            + "；执行时这个依赖会被直接丢掉，顺序不生效"));
                }
            }
        }
        detectCycles(specs, names, problems);
        return problems;
    }

    private void detectCycles(List<Spec> specs, Set<String> known, List<Map<String, Object>> problems) {
        Map<String, List<String>> graph = new LinkedHashMap<>();
        for (Spec s : specs) {
            if (s.name == null || graph.containsKey(s.name)) {
                continue;
            }
            List<String> deps = new ArrayList<>();
            for (String d : s.dependsOn) {
                if (d != null && known.contains(d)) {
                    deps.add(d);
                }
            }
            graph.put(s.name, deps);
        }
        Map<String, Integer> state = new HashMap<>();
        for (String node : graph.keySet()) {
            walk(node, graph, state, new ArrayList<>(), problems);
        }
    }

    private void walk(String node, Map<String, List<String>> graph, Map<String, Integer> state,
                      List<String> path, List<Map<String, Object>> problems) {
        Integer st = state.get(node);
        if (st != null && st == 2) {
            return;
        }
        if (st != null && st == 1) {
            int from = Math.max(0, path.indexOf(node));
            List<String> cycle = new ArrayList<>(path.subList(from, path.size()));
            cycle.add(node);
            problems.add(problem("DEPENDS_ON_CYCLE", node,
                    "依赖成环，调度永远结束不了: " + String.join(" -> ", cycle)));
            return;
        }
        state.put(node, 1);
        path.add(node);
        for (String dep : graph.getOrDefault(node, Collections.emptyList())) {
            walk(dep, graph, state, path, problems);
        }
        path.remove(path.size() - 1);
        state.put(node, 2);
    }

    // ==================== 主机临时覆盖 ====================

    private Map<Long, List<Long>> resolveOverrides(List<Spec> specs, Map<String, Object> serverMapping,
                                                   List<Map<String, Object>> problems) {
        if (serverMapping == null || serverMapping.isEmpty()) {
            return null;
        }
        Map<Long, List<Long>> resolved = new LinkedHashMap<>();
        Map<String, Spec> byName = new LinkedHashMap<>();
        Map<Long, Spec> byId = new LinkedHashMap<>();
        for (Spec s : specs) {
            if (s.name != null) {
                byName.putIfAbsent(s.name, s);
            }
            if (s.pipelineTaskId != null) {
                byId.put(s.pipelineTaskId, s);
            }
        }
        for (Map.Entry<String, Object> entry : serverMapping.entrySet()) {
            String key = entry.getKey() == null ? "" : entry.getKey().trim();
            Spec target = null;
            try {
                target = byId.get(Long.parseLong(key));
            } catch (NumberFormatException ignore) {
                // 不是数字就按任务名找
            }
            if (target == null) {
                target = byName.get(key);
            }
            if (target == null) {
                problems.add(problem("PIPELINE_TASK_NOT_FOUND", key,
                        "serverMapping 的键既不是这条编排的 pipelineTaskId，也不是任务名；用 get_pipeline 核对"));
                continue;
            }
            List<Long> ids = new ArrayList<>();
            for (String raw : flattenRefs(entry.getValue())) {
                Long id = numericId(raw);
                if (id == null) {
                    Server server = serverMapper.selectOne(new LambdaQueryWrapper<Server>().eq(Server::getName, raw));
                    if (server == null) {
                        problems.add(problem("SERVER_NOT_FOUND", target.name, "serverMapping 里的服务器不存在: " + raw));
                        continue;
                    }
                    id = server.getId();
                }
                if (!ids.contains(id)) {
                    ids.add(id);
                }
            }
            if (ids.isEmpty()) {
                problems.add(problem("NO_SERVERS", target.name, "serverMapping 给任务 " + target.name
                        + " 覆盖成了空列表；编排里也没配主机，那就没法定位执行机器"));
                continue;
            }
            target.serverIds.clear();
            target.serverIds.addAll(ids);
            if (target.pipelineTaskId != null) {
                resolved.put(target.pipelineTaskId, ids);
            }
        }
        return resolved;
    }

    private List<String> flattenRefs(Object value) {
        List<String> refs = new ArrayList<>();
        if (value == null) {
            return refs;
        }
        if (value instanceof List<?> list) {
            for (Object o : list) {
                refs.addAll(flattenRefs(o));
            }
        } else if (value instanceof Number number) {
            refs.add(String.valueOf(number.longValue()));
        } else {
            for (String part : String.valueOf(value).split(",")) {
                if (!part.isBlank()) {
                    refs.add(part.trim());
                }
            }
        }
        return refs;
    }

    private Long numericId(String raw) {
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** stepServerMapping 里的引用：数字当 id 用，名称查库 */
    private Long toServerId(Object ref) {
        if (ref == null) {
            return null;
        }
        if (ref instanceof Number) {
            return ((Number) ref).longValue();
        }
        String text = String.valueOf(ref).trim();
        Long numeric = numericId(text);
        if (numeric != null) {
            return numeric;
        }
        Server server = serverMapper.selectOne(new LambdaQueryWrapper<Server>().eq(Server::getName, text));
        return server == null ? null : server.getId();
    }

    // ==================== 视图 ====================

    private List<Map<String, Object>> taskViews(List<PipelineTask> rows) {
        List<Map<String, Object>> views = new ArrayList<>();
        for (PipelineTask pt : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("pipelineTaskId", pt.getId());
            m.put("name", pt.getName());
            m.put("orderNum", pt.getOrderNum());
            m.put("scriptId", pt.getScriptId());
            Script sc = pt.getScriptId() == null ? null : scriptMapper.selectById(pt.getScriptId());
            m.put("scriptName", sc == null ? null : sc.getName());
            m.put("scriptVersion", sc == null ? null : sc.getCurrentVersion());
            m.put("enabled", pt.getEnabled());
            m.put("timeoutMs", pt.getTimeout());
            m.put("dependsOn", parseStringList(pt.getDependsOn()));
            List<Long> ids = uniqueLongs(parseLongList(pt.getServerIds()));
            m.put("serverIds", ids);
            if (ids.isEmpty()) {
                Set<Long> fromMapping = new LinkedHashSet<>();
                for (List<Object> refs : parseStepMapping(pt.getStepServerMapping()).values()) {
                    for (Object ref : refs) {
                        if (ref instanceof Number) {
                            fromMapping.add(((Number) ref).longValue());
                        }
                    }
                }
                m.put("serverIds", new ArrayList<>(fromMapping));
            }
            m.put("servers", serverBriefs(m.get("serverIds")));
            m.put("stepServerMapping", parseStepMapping(pt.getStepServerMapping()));
            m.put("sharedParams", parseObject(pt.getSharedParams()));
            m.put("stepParams", parseObject(pt.getStepParams()));
            views.add(m);
        }
        return views;
    }

    private List<Map<String, Object>> serverBriefs(Object serverIds) {
        List<Map<String, Object>> briefs = new ArrayList<>();
        if (!(serverIds instanceof List<?> list)) {
            return briefs;
        }
        for (Object o : list) {
            Long id = o instanceof Number ? ((Number) o).longValue() : numericId(String.valueOf(o));
            if (id == null) {
                continue;
            }
            if (id == LOCAL_SERVER_ID) {
                briefs.add(result("id", id, "name", "平台本机"));
                continue;
            }
            Server s = serverMapper.selectById(id);
            briefs.add(s == null ? result("id", id, "missing", true)
                    : result("id", id, "name", s.getName(), "host", s.getHost(), "status", s.getStatus()));
        }
        return briefs;
    }

    private List<Map<String, Object>> childViews(Long runId) {
        List<Map<String, Object>> views = new ArrayList<>();
        for (PipelineRunTask rt : pipelineRunTaskMapper.selectList(new LambdaQueryWrapper<PipelineRunTask>()
                .eq(PipelineRunTask::getPipelineRunId, runId).orderByAsc(PipelineRunTask::getId))) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("taskId", rt.getTaskId());
            m.put("taskName", rt.getTaskName());
            Task t = taskMapper.selectById(rt.getTaskId());
            if (t == null) {
                m.put("status", rt.getStatus());
                m.put("note", "任务已经不在了（可能被删除），run_task 快照状态: " + rt.getStatus());
            } else {
                m.put("status", t.getStatus());
                m.put("scriptId", t.getScriptId());
                m.put("startedAt", t.getStartedAt());
                m.put("finishedAt", t.getFinishedAt());
                m.put("timeoutMs", t.getTimeout());
            }
            views.add(m);
        }
        return views;
    }

    private Map<String, Object> lastRunBrief(Long pipelineId) {
        List<PipelineRun> runs = pipelineRunMapper.selectList(new LambdaQueryWrapper<PipelineRun>()
                .eq(PipelineRun::getPipelineId, pipelineId).orderByDesc(PipelineRun::getId).last("LIMIT 1"));
        return runs.isEmpty() ? null : runBrief(runs.get(0));
    }

    private List<Map<String, Object>> recentRuns(Long pipelineId, int limit) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (PipelineRun r : pipelineRunMapper.selectList(new LambdaQueryWrapper<PipelineRun>()
                .eq(PipelineRun::getPipelineId, pipelineId).orderByDesc(PipelineRun::getId))) {
            items.add(runBrief(r));
            if (items.size() >= limit) {
                break;
            }
        }
        return items;
    }

    private Map<String, Object> runBrief(PipelineRun r) {
        return result("runId", r.getId(), "status", r.getStatus(), "startedAt", r.getStartedAt(),
                "finishedAt", r.getFinishedAt(), "triggeredBy", r.getTriggeredBy());
    }

    private List<PipelineRun> activeRunsOf(Long pipelineId) {
        return pipelineRunMapper.selectList(new LambdaQueryWrapper<PipelineRun>()
                .eq(PipelineRun::getPipelineId, pipelineId)
                .in(PipelineRun::getStatus, List.of("pending", "running"))
                .orderByAsc(PipelineRun::getId));
    }

    private List<Long> findTasksUsingServers(List<Long> serverIds) {
        List<Long> conflicts = new ArrayList<>();
        for (Task task : taskMapper.selectList(new LambdaQueryWrapper<Task>().in(Task::getStatus, List.of("pending", "running")))) {
            for (TaskServer ts : taskServerMapper.selectList(new LambdaQueryWrapper<TaskServer>()
                    .eq(TaskServer::getTaskId, task.getId()))) {
                if (serverIds.contains(ts.getServerId()) && !conflicts.contains(task.getId())) {
                    conflicts.add(task.getId());
                    break;
                }
            }
        }
        return conflicts;
    }

    // ==================== 小工具 ====================

    private Map<String, Object> invalid(List<Map<String, Object>> problems) {
        Map<String, Object> out = error("PIPELINE_INVALID",
                "编排有 " + problems.size() + " 处问题，未提交；按 errors 逐条改完再试");
        out.put("errors", problems);
        out.put("next", "get_pipeline 或 export_pipeline");
        return out;
    }

    private Map<String, Object> problem(String code, String where, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("where", where);
        m.put("message", message);
        return m;
    }

    private Map<String, Object> parseObject(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            log.warn("编排里的 JSON 列解析失败: {}", json);
            return result("raw", json);
        }
    }

    private Map<String, List<Object>> parseStepMapping(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            Map<String, List<Object>> parsed = objectMapper.readValue(json,
                    new TypeReference<Map<String, List<Object>>>() {
                    });
            Map<String, List<Object>> clean = new LinkedHashMap<>();
            for (Map.Entry<String, List<Object>> e : parsed.entrySet()) {
                clean.put(e.getKey(), e.getValue() == null ? new ArrayList<>() : e.getValue());
            }
            return clean;
        } catch (Exception e) {
            log.warn("stepServerMapping 解析失败: {}", json);
            return Collections.emptyMap();
        }
    }

    private List<String> parseStringList(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            log.warn("dependsOn 解析失败: {}", json);
            return new ArrayList<>();
        }
    }

    private List<Long> parseLongList(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Long>>() {
            });
        } catch (Exception e) {
            log.warn("serverIds 解析失败: {}", json);
            return new ArrayList<>();
        }
    }

    private List<Long> uniqueLongs(List<Long> ids) {
        List<Long> out = new ArrayList<>();
        for (Long id : ids) {
            if (id != null && !out.contains(id)) {
                out.add(id);
            }
        }
        return out;
    }

    private Map<String, Integer> countBy(List<Map<String, Object>> rows, String key) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            counts.merge(String.valueOf(row.get(key)), 1, Integer::sum);
        }
        return counts;
    }

    /** 键名 -> 用途说明，顺序就是返回给智能体的顺序 */
    private static Map<String, String> docs(String... keyAndDoc) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyAndDoc.length; i += 2) {
            map.put(keyAndDoc[i], keyAndDoc[i + 1]);
        }
        return map;
    }

    private static Map<String, String> mistake(String wrong, String symptom, String fix) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("wrong", wrong);
        row.put("symptom", symptom);
        row.put("fix", fix);
        return row;
    }

    private static boolean matches(String wanted, String section) {
        return "all".equals(wanted) || wanted.equals(section);
    }

    private static boolean contains(String value, String kw) {
        return value != null && value.toLowerCase().contains(kw);
    }

    private static String firstLine(String message) {
        if (message == null) {
            return "未知原因";
        }
        int idx = message.indexOf('\n');
        return idx < 0 ? message : message.substring(0, idx);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
