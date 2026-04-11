package com.autotest.service;

import com.autotest.dto.OnlineImportRequest;
import com.autotest.dto.OnlinePreviewResponse;
import com.autotest.dto.ImportResult;

/**
 * 在线导入服务接口
 */
public interface OnlineImportService {
    
    /**
     * 从在线仓库预览脚本包
     * @param request 导入请求（url, branch, subDir, accessToken）
     * @return 预览结果（脚本列表 + 临时路径）
     */
    OnlinePreviewResponse previewFromOnline(OnlineImportRequest request);
    
    /**
     * 从在线仓库导入脚本
     * @param request 导入请求（tempPath + selectedScripts + conflictStrategy）
     * @return 导入结果
     */
    ImportResult importFromOnline(OnlineImportRequest request);
}