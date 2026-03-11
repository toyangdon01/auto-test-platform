package com.autotest.service;

import com.autotest.entity.ResourceFile;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 资源文件服务接口
 */
public interface ResourceFileService {

    /**
     * 上传资源文件
     *
     * @param file 文件
     * @param fileType 文件类型
     * @param category 分类
     * @param description 描述
     * @return 资源文件实体
     */
    ResourceFile upload(MultipartFile file, String fileType, String category, String description) throws IOException;

    /**
     * 分页查询
     */
    Page<ResourceFile> getPage(int pageNum, int pageSize, String name, String fileType, String category);

    /**
     * 根据ID获取
     */
    ResourceFile getById(Long id);

    /**
     * 根据MD5获取
     */
    ResourceFile getByChecksum(String checksum);

    /**
     * 删除资源文件
     */
    void delete(Long id);

    /**
     * 获取文件内容
     */
    byte[] getFileContent(Long id) throws IOException;

    /**
     * 获取存储路径
     */
    String getStoragePath(Long id);
}
