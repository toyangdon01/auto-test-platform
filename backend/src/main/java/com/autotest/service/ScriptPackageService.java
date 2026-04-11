package com.autotest.service;

import com.autotest.dto.ExportOptions;
import com.autotest.dto.ImportResult;
import com.autotest.dto.PackageManifest;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 脚本包服务
 *
 * @author auto-test-platform
 */
public interface ScriptPackageService {
    
    /**
     * 批量导出脚本
     *
     * @param options 导出选项
     * @return ZIP 文件路径
     */
    String exportScripts(ExportOptions options) throws IOException;
    
    /**
     * 导入脚本包
     *
     * @param file ZIP 文件
     * @param strategy 冲突处理策略
     * @return 导入结果
     */
    ImportResult importPackage(MultipartFile file, ConflictStrategy strategy) throws IOException;
    
    /**
     * 预览脚本包内容
     *
     * @param file ZIP 文件
     * @return 包预览信息
     */
    PackageManifest previewPackage(MultipartFile file) throws IOException;
    
    /**
     * 冲突处理策略
     */
    enum ConflictStrategy {
        SKIP,       // 跳过已存在的
        OVERWRITE,  // 覆盖已存在的
        RENAME      // 重命名新脚本
    }
}
