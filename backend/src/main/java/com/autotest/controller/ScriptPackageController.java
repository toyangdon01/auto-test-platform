package com.autotest.controller;

import com.autotest.common.ApiResponse;
import com.autotest.dto.ExportOptions;
import com.autotest.dto.ImportResult;
import com.autotest.dto.OnlineImportRequest;
import com.autotest.dto.OnlinePreviewResponse;
import com.autotest.dto.PackageManifest;
import com.autotest.service.OnlineImportService;
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
import java.time.LocalDate;

/**
 * 脚本导入导出控制器
 *
 * @author auto-test-platform
 */
@Slf4j
@Tag(name = "scripts/import", description = "脚本导入导出")
@RestController
@RequestMapping("/api/v1/scripts/import")
@RequiredArgsConstructor
public class ScriptPackageController {
    
    private final ScriptPackageService packageService;
    private final OnlineImportService onlineImportService;
    
    // ==================== 离线导入（上传 ZIP） ====================
    
    /**
     * 预览脚本包内容
     */
    @Operation(
        summary = "预览脚本包",
        description = "预览上传的 ZIP 脚本包内容，显示包含的脚本列表和文件信息"
    )
    @PostMapping("/preview")
    public ApiResponse<PackageManifest> previewPackage(
            @RequestParam("file") MultipartFile file) throws IOException {
        
        PackageManifest preview = packageService.previewPackage(file);
        return ApiResponse.success(preview);
    }
    
    /**
     * 导入脚本包
     */
    @Operation(
        summary = "导入脚本包",
        description = "导入上传的 ZIP 脚本包。\n" +
                     "\n**冲突处理策略：**\n" +
                     "- skip: 跳过已存在的脚本\n" +
                     "- overwrite: 覆盖已存在的脚本\n" +
                     "- rename: 重命名新脚本"
    )
    @PostMapping("/execute")
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
    
    // ==================== 在线导入（从 Git 仓库） ====================
    
    /**
     * 预览在线仓库脚本
     */
    @Operation(
        summary = "预览在线仓库",
        description = "预览在线 Git 仓库中的脚本。\n" +
                     "\n**支持的仓库：**\n" +
                     "- Gitee\n" +
                     "- GitHub\n" +
                     "- GitLab\n" +
                     "\n**请求参数：**\n" +
                     "- url: 仓库地址\n" +
                     "- branch: 分支名称（可选）\n" +
                     "- subDir: 子目录路径（可选）\n" +
                     "- accessToken: 访问令牌（私有仓库）"
    )
    @PostMapping("/online/preview")
    public ApiResponse<OnlinePreviewResponse> previewOnline(@RequestBody OnlineImportRequest request) {
        log.info("预览在线仓库：url={}, branch={}", request.getUrl(), request.getBranch());
        OnlinePreviewResponse response = onlineImportService.previewFromOnline(request);
        return ApiResponse.success(response);
    }

    /**
     * 导入在线仓库脚本
     */
    @Operation(
        summary = "导入在线仓库脚本",
        description = "导入在线 Git 仓库中的脚本。\n" +
                     "\n**使用流程：**\n" +
                     "1. 先调用 /online/preview 获取脚本列表和 tempPath\n" +
                     "2. 用户选择要导入的脚本\n" +
                     "3. 调用此接口传入 tempPath 和 selectedScripts"
    )
    @PostMapping("/online/execute")
    public ApiResponse<ImportResult> importOnline(@RequestBody OnlineImportRequest request) {
        log.info("导入在线仓库：tempPath={}, selectedScripts={}", 
            request.getTempPath(), request.getSelectedScripts());
        ImportResult result = onlineImportService.importFromOnline(request);
        return ApiResponse.success(result);
    }
    
    // ==================== 导出 ====================
    
    /**
     * 批量导出脚本
     */
    @Operation(
        summary = "批量导出脚本",
        description = "将选中的脚本导出为 ZIP 包。\n" +
                     "\n**请求体：**\n" +
                     "- scriptIds: 要导出的脚本ID列表"
    )
    @PostMapping("/export")
    public ResponseEntity<Resource> exportScripts(@RequestBody ExportOptions options) throws IOException {
        String zipPath = packageService.exportScripts(options);
        
        Resource resource = new FileSystemResource(zipPath);
        String filename = "scripts-package-" + LocalDate.now() + ".zip";
        
        // 支持 UTF-8 编码的文件名（中文文件名）
        String encodedFilename = java.net.URLEncoder.encode(filename, "UTF-8").replace("+", "%20");
        
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFilename)
                .body(resource);
    }
}
