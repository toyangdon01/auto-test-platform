package com.autotest.agent;

import com.autotest.entity.ResourceFile;
import com.autotest.entity.ScriptResource;
import com.autotest.mapper.ResourceFileMapper;
import com.autotest.mapper.ScriptResourceMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static com.autotest.agent.CatalogTools.error;
import static com.autotest.agent.CatalogTools.result;

/**
 * 面向智能体的资源库查询与登记工具。
 * 脚本包靠 resourceMd5 绑资源，这个工具面就是「MD5 从哪来」的答案。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResourceTools {

    private static final List<String> FILE_TYPES = List.of("binary", "rpm", "tar", "zip", "config", "other");

    private final ResourceFileMapper resourceFileMapper;
    private final ScriptResourceMapper scriptResourceMapper;

    @Value("${autotest.storage.resources-path}")
    private String resourcesPath;

    @McpTool(name = "list_resources", description = "列出平台资源库里的文件。脚本包 autotest.yaml 的 "
            + "resources[].resourceMd5 取自这里的 checksum，resources[].resourceId 取自这里的 id——"
            + "要绑哪个二进制先用它查，不要凭记忆或猜 MD5。每条返回 id、名称、checksum、fileSize、fileType、"
            + "category、description、入库时间，外加两个有用的字段：existsOnDisk（平台上那份文件还在不在）、"
            + "usedByScripts（被几个脚本包关联着）。",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true,
                    title = "列出资源文件"))
    public Map<String, Object> listResources(
            @McpToolParam(required = false, description = "按名称或描述模糊过滤，不区分大小写") String keyword,
            @McpToolParam(required = false, description = "按文件类型过滤：binary/rpm/tar/zip/config/other") String fileType,
            @McpToolParam(required = false, description = "按分类过滤，与脚本包分类一致") String category,
            @McpToolParam(required = false, description = "按 MD5 精确查（用来确认某个 MD5 是否已在库里）") String md5,
            @McpToolParam(required = false, description = "最多返回多少条，默认 50") Integer limit) {

        String kw = lower(keyword);
        String wantMd5 = md5 == null ? null : md5.trim().toLowerCase(Locale.ROOT);
        int max = limit == null || limit <= 0 ? 50 : Math.min(limit, 500);

        Map<Long, Integer> usage = countUsageByResource();
        List<Map<String, Object>> items = new ArrayList<>();
        for (ResourceFile rf : resourceFileMapper.selectList(new LambdaQueryWrapper<ResourceFile>()
                .orderByDesc(ResourceFile::getId))) {
            if (fileType != null && !fileType.isBlank()
                    && !fileType.trim().equalsIgnoreCase(rf.getFileType())) {
                continue;
            }
            if (category != null && !category.isBlank() && !category.trim().equals(rf.getCategory())) {
                continue;
            }
            if (wantMd5 != null && !wantMd5.equals(lower(rf.getChecksum()))) {
                continue;
            }
            if (kw != null && !contains(rf.getName(), kw) && !contains(rf.getDescription(), kw)) {
                continue;
            }
            items.add(toView(rf, usage.getOrDefault(rf.getId(), 0)));
            if (items.size() >= max) {
                break;
            }
        }
        Map<String, Object> out = result("resources", items, "count", items.size(),
                // 每条资源自己的 storagePath 是相对这个目录的路径，别和它混为一谈
                "resourcesDir", Paths.get(resourcesPath).toString().replace("\\", "/"));
        if (items.isEmpty()) {
            out.put("hint", "库里没有匹配的资源；本地已有文件时用 upload_resource 把它登记进来拿 checksum");
        }
        return out;
    }

    @McpTool(name = "upload_resource", description = "把平台主机上的一个文件登记进资源库，等价 UI 的资源上传，"
            + "返回 resourceId 与 checksum（checksum 就是 autotest.yaml 里 resources[].resourceMd5 要填的值）。"
            + "按 MD5 去重：库里已有同 MD5 的记录时直接返回既有记录并置 reused=true，不会存第二份。"
            + "文件本身已经在资源存储目录里时只登记引用、不再拷贝；否则拷进存储目录（引用模式下原文件被删掉平台就取不到了）。"
            + "GB 级文件算 MD5 加拷贝要几十秒，属正常，别在中途重试。",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false,
                    idempotentHint = true,
                    title = "登记资源文件"))
    public Map<String, Object> uploadResource(
            @McpToolParam(description = "平台主机上的文件绝对路径，如 D:/downloads/cpu2017-1.1.8.iso") String localPath,
            @McpToolParam(required = false, description = "文件类型，缺省按扩展名推断：binary/rpm/tar/zip/config/other") String fileType,
            @McpToolParam(required = false, description = "分类，与脚本包分类一致，如 cpu/database") String category,
            @McpToolParam(required = false, description = "描述，建议写清用途与版本（后续 list_resources 靠它认）") String description) {

        Path src;
        try {
            src = Paths.get(localPath);
        } catch (InvalidPathException e) {
            return error("PARAM_INVALID", "路径不合法: " + localPath);
        }
        if (!Files.isRegularFile(src)) {
            return error("FILE_NOT_FOUND", "不是平台主机上的普通文件（或不存在）: " + src);
        }

        long size;
        String checksum;
        try {
            size = Files.size(src);
            try (InputStream in = Files.newInputStream(src)) {
                checksum = DigestUtils.md5DigestAsHex(in);
            }
        } catch (Exception e) {
            return error("READ_FAILED", "读取或算 MD5 失败: " + e.getMessage());
        }
        if (size <= 0) {
            return error("EMPTY_FILE", "文件大小为 0，登记进去任务上传时只会拿到空包: " + src);
        }

        ResourceFile existing = resourceFileMapper.findByChecksum(checksum);
        if (existing != null) {
            Map<String, Object> out = toView(existing, countUsageByResource()
                    .getOrDefault(existing.getId(), 0));
            out.put("ok", true);
            out.put("reused", true);
            out.put("message", "库里已有同 MD5 的资源，未重复入库；resourceMd5 直接用这个 checksum");
            out.put("next", "get_script_contract 复核绑定，或 import_package 重新导入脚本包");
            return out;
        }

        String name = src.getFileName().toString();
        String type = normalizeType(fileType, name);
        Path base = Paths.get(resourcesPath);
        boolean referenced;
        String storagePath;
        try {
            Path normalizedSrc = src.toAbsolutePath().normalize();
            Path normalizedBase = base.toAbsolutePath().normalize();
            if (normalizedSrc.startsWith(normalizedBase)) {
                // 已经在资源目录里，再拷一份只是白占一倍磁盘
                referenced = true;
                storagePath = normalizedBase.relativize(normalizedSrc).toString().replace("\\", "/");
            } else {
                referenced = false;
                String datePath = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
                storagePath = datePath + "/" + UUID.randomUUID() + "_" + name;
                Path target = base.resolve(storagePath);
                Files.createDirectories(target.getParent());
                Files.copy(normalizedSrc, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            return error("STORE_FAILED", "登记进资源目录失败: " + e.getMessage());
        }

        ResourceFile rf = new ResourceFile();
        rf.setName(name);
        rf.setStoragePath(storagePath);
        rf.setFileSize(size);
        rf.setFileType(type);
        rf.setCategory(category);
        rf.setChecksum(checksum);
        rf.setDescription(description);
        rf.setCreatedBy("agent");
        rf.setCreatedAt(LocalDateTime.now());
        rf.setUpdatedAt(LocalDateTime.now());
        resourceFileMapper.insert(rf);
        log.info("MCP upload_resource: id={}, name={}, size={}, checksum={}, referenced={}",
                rf.getId(), name, size, checksum, referenced);

        Map<String, Object> out = toView(rf, 0);
        out.put("ok", true);
        out.put("reused", false);
        out.put("referenced", referenced);
        out.put("next", "把 resourceMd5=" + checksum + " 写进 autotest.yaml 的 resources 再 import_package；"
                + "targetPath 填绝对路径可以按大小跳过重传，填相对路径每个任务都会重传一次");
        return out;
    }

    // ==================== 内部方法 ====================

    /** 一次查出各资源被多少脚本包关联，避免逐行 count */
    private Map<Long, Integer> countUsageByResource() {
        Map<Long, Integer> usage = new LinkedHashMap<>();
        for (ScriptResource sr : scriptResourceMapper.selectList(null)) {
            if (sr.getResourceId() != null) {
                usage.merge(sr.getResourceId(), 1, Integer::sum);
            }
        }
        return usage;
    }

    private Map<String, Object> toView(ResourceFile rf, int usedByScripts) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", rf.getId());
        view.put("name", rf.getName());
        view.put("checksum", rf.getChecksum());
        view.put("fileSize", rf.getFileSize());
        view.put("fileSizeMb", rf.getFileSize() == null ? null
                : Math.round(rf.getFileSize() / 1024.0 / 1024.0 * 10) / 10.0);
        view.put("fileType", rf.getFileType());
        view.put("category", rf.getCategory());
        view.put("description", rf.getDescription());
        view.put("storagePath", rf.getStoragePath());
        view.put("existsOnDisk", existsOnDisk(resourcesPath, rf));
        view.put("usedByScripts", usedByScripts);
        view.put("createdAt", rf.getCreatedAt());
        return view;
    }

    /** 资源记录在库里、文件却不在磁盘上是会发生的（手工清过存储目录），任务执行时才炸 */
    static boolean existsOnDisk(String resourcesPath, ResourceFile rf) {
        if (rf == null || rf.getStoragePath() == null) {
            return false;
        }
        try {
            return Files.isRegularFile(Paths.get(resourcesPath, rf.getStoragePath()));
        } catch (InvalidPathException e) {
            return false;
        }
    }

    private static String normalizeType(String fileType, String name) {
        if (fileType != null && !fileType.isBlank()) {
            String v = fileType.trim().toLowerCase(Locale.ROOT);
            return FILE_TYPES.contains(v) ? v : "other";
        }
        String n = name.toLowerCase(Locale.ROOT);
        if (n.endsWith(".rpm")) {
            return "rpm";
        }
        if (n.endsWith(".tar") || n.endsWith(".tar.gz") || n.endsWith(".tgz")
                || n.endsWith(".tar.xz") || n.endsWith(".txz") || n.endsWith(".tar.bz2")) {
            return "tar";
        }
        if (n.endsWith(".zip")) {
            return "zip";
        }
        if (n.endsWith(".conf") || n.endsWith(".cfg") || n.endsWith(".ini") || n.endsWith(".properties")) {
            return "config";
        }
        return "other";
    }

    private static String lower(String s) {
        return s == null || s.isBlank() ? null : s.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean contains(String value, String kw) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(kw);
    }
}
