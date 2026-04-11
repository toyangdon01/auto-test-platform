package com.autotest.controller;

import com.autotest.common.ApiResponse;
import com.autotest.entity.ResourceFile;
import com.autotest.service.ResourceFileService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * 资源文件控制器
 */
@Slf4j
@RestController
@RequestMapping("/resources")
@RequiredArgsConstructor
@Tag(name = "资源文件管理")
public class ResourceFileController {

    private final ResourceFileService resourceFileService;

    @Operation(summary = "上传资源文件")
    @PostMapping("/upload")
    public ApiResponse<ResourceFile> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "fileType", required = false) String fileType,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "description", required = false) String description) throws IOException {

        // 文件大小检查（20GB）
        long maxSize = 20L * 1024 * 1024 * 1024;
        if (file.getSize() > maxSize) {
            return ApiResponse.error("文件大小超过限制（最大20GB）");
        }

        ResourceFile resourceFile = resourceFileService.upload(file, fileType, category, description);
        return ApiResponse.success(resourceFile);
    }

    @Operation(summary = "分页查询资源文件")
    @GetMapping
    public ApiResponse<Page<ResourceFile>> getPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String fileType,
            @RequestParam(required = false) String category) {

        Page<ResourceFile> page = resourceFileService.getPage(pageNum, pageSize, name, fileType, category);
        return ApiResponse.success(page);
    }

    @Operation(summary = "获取资源文件详情")
    @GetMapping("/{id}")
    public ApiResponse<ResourceFile> getById(@PathVariable Long id) {
        ResourceFile resourceFile = resourceFileService.getById(id);
        if (resourceFile == null) {
            return ApiResponse.error("资源文件不存在");
        }
        return ApiResponse.success(resourceFile);
    }

    @Operation(summary = "删除资源文件")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        try {
            resourceFileService.delete(id);
            return ApiResponse.success();
        } catch (Exception e) {
            return ApiResponse.error("删除失败：" + e.getMessage());
        }
    }

    @Operation(summary = "下载资源文件")
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        try {
            ResourceFile resourceFile = resourceFileService.getById(id);
            if (resourceFile == null) {
                return ResponseEntity.notFound().build();
            }

            Path filePath = Paths.get(resourceFileService.getStoragePath(id));
            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists()) {
                return ResponseEntity.notFound().build();
            }

            String contentType = "application/octet-stream";
            String headerValue = "attachment; filename=\"" + resourceFile.getName() + "\"";

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, headerValue)
                    .body(resource);

        } catch (MalformedURLException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(summary = "检查文件是否已存在（通过MD5）")
    @GetMapping("/check")
    public ApiResponse<Map<String, Object>> checkExists(@RequestParam String checksum) {
        ResourceFile existing = resourceFileService.getByChecksum(checksum);
        Map<String, Object> result = new HashMap<>();
        result.put("exists", existing != null);
        if (existing != null) {
            result.put("file", existing);
        }
        return ApiResponse.success(result);
    }
}
