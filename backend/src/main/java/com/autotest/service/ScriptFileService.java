package com.autotest.service;

import com.autotest.entity.Script;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 脚本文件服务
 *
 * @author auto-test-platform
 */
public interface ScriptFileService {

    /**
     * 上传脚本文件
     *
     * @param file 上传的文件（.sh/.py/.zip/.tar.gz）
     * @return 文件信息
     */
    Map<String, Object> uploadScriptFile(MultipartFile file) throws IOException;

    /**
     * 解压压缩包并获取文件列表
     *
     * @param tempFilePath 临时文件路径
     * @param scriptName   脚本名称
     * @return 文件列表
     */
    List<Map<String, Object>> extractArchive(String tempFilePath, String scriptName) throws IOException;

    /**
     * 保存脚本文件
     *
     * @param scriptId   脚本ID
     * @param files      文件内容列表
     * @param entryFile  入口文件
     */
    void saveScriptFiles(Long scriptId, List<Map<String, Object>> files, String entryFile) throws IOException;

    /**
     * 读取脚本文件内容
     *
     * @param scriptId 脚本ID
     * @param filePath 文件路径（相对于脚本目录）
     * @return 文件内容
     */
    String readScriptFile(Long scriptId, String filePath) throws IOException;

    /**
     * 更新脚本文件内容
     *
     * @param scriptId  脚本ID
     * @param filePath  文件路径
     * @param content   文件内容
     */
    void updateScriptFile(Long scriptId, String filePath, String content) throws IOException;

    /**
     * 删除脚本目录
     *
     * @param scriptId 脚本ID
     */
    void deleteScriptFiles(Long scriptId) throws IOException;

    /**
     * 导出脚本为压缩包
     *
     * @param script 脚本信息
     * @param format 格式（zip/tar.gz）
     * @return 压缩包文件路径
     */
    String exportScript(Script script, String format) throws IOException;

    /**
     * 获取脚本目录路径
     *
     * @param scriptId 脚本ID
     * @return 目录路径
     */
    String getScriptPath(Long scriptId);

    /**
     * 列出脚本目录下的所有文件
     *
     * @param scriptId 脚本ID
     * @return 文件路径列表
     */
    List<String> listScriptFiles(Long scriptId) throws IOException;
}
