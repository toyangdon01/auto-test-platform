package com.autotest.controller;

import com.autotest.common.ApiResponse;
import com.autotest.dto.ExportOptions;
import com.autotest.dto.ImportResult;
import com.autotest.dto.PackageManifest;
import com.autotest.service.ScriptPackageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;

/**
 * 脚本包管理控制器
 *
 * @author auto-test-platform
 */
@Slf4j
@Tag(name = "scripts/package", description = "脚本包管理")
@RestController
@RequestMapping("/scripts/package")
@RequiredArgsConstructor
public class ScriptPackageController {
    
    private final ScriptPackageService packageService;
    
    /**
     * 批量导出脚本
     */
    @Operation(summary = "批量导出脚本")
    @PostMapping("/export")
    public ResponseEntity<Resource> exportScripts(@RequestBody ExportOptions options) throws IOException {
        String zipPath = packageService.exportScripts(options);
        
        Resource resource = new FileSystemResource(zipPath);
        String filename = "scripts-package-" + LocalDate.now() + ".zip";
        
        // 支持 UTF-8 编码的文件名（中文文件名）
        String encodedFilename = java.net.URLEncoder.encode(filename, "UTF-8").replace("+", "%20");
        
        // 设置文件在传输完成后删除
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFilename)
                .body(resource);
    }
    
    /**
     * 导入脚本包
     */
    @Operation(summary = "导入脚本包")
    @PostMapping("/import")
    public ApiResponse<ImportResult> importPackage(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "conflictStrategy", defaultValue = "skip") 
            ScriptPackageService.ConflictStrategy strategy) {
        
        try {
            log.info("开始导入脚本包: fileName={}, strategy={}", file.getOriginalFilename(), strategy);
            ImportResult result = packageService.importPackage(file, strategy);
            log.info("导入完成: total={}, imported={}, skipped={}, failed={}", 
                     result.getTotal(), result.getImported(), result.getSkipped(), result.getFailed());
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("导入脚本包失败", e);
            return ApiResponse.error(500, "导入失败: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }
    
    /**
     * 预览脚本包内容
     */
    @Operation(summary = "预览脚本包")
    @PostMapping("/import/preview")
    public ApiResponse<PackageManifest> previewPackage(
            @RequestParam("file") MultipartFile file) throws IOException {
        
        PackageManifest preview = packageService.previewPackage(file);
        return ApiResponse.success(preview);
    }
}
