package com.autotest.controller;

import com.autotest.common.ApiResponse;
import com.autotest.dto.OnlineImportRequest;
import com.autotest.dto.OnlinePreviewResponse;
import com.autotest.dto.ImportResult;
import com.autotest.service.OnlineImportService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 在线导入控制器
 */
@RestController
@RequestMapping("/api/v1/scripts/import/online")
@Slf4j
public class OnlineImportController {

    @Autowired
    private OnlineImportService onlineImportService;

    /**
     * 预览在线仓库脚本
     */
    @PostMapping("/preview")
    public ApiResponse<OnlinePreviewResponse> preview(@RequestBody OnlineImportRequest request) {
        log.info("预览请求：url={}, branch={}", request.getUrl(), request.getBranch());
        OnlinePreviewResponse response = onlineImportService.previewFromOnline(request);
        return ApiResponse.success(response);
    }

    /**
     * 导入在线仓库脚本
     */
    @PostMapping("/import")
    public ApiResponse<ImportResult> importScripts(@RequestBody OnlineImportRequest request) {
        log.info("导入请求：tempPath={}, selectedScripts={}", 
            request.getTempPath(), request.getSelectedScripts());
        ImportResult result = onlineImportService.importFromOnline(request);
        return ApiResponse.success(result);
    }
}