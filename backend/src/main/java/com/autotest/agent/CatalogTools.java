package com.autotest.agent;

import com.autotest.dto.request.ServerCreateRequest;
import com.autotest.entity.Script;
import com.autotest.entity.ScriptVersion;
import com.autotest.entity.Server;
import com.autotest.entity.Task;
import com.autotest.entity.TaskServer;
import com.autotest.mapper.ScriptMapper;
import com.autotest.mapper.ScriptVersionMapper;
import com.autotest.mapper.ServerMapper;
import com.autotest.mapper.TaskMapper;
import com.autotest.mapper.TaskServerMapper;
import com.autotest.service.ServerService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 面向智能体的资产查询与服务器管理工具。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogTools {

    private final ScriptMapper scriptMapper;
    private final ScriptVersionMapper scriptVersionMapper;
    private final ServerMapper serverMapper;
    private final TaskMapper taskMapper;
    private final TaskServerMapper taskServerMapper;
    private final ServerService serverService;
    private final ObjectMapper objectMapper;

    @McpTool(name = "list_scripts", description = "列出平台上的测试脚本包，返回 id、名称、当前版本、分类、标签。"
            + "先用它选出 scriptId，再用 get_script_contract 读该包的参数契约。",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true,
                    title = "列出脚本包"))
    public Map<String, Object> listScripts(
            @McpToolParam(required = false, description = "按名称或描述模糊过滤，不区分大小写") String keyword,
            @McpToolParam(required = false, description = "按标签过滤，形如 Kylin；匹配标签的 key 或 value") String tag,
            @McpToolParam(required = false, description = "最多返回多少条，默认 50") Integer limit) {

        List<Script> scripts = scriptMapper.selectList(new LambdaQueryWrapper<Script>()
                .orderByDesc(Script::getId));
        String kw = lower(keyword);
        String tagKw = lower(tag);
        int max = limit == null || limit <= 0 ? 50 : Math.min(limit, 500);

        List<Map<String, Object>> items = new ArrayList<>();
        for (Script s : scripts) {
            if (kw != null && !matchesKw(s, kw)) {
                continue;
            }
            List<Map<String, Object>> tags = parseTags(s.getTags());
            if (tagKw != null && !matchesTag(tags, tagKw)) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", s.getId());
            item.put("name", s.getName());
            item.put("currentVersion", s.getCurrentVersion());
            item.put("scriptType", s.getScriptType());
            item.put("testCategory", s.getTestCategory());
            item.put("status", s.getStatus());
            item.put("defaultTimeout", s.getDefaultTimeout());
            item.put("tags", tags);
            items.add(item);
            if (items.size() >= max) {
                break;
            }
        }
        return result("scripts", items, "count", items.size());
    }

    @McpTool(name = "get_script_contract", description = "读取脚本包的执行契约：步骤 DAG、入口脚本、"
            + "共享参数定义（名称/类型/默认值/可选值/说明）、文件清单与版本列表。提交任务前必须先读它来填参数。",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true,
                    title = "读取脚本参数契约"))
    public Map<String, Object> getScriptContract(
            @McpToolParam(description = "脚本包 id，来自 list_scripts") Long scriptId,
            @McpToolParam(required = false, description = "版本号，默认取脚本当前版本") String version) {

        Script script = scriptMapper.selectById(scriptId);
        if (script == null) {
            return error("SCRIPT_NOT_FOUND", "脚本不存在: " + scriptId);
        }
        String useVersion = version == null || version.isEmpty() ? script.getCurrentVersion() : version;
        ScriptVersion sv = scriptVersionMapper.selectOne(new LambdaQueryWrapper<ScriptVersion>()
                .eq(ScriptVersion::getScriptId, scriptId)
                .eq(ScriptVersion::getVersion, useVersion));
        if (sv == null) {
            return error("VERSION_NOT_FOUND", "版本不存在: scriptId=" + scriptId + ", version=" + useVersion
                    + "；该脚本现有版本见 currentVersion");
        }

        List<String> files = new ArrayList<>();
        if (sv.getFileList() != null) {
            for (Map<String, Object> f : sv.getFileList()) {
                Object path = f.get("path") != null ? f.get("path") : f.get("name");
                if (path != null) {
                    files.add(String.valueOf(path));
                }
            }
        }

        Map<String, Object> contract = new LinkedHashMap<>();
        contract.put("scriptId", script.getId());
        contract.put("name", script.getName());
        contract.put("description", script.getDescription());
        contract.put("version", sv.getVersion());
        contract.put("scriptType", script.getScriptType());
        contract.put("tags", parseTags(script.getTags()));
        contract.put("defaultTimeoutMs", script.getDefaultTimeout());
        contract.put("files", files);
        contract.put("sharedParams", sv.getParameters() == null ? List.of() : sv.getParameters());
        contract.put("steps", sv.getSteps() == null ? Map.of() : sv.getSteps());

        List<Map<String, Object>> versions = new ArrayList<>();
        for (ScriptVersion v : scriptVersionMapper.selectList(new LambdaQueryWrapper<ScriptVersion>()
                .eq(ScriptVersion::getScriptId, scriptId)
                .orderByDesc(ScriptVersion::getId))) {
            versions.add(result("version", v.getVersion(),
                    "createdAt", v.getCreatedAt(),
                    "fileCount", v.getFileCount(),
                    "changeLog", v.getChangeLog()));
        }
        contract.put("versions", versions);
        return contract;
    }

    @McpTool(name = "list_servers", description = "列出可用的被测/操作主机（不含凭据），返回 id、名称、地址、"
            + "端口、认证方式、分组、标签、在线状态与启用状态。run_test 的 serverIds 取自这里；"
            + "id 为 -1 表示在平台本机执行。",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true,
                    title = "列出服务器"))
    public Map<String, Object> listServers(
            @McpToolParam(required = false, description = "按名称、地址或标签模糊过滤") String keyword,
            @McpToolParam(required = false, description = "按分组 id 过滤") Long groupId) {

        String kw = lower(keyword);
        List<Map<String, Object>> items = new ArrayList<>();
        for (Server s : serverMapper.selectList(new LambdaQueryWrapper<Server>().orderByAsc(Server::getId))) {
            if (groupId != null && !groupId.equals(s.getGroupId())) {
                continue;
            }
            if (kw != null && !matchesServer(s, kw)) {
                continue;
            }
            items.add(toServerView(s));
        }
        return result("servers", items, "count", items.size());
    }

    @McpTool(name = "get_server", description = "查看单台服务器的详细信息（CPU、内存、磁盘、最近检查时间），不含凭据。",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true,
                    title = "查看服务器"))
    public Map<String, Object> getServer(
            @McpToolParam(description = "服务器 id") Long id) {
        Server s = serverMapper.selectById(id);
        if (s == null) {
            return error("SERVER_NOT_FOUND", "服务器不存在: " + id);
        }
        Map<String, Object> view = toServerView(s);
        view.put("osType", s.getOsType());
        view.put("osVersion", s.getOsVersion());
        view.put("cpuCores", s.getCpuCores());
        view.put("cpuModel", s.getCpuModel());
        view.put("cpuArch", s.getCpuArch());
        view.put("memorySize", s.getMemorySize());
        view.put("diskInfo", s.getDiskInfo());
        view.put("remark", s.getRemark());
        view.put("lastCheckAt", s.getLastCheckAt());
        return view;
    }

    @McpTool(name = "create_server", description = "新增一台服务器。authType 只能是 password 或 key；"
            + "password 时 authSecret 是登录密码，key 时 authSecret 是私钥文本。",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false,
                    idempotentHint = false, title = "新增服务器"))
    public Map<String, Object> createServer(
            @McpToolParam(description = "服务器名称") String name,
            @McpToolParam(description = "主机地址（IP 或域名）") String host,
            @McpToolParam(description = "登录用户名") String username,
            @McpToolParam(description = "认证类型：password 或 key") String authType,
            @McpToolParam(description = "认证凭据：密码文本或私钥文本") String authSecret,
            @McpToolParam(required = false, description = "SSH 端口，默认 22") Integer port,
            @McpToolParam(required = false, description = "分组 id") Long groupId,
            @McpToolParam(required = false, description = "标签列表") List<String> tags,
            @McpToolParam(required = false, description = "备注") String remark) {

        String auth = authType == null ? null : authType.trim().toLowerCase(Locale.ROOT);
        if (!"password".equals(auth) && !"key".equals(auth)) {
            return error("PARAM_INVALID", "authType 只能是 password 或 key，收到: " + authType);
        }

        ServerCreateRequest req = new ServerCreateRequest();
        req.setName(name);
        req.setHost(host);
        req.setUsername(username);
        req.setAuthType(auth);
        req.setAuthSecret(authSecret);
        req.setPort(port == null ? 22 : port);
        req.setGroupId(groupId);
        req.setTags(tags);
        req.setRemark(remark);

        Server created = serverService.createServer(req);
        Map<String, Object> view = toServerView(created);
        view.put("ok", true);
        return view;
    }

    @McpTool(name = "update_server", description = "修改服务器。只传需要改的字段；未传的字段保持原值"
            + "（实现按“读-改-写”合并，不会把 authSecret 洗成空）。",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false,
                    idempotentHint = true, title = "修改服务器"))
    public Map<String, Object> updateServer(
            @McpToolParam(description = "服务器 id") Long id,
            @McpToolParam(required = false, description = "新的名称") String name,
            @McpToolParam(required = false, description = "新的主机地址") String host,
            @McpToolParam(required = false, description = "新的端口") Integer port,
            @McpToolParam(required = false, description = "新的用户名") String username,
            @McpToolParam(required = false, description = "新的认证类型：password 或 key") String authType,
            @McpToolParam(required = false, description = "新的凭据；不传则保持原凭据") String authSecret,
            @McpToolParam(required = false, description = "新的分组 id") Long groupId,
            @McpToolParam(required = false, description = "新的标签列表") List<String> tags,
            @McpToolParam(required = false, description = "新的备注") String remark) {

        Server existing = serverMapper.selectById(id);
        if (existing == null) {
            return error("SERVER_NOT_FOUND", "服务器不存在: " + id);
        }

        ServerCreateRequest req = new ServerCreateRequest();
        req.setName(orDefault(name, existing.getName()));
        req.setHost(orDefault(host, existing.getHost()));
        req.setPort(port == null ? existing.getPort() : port);
        req.setUsername(orDefault(username, existing.getUsername()));
        String auth = authType == null ? existing.getAuthType()
                : authType.trim().toLowerCase(Locale.ROOT);
        if (!"password".equals(auth) && !"key".equals(auth)) {
            return error("PARAM_INVALID", "authType 只能是 password 或 key，收到: " + authType);
        }
        req.setAuthType(auth);
        req.setAuthSecret(orDefault(authSecret, existing.getAuthSecret()));
        req.setGroupId(groupId == null ? existing.getGroupId() : groupId);
        req.setTags(tags == null ? existing.getTags() : tags);
        req.setRemark(orDefault(remark, existing.getRemark()));

        Server updated = serverService.updateServer(id, req);
        Map<String, Object> view = toServerView(updated);
        view.put("ok", true);
        return view;
    }

    @McpTool(name = "delete_server", description = "删除服务器。被 pending/running 任务引用时拒绝并返回具体任务 id；"
            + "必须显式传 confirm=true。",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = true,
                    idempotentHint = false, title = "删除服务器"))
    public Map<String, Object> deleteServer(
            @McpToolParam(description = "服务器 id") Long id,
            @McpToolParam(description = "确认删除，必须为 true") Boolean confirm) {

        if (!Boolean.TRUE.equals(confirm)) {
            return error("CONFIRM_REQUIRED", "删除服务器需要显式 confirm=true");
        }
        Server existing = serverMapper.selectById(id);
        if (existing == null) {
            return error("SERVER_NOT_FOUND", "服务器不存在: " + id);
        }

        List<Long> blocking = new ArrayList<>();
        for (Task task : taskMapper.selectList(new LambdaQueryWrapper<Task>()
                .in(Task::getStatus, List.of("pending", "running")))) {
            Long refs = taskServerMapper.selectCount(new LambdaQueryWrapper<TaskServer>()
                    .eq(TaskServer::getTaskId, task.getId())
                    .eq(TaskServer::getServerId, id));
            if (refs != null && refs > 0) {
                blocking.add(task.getId());
            }
        }
        if (!blocking.isEmpty()) {
            return error("SERVER_IN_USE", "服务器 " + id + " 正被这些任务引用，先撤任务再删: " + blocking);
        }

        serverService.deleteServer(id);
        return result("ok", true, "deletedServerId", id, "name", existing.getName());
    }

    @McpTool(name = "test_server_connection", description = "对服务器发起一次 SSH 连接测试（可达 + 认证），"
            + "并刷新其在线状态。注意：每次连接都会在远端留下一条认证失败计数，不要连续猛测同一台机器。",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false,
                    idempotentHint = false, title = "测试服务器连接"))
    public Map<String, Object> testServerConnection(
            @McpToolParam(description = "服务器 id") Long id) {
        boolean connected = serverService.testConnection(id);
        return result("ok", connected, "serverId", id, "connected", connected);
    }

    @McpTool(name = "refresh_server_info", description = "登录服务器采集 CPU/内存/磁盘等系统信息并回写，"
            + "用于确认机器画像是否符合脚本要求。",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false,
                    idempotentHint = true, title = "刷新服务器信息"))
    public Map<String, Object> refreshServerInfo(
            @McpToolParam(description = "服务器 id") Long id) {
        Server refreshed = serverService.refreshServerInfo(id);
        Map<String, Object> view = toServerView(refreshed);
        view.put("osType", refreshed.getOsType());
        view.put("osVersion", refreshed.getOsVersion());
        view.put("cpuCores", refreshed.getCpuCores());
        view.put("cpuArch", refreshed.getCpuArch());
        view.put("memorySize", refreshed.getMemorySize());
        view.put("ok", true);
        return view;
    }

    // ==================== 内部方法 ====================

    private Map<String, Object> toServerView(Server s) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", s.getId());
        view.put("name", s.getName());
        view.put("host", s.getHost());
        view.put("port", s.getPort());
        view.put("username", s.getUsername());
        view.put("authType", s.getAuthType());
        view.put("groupId", s.getGroupId());
        view.put("tags", s.getTags() == null ? List.of() : s.getTags());
        view.put("status", s.getStatus());
        view.put("enabled", s.getEnabled());
        return view;
    }

    private boolean matchesKw(Script s, String kw) {
        return contains(s.getName(), kw) || contains(s.getDescription(), kw)
                || contains(s.getTestCategory(), kw);
    }

    private boolean matchesTag(List<Map<String, Object>> tags, String tagKw) {
        for (Map<String, Object> t : tags) {
            if (contains(String.valueOf(t.get("key")), tagKw) || contains(String.valueOf(t.get("value")), tagKw)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesServer(Server s, String kw) {
        if (contains(s.getName(), kw) || contains(s.getHost(), kw)) {
            return true;
        }
        if (s.getTags() != null) {
            for (String t : s.getTags()) {
                if (contains(t, kw)) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<Map<String, Object>> parseTags(String json) {
        if (json == null || json.isBlank() || "[]".equals(json.trim())) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {
            });
        } catch (Exception e) {
            log.warn("解析脚本标签失败: {}", json);
            return List.of();
        }
    }

    private static String lower(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean contains(String value, String kw) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(kw);
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }

    static Map<String, Object> result(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            map.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return map;
    }

    static Map<String, Object> error(String reasonCode, String message) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("ok", false);
        map.put("reason_code", reasonCode);
        map.put("error", message);
        return map;
    }
}
