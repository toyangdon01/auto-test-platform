package com.autotest.service.impl;

import com.autotest.entity.ResourceFile;
import com.autotest.mapper.ResourceFileMapper;
import com.autotest.service.ResourceFileService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 资源文件服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResourceFileServiceImpl implements ResourceFileService {

    private final ResourceFileMapper resourceFileMapper;

    @Value("${app.storage.path:C:/data/auto-test/resources}")
    private String storageBasePath;

    @Override
    public ResourceFile upload(MultipartFile file, String fileType, String category, String description) throws IOException {
        // 计算 MD5
        String checksum = DigestUtils.md5DigestAsHex(file.getInputStream());

        // 检查是否已存在
        ResourceFile existing = resourceFileMapper.findByChecksum(checksum);
        if (existing != null) {
            throw new RuntimeException("文件已存在：" + existing.getName() + "（MD5: " + checksum + "）");
        }

        // 生成存储路径
        String originalFilename = file.getOriginalFilename();
        String datePath = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String storageName = UUID.randomUUID().toString() + "_" + originalFilename;
        String storagePath = datePath + "/" + storageName;

        // 创建目录
        Path fullPath = Paths.get(storageBasePath, datePath);
        Files.createDirectories(fullPath);

        // 保存文件
        Path filePath = fullPath.resolve(storageName);
        file.transferTo(filePath.toFile());

        // 创建实体
        ResourceFile resourceFile = new ResourceFile();
        resourceFile.setName(originalFilename);
        resourceFile.setStoragePath(storagePath);
        resourceFile.setFileSize(file.getSize());
        resourceFile.setFileType(fileType);
        resourceFile.setCategory(category);
        resourceFile.setChecksum(checksum);
        resourceFile.setDescription(description);
        resourceFile.setCreatedBy("system"); // TODO: 从上下文获取当前用户
        resourceFile.setCreatedAt(LocalDateTime.now());
        resourceFile.setUpdatedAt(LocalDateTime.now());

        resourceFileMapper.insert(resourceFile);

        log.info("上传资源文件成功: id={}, name={}, size={}, checksum={}",
                resourceFile.getId(), resourceFile.getName(), resourceFile.getFileSize(), checksum);

        return resourceFile;
    }

    @Override
    public Page<ResourceFile> getPage(int pageNum, int pageSize, String name, String fileType, String category) {
        Page<ResourceFile> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<ResourceFile> wrapper = new LambdaQueryWrapper<>();

        if (name != null && !name.isEmpty()) {
            wrapper.like(ResourceFile::getName, name);
        }
        if (fileType != null && !fileType.isEmpty()) {
            wrapper.eq(ResourceFile::getFileType, fileType);
        }
        if (category != null && !category.isEmpty()) {
            wrapper.eq(ResourceFile::getCategory, category);
        }

        wrapper.orderByDesc(ResourceFile::getCreatedAt);

        return resourceFileMapper.selectPage(page, wrapper);
    }

    @Override
    public ResourceFile getById(Long id) {
        return resourceFileMapper.selectById(id);
    }

    @Override
    public ResourceFile getByChecksum(String checksum) {
        return resourceFileMapper.findByChecksum(checksum);
    }

    @Override
    public void delete(Long id) {
        ResourceFile resourceFile = resourceFileMapper.selectById(id);
        if (resourceFile == null) {
            throw new RuntimeException("资源文件不存在: " + id);
        }

        // 删除物理文件
        try {
            Path filePath = Paths.get(storageBasePath, resourceFile.getStoragePath());
            Files.deleteIfExists(filePath);
            log.info("删除资源文件: {}", filePath);
        } catch (IOException e) {
            log.warn("删除资源文件失败: {}", e.getMessage());
        }

        // 删除数据库记录
        resourceFileMapper.deleteById(id);
    }

    @Override
    public byte[] getFileContent(Long id) throws IOException {
        ResourceFile resourceFile = resourceFileMapper.selectById(id);
        if (resourceFile == null) {
            throw new RuntimeException("资源文件不存在: " + id);
        }

        Path filePath = Paths.get(storageBasePath, resourceFile.getStoragePath());
        return Files.readAllBytes(filePath);
    }

    @Override
    public String getStoragePath(Long id) {
        ResourceFile resourceFile = resourceFileMapper.selectById(id);
        if (resourceFile == null) {
            throw new RuntimeException("资源文件不存在: " + id);
        }
        return Paths.get(storageBasePath, resourceFile.getStoragePath()).toString();
    }
}
